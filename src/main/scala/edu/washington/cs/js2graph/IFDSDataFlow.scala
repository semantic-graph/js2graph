package edu.washington.cs.js2graph

import java.util.{Collection, HashSet}

import com.ibm.wala.cast.ir.ssa.{AstGlobalRead, AstGlobalWrite, AstLexicalWrite, EachElementGetInstruction}
import com.ibm.wala.cast.js.ssa._
import com.ibm.wala.dataflow.IFDS._
import com.ibm.wala.ipa.callgraph.CGNode
import com.ibm.wala.ipa.cfg.{BasicBlockInContext, ExplodedInterproceduralCFG}
import com.ibm.wala.ssa._
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock
import com.ibm.wala.util.collections.Pair
import com.ibm.wala.util.intset.{IntSet, MutableMapping, MutableSparseIntSet}

import scala.collection.mutable

sealed abstract class AbsVar extends Product with Serializable

object AbsVar {
  final case class Global(name: String) extends AbsVar

  /**
   * @param idx SSA index
   */
  final case class Local(idx: Int) extends AbsVar
}

class IFDSDataFlow(val icfg: ExplodedInterproceduralCFG) {
  final private val supergraph = ICFGSupergraph.make(icfg.getCallGraph)
  final private val domain = new DataFlowDomain
  type Block = BasicBlockInContext[IExplodedBasicBlock]
  type AbsDomain = Pair[AbsVar, AbsVar]

  /**
   * controls numbering of putstatic instructions for use in tabulation
   */
  @SuppressWarnings(Array("serial")) class DataFlowDomain
    extends MutableMapping[AbsDomain]
      with TabulationDomain[AbsDomain, Block] {
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

    /**
     * Flow function from call node to return node when there are no targets for the call site
     * FIXME: not a case we are expecting
     * if we're missing callees, just keep what information we have
     */
    override def getCallNoneToReturnFlowFunction(src: Block, dest: Block): IUnaryFlowFunction = {
      // TODO: imprecision for both application API and platform API
//      val instr = src.getDelegate.getInstruction
//      instr match {
//        case invoke:JavaScriptInvoke =>
//          d1: Int => {
//            val fact = domain.getMappedObject(d1)
//            val result = MutableSparseIntSet.makeEmpty
//            result.add(d1)
//            fact.fst match {
//              case AbsVar.Local(i) =>
//                if (invoke.getNumberOfReturnValues > 1) {
//                  for(paramIdx <- 1 until invoke.getNumberOfPositionalParameters){
//                    val param = invoke.getUse(paramIdx)
//                    // if i is one of the params of the function call
//                    if (param == i) {
//                      val deps = new util.HashSet[AbsVar](fact.snd)
//                      deps.add(AbsVar.Local(param))
//                      val to = invoke.getReturnValue(0)
//                      val factNum = domain.add(Pair.make(AbsVar.Local(to), deps))
//                      result.add(factNum)
//                    }
//                  }
//                }
//              case _ =>
//            }
//            result
//          }
//        case _ => IdentityFlowFunction.identity
//      }
      IdentityFlowFunction.identity
    }

    /**
     * flow function from call node to return node at a call site when callees exist. We kill everything; surviving facts should
     * flow out of the callee
     */
    override def getCallToReturnFlowFunction(src: Block, dest: Block): IUnaryFlowFunction = KillEverything.singleton

    private def flow(inputDomain: Int, lhsVar: AbsVar, rhsVars: Set[AbsVar], newFlows: Set[AbsVar]): IntSet = {
      val fact = domain.getMappedObject(inputDomain)
      val tainted: AbsVar = fact.fst
      val taint: AbsVar = fact.snd
      val result = MutableSparseIntSet.makeEmpty
      // if for propagating existing flow
      if (tainted != zeroTainted) {
        if (rhsVars.contains(tainted)) {
          result.add(domain.add(Pair.make(lhsVar, taint)))
        }
      } else { // if for new flow
        for (flow <- newFlows) {
          result.add(domain.add(Pair.make(lhsVar, flow)))
        }
      }
      // If not flowing to the killed LHS
      if (tainted != lhsVar) {
        result.add(inputDomain)
      }
      result
    }

    /**
     * flow function for normal intraprocedural edges
     *
     * FIXME: only test.js/<function-name> is analyzed. The implicit main function is not. What is wrong?
     */
    override def getNormalFlowFunction(src: Block, dest: Block): IUnaryFlowFunction = {
      val symTable = src.getNode.getIR.getSymbolTable
      new IUnaryFlowFunction {
        override def getTargets(inputDomain: Int): IntSet = {
          val instr = src.getDelegate.getInstruction
          // FIXME: The instr here of <Code body of function Leventstream.js> is not complete
          val result = MutableSparseIntSet.makeEmpty
          if (instr == null || (instr.getNumberOfUses < 1 && !instr.isInstanceOf[AstGlobalRead])
            || instr.isInstanceOf[JavaScriptCheckReference] || instr.isInstanceOf[SetPrototype]
            || instr.isInstanceOf[SSAConditionalBranchInstruction]
            || instr.isInstanceOf[AstLexicalWrite] || instr.isInstanceOf[JavaScriptTypeOfInstruction]
            || instr.isInstanceOf[EachElementGetInstruction]) {
            // do nothing
          } else instr match {
            case lookup: PrototypeLookup =>
              return flow(inputDomain, AbsVar.Local(lookup.getDef(0)), Set(AbsVar.Local(lookup.getUse(0))), Set())
            case readInstr: AstGlobalRead =>
              return flow(inputDomain, AbsVar.Local(readInstr.getDef), Set(), Set(AbsVar.Global(readInstr.getGlobalName)))
            case writeInstr: AstGlobalWrite =>
              return flow(inputDomain, AbsVar.Global(writeInstr.getGlobalName), Set(AbsVar.Local(writeInstr.getVal)), Set())
            case getInstr: SSAGetInstruction =>
              val newConstants: Set[AbsVar] = if (!getInstr.isStatic) {
                Set(AbsVar.Global("\"" + getInstr.getDeclaredField.getName + "\""))
              } else {
                Set()
              }
              return flow(inputDomain, AbsVar.Local(getInstr.getDef), Set(), newConstants)
            case putInstr: SSAPutInstruction =>
              if (putInstr.getNumberOfUses > 1) {
                val from = putInstr.getUse(1)
                val to = putInstr.getUse(0)
                val newConstants: Set[AbsVar] = if (!putInstr.isStatic) {
                  Set(AbsVar.Global("\"" + putInstr.getDeclaredField.getName + "\""))
                } else {
                  Set()
                }
                return flow(inputDomain, AbsVar.Local(to), Set(AbsVar.Local(from)), newConstants)
              }
            case javaScriptPropertyWrite: JavaScriptPropertyWrite =>
              // NOTE: this branch seems like a hot plate
              val from = javaScriptPropertyWrite.getUse(2)
              val to = javaScriptPropertyWrite.getObjectRef
              val newConstants: Set[AbsVar] = Set(AbsVar.Local(javaScriptPropertyWrite.getMemberRef))
              return flow(inputDomain, AbsVar.Local(to), Set(AbsVar.Local(from)), newConstants)
            case javaScriptPropertyRead: JavaScriptPropertyRead =>
              val from = javaScriptPropertyRead.getObjectRef
              val to = javaScriptPropertyRead.getDef
              val newConstants: Set[AbsVar] = Set(AbsVar.Local(javaScriptPropertyRead.getMemberRef))
              return flow(inputDomain, AbsVar.Local(to), Set(AbsVar.Local(from)), newConstants)
            case _: SSABinaryOpInstruction =>
              val from1 = instr.getUse(0)
              val from2 = instr.getUse(1)
              val lhs = instr.getDef
              val rhs = mutable.Set[AbsVar]()
              val newConstants = mutable.Set[AbsVar]()
              if (symTable.isConstant(from1)) {
                newConstants.add(AbsVar.Local(from1))
              } else {
                rhs.add(AbsVar.Local(from1))
              }
              if (symTable.isConstant(from2)) {
                newConstants.add(AbsVar.Local(from2))
              } else {
                rhs.add(AbsVar.Local(from2))
              }
              return flow(inputDomain, AbsVar.Local(lhs), rhs.toSet, newConstants.toSet)
            case _: SSAUnaryOpInstruction =>
              val from1 = instr.getUse(0)
              val lhs = instr.getDef
              if (symTable.isConstant(from1)) {
                return flow(inputDomain, AbsVar.Local(lhs), Set(), Set(AbsVar.Local(from1)))
              } else {
                return flow(inputDomain, AbsVar.Local(lhs), Set(AbsVar.Local(from1)), Set())
              }
            case _ =>
              val instrString = instr.toString
              if (!instrString.contains("throw ") && !instrString.contains("is instance of ") &
                !instrString.contains("isDefined")) {
//                System.out.println("Unhandled getNormalFlowFunction: " + instrString + ", " + instr.getClass.toString)
              }
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

  private val zeroTainted = AbsVar.Local(0)
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
  class ReachingDefsProblem extends PartiallyBalancedTabulationProblem[Block, CGNode, AbsDomain] {
    private val flowFunctions = new DataFlowFunctions(domain)
    /**
     * path edges corresponding to all putstatic instructions, used as seeds for the analysis
     */
    private val initialSeedsVal: Collection[PathEdge[Block]] = collectInitialSeeds

    /**
     * collect the putstatic instructions in the call graph as {@link PathEdge} seeds for the analysis
     */
    private def collectInitialSeeds: Collection[PathEdge[Block]] = {
      val result:Collection[PathEdge[Block]] = new HashSet[PathEdge[Block]]()
      val itr = supergraph.getProcedureGraph.iterator
      while (itr.hasNext) {
        val cgNode = itr.next()
        val fakeEntry = getFakeEntry(cgNode)
        val factNum = domain.add(Pair.make(zeroTainted, zeroTaint))
        icfg.getSuccNodes(fakeEntry).forEachRemaining((succ: Block) => {
          def foo(succ: Block) = {
            result.add(PathEdge.createPathEdge(fakeEntry, factNum, succ, factNum))
          }
          foo(succ)
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
    private def getFakeEntry(cgNode: CGNode) = {
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

    override def initialSeeds: Collection[PathEdge[Block]] = initialSeedsVal
  }

  val problem = new ReachingDefsProblem

  def solve: TabulationResult[BasicBlockInContext[IExplodedBasicBlock], CGNode, AbsDomain] = {
    PartiallyBalancedTabulationSolver.createPartiallyBalancedTabulationSolver(problem, null).solve()
  }
}
