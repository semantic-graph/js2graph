package edu.washington.cs.js2graph

import java.util

import com.ibm.wala.cast.ir.ssa.{AstGlobalRead, AstGlobalWrite, AstLexicalRead, AstLexicalWrite, EachElementGetInstruction}
import com.ibm.wala.cast.js.ssa._
import com.ibm.wala.cast.js.types.JavaScriptMethods
import com.ibm.wala.dataflow.IFDS._
import com.ibm.wala.ipa.callgraph.CGNode
import com.ibm.wala.ipa.cfg.{BasicBlockInContext, ExplodedInterproceduralCFG}
import com.ibm.wala.ssa._
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock
import com.ibm.wala.util.collections.Pair
import com.ibm.wala.util.intset.{EmptyIntSet, IntSet, MutableMapping, MutableSparseIntSet}

import scala.collection.mutable

sealed abstract class AbsVal extends Product with Serializable

object AbsVal {

  /**
   * FIXME: maybe we want to use NodeId for this case as well?
   * @param name Name of the global object or its (potentially nested) sub-field, e.g. module.constructor
   */
  final case class Global(name: String) extends AbsVal

  final case class Constant(v: String) extends AbsVal

  /**
   * Use instruction to track API instance constructed by the instruction
   */
  final case class Instance(apiName: String, instruction: JavaScriptInvoke) extends AbsVal
}

sealed abstract class AbsPath extends Product with Serializable

object AbsPath {
  final case class Global(name: String) extends AbsPath

  /**
   * @param idx SSA index for any intermediate variable
   */
  final case class Local(idx: Int) extends AbsPath
}

object InvokeType extends Enumeration {
  type InvokeType = Value
  val CONSTRUCT, DISPATCH, INVOKE = Value
}

class IFDSDataFlow(val icfg: ExplodedInterproceduralCFG) {
  final private val supergraph = ICFGSupergraph.make(icfg.getCallGraph)
  final private val domain = new DataFlowDomain
  type Block = BasicBlockInContext[IExplodedBasicBlock]
  type AbsDomain = Pair[AbsPath, AbsVal]

  /**
   * Map from invocation to the constructor API name
   *
   * FIXME: maybe multiple values?
   */
  private val postApiInvocation = mutable.Map[JavaScriptInvoke, (String, Tag.Value)]()
  def getApiNameAndTag(javaScriptInvoke: JavaScriptInvoke): Option[(String, Tag.Value)] =
    postApiInvocation.get(javaScriptInvoke)

  /**
   * controls numbering of putstatic instructions for use in tabulation
   */
  @SuppressWarnings(Array("serial"))
  class DataFlowDomain extends MutableMapping[AbsDomain] with TabulationDomain[AbsDomain, Block] {
    override def hasPriorityOver(p1: PathEdge[Block], p2: PathEdge[Block]): Boolean = {
      // Don't worry about worklist priorities
      false
    }
  }

  class DataFlowFunctions (val domain: DataFlowDomain) extends IPartiallyBalancedFlowFunctions[Block] {
    /**
     * the flow function for flow from a callee to caller where there was no flow from caller to callee; just the identity function
     *
     * @see ReachingDefsProblem
     */
    override def getUnbalancedReturnFlowFunction(src: Block, dest: Block): IFlowFunction = IdentityFlowFunction.identity

    /**
     * flow function from caller to callee; just the identity function
     */
    override def getCallFlowFunction(src: Block, dest: Block, ret: Block): IUnaryFlowFunction = IdentityFlowFunction.identity

    private def getInvokeInstructionType(javaScriptInvoke: JavaScriptInvoke): InvokeType.Value = {
      javaScriptInvoke.getCallSite.getDeclaredTarget match {
        case JavaScriptMethods.ctorReference => InvokeType.CONSTRUCT
        case JavaScriptMethods.dispatchReference => InvokeType.DISPATCH
        case _ => InvokeType.INVOKE
      }
    }

    /**
     * Flow function from call node to return node when there are no targets for the call site
     * FIXME: not a case we are expecting
     * if we're missing callees, just keep what information we have
     */
    override def getCallNoneToReturnFlowFunction(src: Block, dest: Block): IUnaryFlowFunction = {
      val instr = src.getDelegate.getInstruction
      val symTable = src.getNode.getIR.getSymbolTable
      instr match {
        case invokeInstruction: JavaScriptInvoke =>
          getInvokeInstructionType(invokeInstruction) match {
            case InvokeType.CONSTRUCT =>
              inputDomain: Int => {
                val result = MutableSparseIntSet.makeEmpty
                result.add(inputDomain)
                val fact = domain.getMappedObject(inputDomain)
                if (fact.fst == AbsPath.Local(invokeInstruction.getReceiver)) {
                  // Create a new instance type node here with type information
                  fact.snd match {
                    case AbsVal.Global(globalName) =>
                      postApiInvocation.addOne(invokeInstruction, (globalName, Tag.Construct))
                      result.add(domain.add(Pair.make(AbsPath.Local(invokeInstruction.getDef(0)),
                                                      AbsVal.Instance(globalName, invokeInstruction))))
                    case _ =>
                  }
                  result
                } else {
                  result
                }
              }
            case InvokeType.DISPATCH | InvokeType.INVOKE =>
              inputDomain: Int => {
                val result = MutableSparseIntSet.makeEmpty
                result.add(inputDomain)
                val fact = domain.getMappedObject(inputDomain)

                // Case 1: API invocation on global object
                // If it is and contains "global", we will check if the argument is a constant representing the name of
                // required package.
                // Case 1.1: var x = require(pkg_name);
                if (fact.fst == AbsPath.Local(invokeInstruction.getReceiver) && fact.snd == AbsVal.Global("global require")) {
                  if (symTable.isConstant(invokeInstruction.getUse(2))) {
                    val requiredPkgName = symTable.getConstantValue(invokeInstruction.getUse(2)).toString
                    val lhs = AbsPath.Local(instr.getDef(0))
                    result.add(domain.add(Pair.make(lhs, AbsVal.Global("require(" + requiredPkgName + ")"))))
                  }
                }
                // FIXME: maybe it can be combined with the conditional above
                // Case 2: API invocation on instance
                else if (invokeInstruction.getNumberOfUses >= 2) {
                  val dispatchFuncIndex = invokeInstruction.getUse(0)
                  val dispatchBaseIndex = invokeInstruction.getUse(1)
                  if (symTable.isConstant(dispatchFuncIndex)) {
                    /*
                      Example: For code

                        var crypto = require("crypto");
                        var cipher = crypto.createDecipher(...);

                      The dispatchFunc will be "createDecipher". However, this is both a sink and an instance node.
                      Here, we are doing to create an instance node and point the base to it.
                     */
                    if (fact.fst == AbsPath.Local(dispatchBaseIndex)) {
                      val dispatchFunc = symTable.getConstantValue(dispatchFuncIndex).toString
                      // TODO: if there is a callback in the API invocation's arguments, add a edge from *after* the
                      //    invocation to the entry of the callback function, and try to connect the "dispatchFunc"
                      //    fact to the AbsPath.Global("__context__")
                      fact.snd match {
                        case AbsVal.Global(globalBaseName) =>
                          val apiName = globalBaseName + "." + dispatchFunc
                          val absVal = if (Constants.isConstructorAPI(dispatchFunc)) {
                            postApiInvocation.addOne(invokeInstruction, (apiName, Tag.Construct))
                            AbsVal.Instance(apiName, invokeInstruction)
                          } else {
                            AbsVal.Global(apiName)
                          }
                          result.add(domain.add(Pair.make(AbsPath.Local(invokeInstruction.getDef(0)), absVal)))
                        case AbsVal.Instance(apiName, _) =>
                          val newApiName = apiName + "." + dispatchFunc
                          postApiInvocation.addOne(invokeInstruction, (newApiName, Tag.Call))
                          result.add(domain.add(Pair.make(AbsPath.Local(invokeInstruction.getDef(0)),
                            AbsVal.Instance(newApiName, invokeInstruction))))
                        case _ =>
                      }
                    }
                  }
                }
                result
              }
          }
        case _ => IdentityFlowFunction.identity
      }
    }

    /**
     * flow function from call node to return node at a call site when callees exist. We kill everything; surviving facts should
     * flow out of the callee
     */
    override def getCallToReturnFlowFunction(src: Block, dest: Block): IUnaryFlowFunction = {
      KillEverything.singleton
    }

    /**
     * @param withRhsTaint update the taint from RHS for new state
     */
    private def flow(inputDomain: Int, lhsVar: AbsPath, rhsVars: Set[AbsPath],
                     newFlows: Set[AbsVal], withRhsTaint: Option[AbsVal => AbsVal] = None): IntSet = {
      flow(inputDomain, Set(lhsVar), rhsVars, newFlows, withRhsTaint)
    }

    private def flow(inputDomain: Int, lhsVars: Set[AbsPath], rhsVars: Set[AbsPath],
                     newFlows: Set[AbsVal], withRhsTaint: Option[AbsVal => AbsVal]): IntSet = {
      val fact = domain.getMappedObject(inputDomain)
      val tainted = fact.fst
      val taint: AbsVal = withRhsTaint match {
        case None => fact.snd
        case Some(mapper) => mapper(fact.snd)
      }
      val result = MutableSparseIntSet.makeEmpty
      // if for propagating existing flow
      if (tainted != zeroTainted) {
        if (rhsVars.contains(tainted)) {
          for (lhsVar <- lhsVars) {
            result.add(domain.add(Pair.make(lhsVar, taint)))
          }
        }
      } else { // if for new flow
        for (flow <- newFlows) {
          for (lhsVar <- lhsVars) {
            result.add(domain.add(Pair.make(lhsVar, flow)))
          }
        }
      }
      // If not flowing to the killed LHS
      if (!lhsVars.contains(tainted)) {
        result.add(inputDomain)
      }
      result
    }

    /**
     * flow function for normal intraprocedural edges
     *
     */
    override def getNormalFlowFunction(src: Block, dest: Block): IUnaryFlowFunction = {
      val symTable = src.getNode.getIR.getSymbolTable
      new IUnaryFlowFunction {
        override def getTargets(inputDomain: Int): IntSet = {
          val instr = src.getDelegate.getInstruction
          val result = MutableSparseIntSet.makeEmpty
          if (instr == null
            || instr.isInstanceOf[JavaScriptCheckReference] || instr.isInstanceOf[SetPrototype]
            || instr.isInstanceOf[SSAConditionalBranchInstruction]
            || instr.isInstanceOf[JavaScriptTypeOfInstruction]
            || instr.isInstanceOf[JavaScriptPropertyWrite] // Use of flow to write the objectRef is wrong..currently we don't handle "access path" like pattern
            || instr.isInstanceOf[EachElementGetInstruction]) {
            // do nothing
          } else instr match {
            case astLexicalWrite: AstLexicalWrite =>
              // e.g.
              //  instruction
              //      lexical:child_process_1@Lexample3.js = 7
              //  from
              //      var child_process_1 = require("child_process");
              var ret: IntSet = new EmptyIntSet()
              for (access <- astLexicalWrite.getAccesses) {
                ret = ret.union(flow(inputDomain, AbsPath.Global(access.getName.fst + "@" + access.getName.snd),
                  Set(AbsPath.Local(astLexicalWrite.getUse(0))), Set()))
              }
              return ret
            case lookup: PrototypeLookup =>
              return flow(inputDomain, AbsPath.Local(lookup.getDef(0)), Set(AbsPath.Local(lookup.getUse(0))), Set())
            case readInstr: AstGlobalRead =>
              return flow(inputDomain, AbsPath.Local(readInstr.getDef(0)), Set(), Set(AbsVal.Global(readInstr.getGlobalName)))
            case astLexicalRead: AstLexicalRead =>
              // e.g.
              //  instruction
              //      33 = lexical:child_process_1@Lexample3.js
              var ret: IntSet = new EmptyIntSet()
              for (access <- astLexicalRead.getAccesses) {
                ret = ret.union(flow(inputDomain, AbsPath.Local(astLexicalRead.getDef(0)),
                                     Set(AbsPath.Global(access.getName.fst + "@" + access.getName.snd)),
                                     Set()))
              }
              return ret
            case writeInstr: AstGlobalWrite =>
              return flow(inputDomain, AbsPath.Global(writeInstr.getGlobalName), Set(AbsPath.Local(writeInstr.getVal)), Set())
            case getInstr: SSAGetInstruction =>
              val getFieldName = getInstr.getDeclaredField.getName.toString
              return flow(inputDomain, AbsPath.Local(getInstr.getDef(0)), Set(AbsPath.Local(instr.getUse(0))), Set(),
                          Some({
                            case AbsVal.Global(name) =>
                              AbsVal.Global(name + "." + getFieldName)
                            case x => x
                          }))
            case putInstr: SSAPutInstruction =>
              if (putInstr.getNumberOfUses > 1) {
                val from = putInstr.getUse(1)
                val to = putInstr.getUse(0)
                val newConstants: Set[AbsVal] = if (!putInstr.isStatic) {
                  Set(AbsVal.Global("\"" + putInstr.getDeclaredField.getName + "\""))
                } else {
                  Set()
                }
                return flow(inputDomain, AbsPath.Local(to), Set(AbsPath.Local(from)), newConstants)
              }
            case javaScriptPropertyRead: JavaScriptPropertyRead =>
              val from = javaScriptPropertyRead.getObjectRef
              val to = javaScriptPropertyRead.getDef(0)
              val newConstants = mutable.Set[AbsVal]()
              val from1 = javaScriptPropertyRead.getMemberRef
              if (symTable.isConstant(from1) && symTable.getConstantValue(from1) != null) {
                newConstants.add(AbsVal.Constant(symTable.getConstantValue(from1).toString))
              }
              return flow(inputDomain, AbsPath.Local(to), Set(AbsPath.Local(from)), newConstants.toSet)
            case _: SSABinaryOpInstruction =>
              val from1 = instr.getUse(0)
              val from2 = instr.getUse(1)
              val lhs = instr.getDef(0)
              val rhs = mutable.Set[AbsPath]()
              val newConstants = mutable.Set[AbsVal]()
              if (symTable.isConstant(from1) && symTable.getConstantValue(from1) != null) {
                newConstants.add(AbsVal.Constant(symTable.getConstantValue(from1).toString))
              } else {
                rhs.add(AbsPath.Local(from1))
              }
              if (symTable.isConstant(from2) && symTable.getConstantValue(from2) != null) {
                newConstants.add(AbsVal.Constant(symTable.getConstantValue(from2).toString))
              } else {
                rhs.add(AbsPath.Local(from2))
              }
              return flow(inputDomain, AbsPath.Local(lhs), rhs.toSet, newConstants.toSet)
            case _: SSAUnaryOpInstruction =>
              val from1 = instr.getUse(0)
              val lhs = instr.getDef(0)
              val newConstants = mutable.Set[AbsVal]()
              val rhs = mutable.Set[AbsPath]()
              if (symTable.isConstant(from1) && symTable.getConstantValue(from1) != null) {
                newConstants.add(AbsVal.Constant(symTable.getConstantValue(from1).toString))
              } else {
                rhs.add(AbsPath.Local(lhs))
              }
              return flow(inputDomain, AbsPath.Local(lhs), rhs.toSet, newConstants.toSet)
            case _ =>
          }
          // we need to kill at assignment
          if (!instr.isInstanceOf[SSAReturnInstruction]) {
            result.add(inputDomain)
          }
          result
        }
      }
    }

    /**
     * standard flow function from callee to caller; just identity
     */
    override def getReturnFlowFunction(call: Block,
                                       src: Block,
                                       dest: Block): IFlowFunction = IdentityFlowFunction.identity
  }

  private val zeroTainted = AbsPath.Local(0)
  private val zeroTaint = null

  /**
   * Definition of the reaching definitions tabulation problem. Note that we choose to make the problem a <em>partially</em>
   * balanced tabulation problem, where the solver is seeded with the putstatic instructions themselves. The problem is partially
   * balanced since a definition in a callee used as a seed for the analysis may then reach a caller, yielding a "return" without a
   * corresponding "call." An alternative to this approach, used in the Reps-Horwitz-Sagiv POPL95 paper, would be to "lift" the
   * domain of putstatic instructions with a 0 (bottom) element, have a 0->0 transition in all transfer functions, and then seed the
   * analysis with the path edge (main_entry, 0) -> (main_entry, 0). We choose the partially-balanced approach to avoid pollution of
   * the flow functions.
   *
   */
  class DataFlowProblem extends PartiallyBalancedTabulationProblem[Block, CGNode, AbsDomain] {
    private val flowFunctions = new DataFlowFunctions(domain)
    /**
     * path edges corresponding to all putstatic instructions, used as seeds for the analysis
     */
    private val initialSeedsVal: util.Collection[PathEdge[Block]] = collectInitialSeeds

    /**
     * Collect the initial seeds for the analysis
     */
    private def collectInitialSeeds: util.Collection[PathEdge[Block]] = {
      val result: util.Collection[PathEdge[Block]] = new util.HashSet[PathEdge[Block]]()
      val itr = supergraph.getProcedureGraph.iterator
      while (itr.hasNext) {
        val cgNode = itr.next()
        val fakeEntry: Block = getFakeEntry(cgNode)
        val factNum = domain.add(Pair.make(zeroTainted, zeroTaint))
        icfg.getSuccNodes(fakeEntry).forEachRemaining((succ: Block) => {
          result.add(PathEdge.createPathEdge(fakeEntry, factNum, succ, factNum))
        })
      }
      result
    }

    /**
     * we use the entry block of the CGNode as the fake entry when propagating from callee to caller with unbalanced parens
     */
    override def getFakeEntry(node: Block): Block = {
      val cgNode = node.getNode
      getFakeEntry(cgNode)
    }

    /**
     * we use the entry block of the CGNode as the "fake" entry when propagating from callee to caller with unbalanced parens
     */
    private def getFakeEntry(cgNode: CGNode): Block = {
      val entriesForProcedure = supergraph.getEntriesForProcedure(cgNode)
      assert(entriesForProcedure.length == 1)
      entriesForProcedure(0)
    }

    override def getFunctionMap: IPartiallyBalancedFlowFunctions[Block] = flowFunctions

    override def getDomain: TabulationDomain[AbsDomain, Block] = domain

    /**
     * we don't need a merge function; the default unioning of tabulation works fine
     */
    override def getMergeFunction: IMergeFunction = null

    override def getSupergraph: ISupergraph[Block, CGNode] = supergraph

    override def initialSeeds: util.Collection[PathEdge[Block]] = initialSeedsVal
  }

  val problem = new DataFlowProblem
  private var solver: TabulationSolver[Block, CGNode, AbsDomain] = _

  def solve: TabulationResult[BasicBlockInContext[IExplodedBasicBlock], CGNode, AbsDomain] = {
    solver = PartiallyBalancedTabulationSolver.createPartiallyBalancedTabulationSolver(problem, null)
    solver.solve()
  }

  /**
   * Fact remapped back to abstract domain: a pair of (dependent: Int, dependencies: Set[Int])
   * each value is an Int due to SSA construction
   */
  class FactIterator(intSet: IntSet) extends Iterator[AbsDomain] {
    private val iter = intSet.intIterator

    override def hasNext: Boolean = iter.hasNext

    override def next(): AbsDomain = problem.getDomain.getMappedObject(iter.next())
  }

  /**
    * Collect facts at a given block from solution
    */
  def getFacts(results: TabulationResult[BasicBlockInContext[IExplodedBasicBlock], CGNode, AbsDomain],
               block: Block): Map[AbsPath, Set[AbsVal]] = {
    new FactIterator(results.getResult(block)).toList.groupBy(_.fst).map { case (k, v) => (k, v.map(_.snd).toSet)}
  }
}
