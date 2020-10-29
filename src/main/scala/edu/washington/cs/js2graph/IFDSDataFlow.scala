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
  def fromInstrGlobalName(globalName: String): AbsVal = {
    assert(globalName.startsWith("global "))
    AbsVal.Global(globalName.stripPrefix("global "))
  }

  /** FIXME: maybe we want to use NodeId for this case as well?
    *
    * @param name Name of the global object or its (potentially nested) sub-field, e.g. module.constructor
    */
  final case class Global(name: String) extends AbsVal

  final case class Constant(v: String) extends AbsVal

  final case class HasField(name: String) extends AbsVal

  /** Use instruction to track API instance constructed by the instruction
    */
  final case class Instance(className: String, instruction: SSAInstruction) extends AbsVal
}

sealed abstract class AbsPath extends Product with Serializable

object AbsPath {
  def fromInstrGlobalName(globalName: String): AbsPath = {
    assert(globalName.startsWith("global "))
    AbsPath.Global(globalName.stripPrefix("global "))
  }

  final case class Global(name: String) extends AbsPath

  final case class Lexical(name: String) extends AbsPath

  /** @param idx SSA index for any intermediate variable
    */
  final case class Local(idx: Int) extends AbsPath

  final case class Ret() extends AbsPath
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

  /** Map from invocation to the constructor API name
    *
    * FIXME: maybe multiple values?
    */
  private val intermediateOpNameAndAttrs = mutable.Map[SSAInstruction, mutable.Set[(String, Constants.NodeAttrs)]]()
  def getOpNodeNameAndAttrs(instruction: SSAInstruction): Set[(String, Constants.NodeAttrs)] =
    intermediateOpNameAndAttrs.getOrElse(instruction, mutable.Set()).toSet

  def addIntermediateOpNameAndAttrs(k: SSAInstruction, v: (String, Constants.NodeAttrs)): Unit = {
    intermediateOpNameAndAttrs.get(k) match {
      case Some(s) => s.add(v)
      case None    => intermediateOpNameAndAttrs.addOne(k, mutable.Set(v))
    }
  }

  /** controls numbering of putstatic instructions for use in tabulation
    */
  @SuppressWarnings(Array("serial"))
  class DataFlowDomain extends MutableMapping[AbsDomain] with TabulationDomain[AbsDomain, Block] {
    override def hasPriorityOver(p1: PathEdge[Block], p2: PathEdge[Block]): Boolean = {
      // Don't worry about worklist priorities
      false
    }
  }

  class DataFlowFunctions(val domain: DataFlowDomain) extends IPartiallyBalancedFlowFunctions[Block] {

    /** the flow function for flow from a callee to caller where there was no flow from caller to callee; just the identity function
      *
      * @see ReachingDefsProblem
      */
    override def getUnbalancedReturnFlowFunction(src: Block, dest: Block): IFlowFunction = IdentityFlowFunction.identity

    /** Flow function from caller to callee
      *
      * @param src Call-site
      * @param dest the entry of the callee
      * @param ret the block that will be returned to, in the caller. This can be null .. signifying
      *     that facts can flow into the callee but not return
      * @return the flow function for a "call" edge in the supergraph from src to desc
      */
    override def getCallFlowFunction(src: Block, dest: Block, ret: Block): IUnaryFlowFunction = {
      val invokeInstruction = src.getDelegate.getInstruction.asInstanceOf[JavaScriptInvoke]
      val symTable = src.getNode.getIR.getSymbolTable
      // Inside dest block, v3 corresponds to invokeInstruction.getUse(2)
      // v4 corresponds to invokeInstruction.getUse(2) ...
      getInvokeInstructionType(invokeInstruction) match {
        case InvokeType.INVOKE | InvokeType.DISPATCH =>
          inputDomain: Int => {
            val result = MutableSparseIntSet.makeEmpty
            val fact = domain.getMappedObject(inputDomain)
            for (paramIdx <- 2 until invokeInstruction.getNumberOfPositionalParameters) {
              // If passing from data-flow
              val param = invokeInstruction.getUse(paramIdx)
              if (fact.fst == AbsPath.Local(param)) {
                result.add(domain.add(Pair.make(AbsPath.Local(1 + paramIdx), fact.snd)))
              }
              // If passing from constant
              if (symTable.isConstant(param)) {
                val paramVal = symTable.getConstantValue(param).toString
                result.add(domain.add(Pair.make(AbsPath.Local(1 + paramIdx), AbsVal.Constant(paramVal))))
              }
            }
            fact.fst match {
              case _: AbsPath.Global             => result.add(inputDomain)
              case _: AbsPath.Lexical            => result.add(inputDomain)
              case local if local == zeroTainted => result.add(inputDomain)
              case _                             =>
            }
            result
          }
        case _ => KillEverything.singleton()
      }
    }

    private def getInvokeInstructionType(javaScriptInvoke: JavaScriptInvoke): InvokeType.Value = {
      javaScriptInvoke.getCallSite.getDeclaredTarget match {
        case JavaScriptMethods.ctorReference     => InvokeType.CONSTRUCT
        case JavaScriptMethods.dispatchReference => InvokeType.DISPATCH
        case _                                   => InvokeType.INVOKE
      }
    }

    /** Flow function from call node to return node when there are no targets for the call site
      *
      * Here we define the semantics of various types of "built-in" library API.
      */
    override def getCallNoneToReturnFlowFunction(src: Block, dest: Block): IUnaryFlowFunction = {
      val instr = src.getDelegate.getInstruction
      val symTable = src.getNode.getIR.getSymbolTable
      instr match {
        case invokeInstruction: JavaScriptInvoke =>
          getInvokeInstructionType(invokeInstruction) match {
            case InvokeType.CONSTRUCT =>
              /* Rule 1: Create a new instance node with type information

                  i: lhs = new C();
                --------------------
                lhs -> Instance(C, i)
               */
              inputDomain: Int => {
                val result = MutableSparseIntSet.makeEmpty
                result.add(inputDomain)
                val fact = domain.getMappedObject(inputDomain)
                if (fact.fst == AbsPath.Local(invokeInstruction.getReceiver)) {
                  fact.snd match {
                    case AbsVal.Global(globalName) =>
                      addIntermediateOpNameAndAttrs(
                        invokeInstruction,
                        (globalName, Map(JsNodeAttr.TYPE -> NodeType.Construct.toString, JsNodeAttr.TAG -> Tag.Singleton.toString)))
                      result.add(
                        domain.add(Pair.make(AbsPath.Local(invokeInstruction.getDef(0)), AbsVal.Instance(globalName, invokeInstruction))))
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

                /* Rule 2: The argument is a constant representing the name of required package.

                       i: lhs = f(a1, ...)
                       a1 = const_string(pkg_name)
                       f -> Global("require")
                    ---------------------------
                      lhs -> Global(pkg_name)
                 */
                if (!symTable.isConstant(invokeInstruction.getReceiver) &&
                  fact.fst == AbsPath.Local(invokeInstruction.getReceiver) &&
                  fact.snd == AbsVal.Global("require")) {
                  if (symTable.isConstant(invokeInstruction.getUse(2))) {
                    var requiredPkgName = symTable.getConstantValue(invokeInstruction.getUse(2)).toString
                    if (!Constants.nodeJsBuiltInGlobalNames.contains(requiredPkgName)) {
                      requiredPkgName = Constants.unknownModule + "(" + requiredPkgName + ")"
                    }
                    val lhs = AbsPath.Local(instr.getDef(0))
                    result.add(domain.add(Pair.make(lhs, AbsVal.Global(requiredPkgName))))
                  }
                }
                /* Rule 3.1 and 3.2: API invocation on instance

                    x -> Global(N) \/ x -> Instance(N, _)
                  ---------------------------------------
                              namespace(x, N)

                    lhs = f(base, ...)
                    f = const_string(funcName)
                    constructorOf(funcName, C)
                  -----------------------------
                      lhs -> Instance(C, i)

                    lhs = f(base, ...)
                    f = const_string(funcName)
                    ~constructorOf(funcName, C)
                    namespace(base, N)
                  -----------------------------
                      lhs -> Global(N.funcName)
                 */
                else if (invokeInstruction.getNumberOfUses >= 2) {
                  val receiverFuncIndex = invokeInstruction.getUse(0)
                  val dispatchBaseIndex = invokeInstruction.getUse(1)
                  if (symTable.isConstant(receiverFuncIndex)) {
                    /*
                      Example: For code

                        var crypto = require("crypto");
                        var cipher = crypto.createDecipher(...);

                      The receiverFunc will be "createDecipher". However, this is both a sink and an instance node.
                      Here, we are doing to create an instance node and point the base to it.
                     */
                    val receiverFunc = symTable.getConstantValue(receiverFuncIndex).toString
                    val apiNameSpaceAndTag =
                      if (fact.fst == AbsPath.Local(dispatchBaseIndex)) {
                        fact.snd match {
                          case AbsVal.Global(globalBaseName) => Some((globalBaseName, Tag.Singleton))
                          case AbsVal.Instance(className, _) => Some((className, Tag.Instance))
                          case _                             => None
                        }
                      } else if (Constants.moduleFieldNames.contains(receiverFunc)) {
                        Some(("module", Tag.Singleton))
                      } else {
                        None
                      }
                    if (apiNameSpaceAndTag.nonEmpty) {
                      val (apiNameSpace, tag) = apiNameSpaceAndTag.get
                      val apiName = apiNameSpace + "." + receiverFunc
                      val absVal = Constants.getConstructorAPI(receiverFunc) match {
                        case Some(constructedClassName) =>
                          // 1) LHS value is considered an instance returned from "built-in" API, which
                          //    can function as the parent of some other node in the dependency graph as well.
                          addIntermediateOpNameAndAttrs(
                            invokeInstruction,
                            (apiName, Map(JsNodeAttr.TYPE -> NodeType.Construct.toString, JsNodeAttr.TAG -> tag.toString)))
                          AbsVal.Instance(constructedClassName, invokeInstruction)
                        case None =>
                          addIntermediateOpNameAndAttrs(
                            invokeInstruction,
                            (apiName, Map(JsNodeAttr.TYPE -> NodeType.Call.toString, JsNodeAttr.TAG -> tag.toString)))
                          // 2) LHS value is still a global object, derived from the base global object.
                          //    In this case, we use the global object as its "source"
                          AbsVal.Global(apiName)
                      }
                      result.add(domain.add(Pair.make(AbsPath.Local(invokeInstruction.getDef(0)), absVal)))
                    }
                  } else {
                    /* Rule 4: Dynamic receiverFunc

                        i: lhs = f(...)
                        f -> Global(N)
                        libraryGlobalName(N)
                      ------------------------
                        lhs -> Global(N)
                     */
                    if (fact.fst == AbsPath.Local(receiverFuncIndex)) {
                      fact.snd match {
                        case AbsVal.Global(apiName) if Constants.isLibraryGlobalName(apiName) =>
                          addIntermediateOpNameAndAttrs(
                            invokeInstruction,
                            (apiName, Map(JsNodeAttr.TYPE -> NodeType.Call.toString, JsNodeAttr.TAG -> Tag.Singleton.toString)))
                          // 2) LHS value is still a global object, derived from the base global object.
                          //    In this case, we use the global object as its "source"
                          result.add(domain.add(Pair.make(AbsPath.Local(invokeInstruction.getDef(0)), AbsVal.Global(apiName))))
                        case _ => // TODO: other branches?
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

    /** flow function from call node to return node at a call site when callees exist
      */
    override def getCallToReturnFlowFunction(src: Block, dest: Block): IUnaryFlowFunction = {
      val instr = src.getDelegate.getInstruction
      val symTable = src.getNode.getIR.getSymbolTable
      instr match {
        case invokeInstruction: JavaScriptInvoke =>
          inputDomain: Int => {
            val result = MutableSparseIntSet.makeEmpty
            result.add(inputDomain)
            val fact = domain.getMappedObject(inputDomain)
            if (!symTable.isConstant(invokeInstruction.getReceiver) &&
              fact.fst == AbsPath.Local(invokeInstruction.getReceiver) &&
              // NOTE: this variant is for com.ibm.wala.cast.js.nodejs-1.5.4-sources.jar!/module-wrapper.js
              fact.snd == AbsVal.Global("module.require")) {
              if (symTable.isConstant(invokeInstruction.getUse(2))) {
                var requiredPkgName = symTable.getConstantValue(invokeInstruction.getUse(2)).toString
                if (!Constants.nodeJsBuiltInGlobalNames.contains(requiredPkgName)) {
                  requiredPkgName = Constants.unknownModule + "(" + requiredPkgName + ")"
                }
                val lhs = AbsPath.Local(instr.getDef(0))
                result.add(domain.add(Pair.make(lhs, AbsVal.Global(requiredPkgName))))
              }
            }
            result
          }
        case _ => IdentityFlowFunction.identity()
      }
    }

    type AbsValMapper = AbsVal => AbsVal

    /** @param withRhsTaint update the taint from RHS for new state
      */
    private def flowThrough(inputDomain: Int,
                            lhsVar: AbsPath,
                            rhsVars: Set[AbsPath],
                            newFlows: Set[AbsVal],
                            withRhsTaint: Option[AbsValMapper] = None): IntSet = {
      flow(inputDomain, Set(lhsVar), rhsVars, newFlows, withRhsTaint)
    }

    private def flow(inputDomain: Int,
                     lhsVars: Set[AbsPath],
                     rhsVars: Set[AbsPath],
                     newFlows: Set[AbsVal],
                     withRhsTaint: Option[AbsValMapper]): IntSet = {
      val fact = domain.getMappedObject(inputDomain)
      val tainted = fact.fst
      val taint: AbsVal = withRhsTaint match {
        case None         => fact.snd
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

    /** Flow function for normal intra-procedural edges
      */
    override def getNormalFlowFunction(src: Block, dest: Block): IUnaryFlowFunction = {
      val symTable = src.getNode.getIR.getSymbolTable
      new IUnaryFlowFunction {
        override def getTargets(inputDomain: Int): IntSet = {
          val instr = src.getDelegate.getInstruction
          val result = MutableSparseIntSet.makeEmpty
          result.add(inputDomain)
          if (instr == null) {
            return result
          }
          if (instr.isInstanceOf[JavaScriptCheckReference] || instr.isInstanceOf[SetPrototype]
            || instr.isInstanceOf[SSAConditionalBranchInstruction]
            || instr.isInstanceOf[JavaScriptTypeOfInstruction]
            || instr.isInstanceOf[EachElementGetInstruction]) {
            // do nothing
          } else
            instr match {
              case propertyWrite: JavaScriptPropertyWrite =>
                /* Rule 5:

                    o.m = ...
                    m = const_string(fieldName)
                  ------------------------------
                    o -> HasField(fieldName)
                 */
                val memberRef = propertyWrite.getMemberRef
                if (symTable.isConstant(memberRef)) {
                  val fieldName = symTable.getConstantValue(memberRef).toString
                  return flowThrough(inputDomain, AbsPath.Local(propertyWrite.getObjectRef), Set(), Set(AbsVal.HasField(fieldName)))
                }
              case astLexicalWrite: AstLexicalWrite =>
                /* Rule 6.1 6.2:

                    lexical(a@b) = x
                    x = constant(v)
                  --------------------
                    lexical(a@b) -> Constant(v)

                    lexical(a@b) = x
                    x -> D
                  --------------------
                    lexical(a@b) -> D
                 */
                var ret: IntSet = new EmptyIntSet()
                val from = astLexicalWrite.getUse(0)
                val newConstants = mutable.Set[AbsVal]()
                if (symTable.isConstant(from) && symTable.getConstantValue(from) != null) {
                  newConstants.add(AbsVal.Constant(symTable.getConstantValue(from).toString))
                }
                for (access <- astLexicalWrite.getAccesses) {
                  ret = ret.union(
                    flowThrough(
                      inputDomain,
                      AbsPath.Lexical(access.getName.fst + "@" + access.getName.snd),
                      Set(AbsPath.Local(from)),
                      newConstants.toSet))
                }
                return ret
              case astLexicalRead: AstLexicalRead =>
                /* Rule 7:

                    x = lexical(a@b)
                    lexical(a@b) -> d
                  --------------------
                    x -> d
                 */
                var ret: IntSet = new EmptyIntSet()
                for (access <- astLexicalRead.getAccesses) {
                  ret = ret.union(
                    flowThrough(
                      inputDomain,
                      AbsPath.Local(astLexicalRead.getDef(0)),
                      Set(AbsPath.Lexical(access.getName.fst + "@" + access.getName.snd)),
                      Set()))
                }
                return ret
              case lookup: PrototypeLookup =>
                /* Rule 8:

                    x = prototype y
                    y -> D
                  --------------------
                    x -> D
                 */
                return flowThrough(inputDomain, AbsPath.Local(lookup.getDef(0)), Set(AbsPath.Local(lookup.getUse(0))), Set())
              case readInstr: AstGlobalRead =>
                /* Rule 9:

                    x = readGlobal y
                  --------------------
                    x -> Global(y)
                 */
                return flowThrough(
                  inputDomain,
                  AbsPath.Local(readInstr.getDef(0)),
                  Set(),
                  Set(AbsVal.fromInstrGlobalName(readInstr.getGlobalName)))
              case writeInstr: AstGlobalWrite =>
                /* Rule 10:

                    writeGlobal y = x
                    x -> d
                  --------------------
                    y -> d
                 */
                return flowThrough(
                  inputDomain,
                  AbsPath.fromInstrGlobalName(writeInstr.getGlobalName),
                  Set(AbsPath.Local(writeInstr.getVal)),
                  Set())
              case getInstr: SSAGetInstruction =>
                /* Rule 11: Get global library object's field

                    x = y.fieldName
                    y -> Global(N)
                  --------------------
                    x -> N.fieldName
                 */
                val getFieldName = getInstr.getDeclaredField.getName.toString
                return flowThrough(
                  inputDomain,
                  AbsPath.Local(getInstr.getDef(0)),
                  Set(AbsPath.Local(instr.getUse(0))),
                  Set(),
                  Some({
                    case AbsVal.Global(name) =>
                      AbsVal.Global(name + "." + getFieldName)
                    // If has field 'exports', then it is relatd to com.ibm.wala.cast.js.nodejs-1.5.4-sources.jar!/module-wrapper.js
                    case AbsVal.HasField("exports") => AbsVal.Global("module." + getFieldName)
                    case x                          => x
                  })
                )
              case putInstr: SSAPutInstruction =>
                /* Rule 12:

                    put x.fieldName = y
                    y -> d
                  --------------------
                    x -> Global(fieldName) + d
                 */
                if (putInstr.getNumberOfUses > 1) {
                  val from = putInstr.getUse(1)
                  val to = putInstr.getUse(0)
                  val newConstants: Set[AbsVal] = if (!putInstr.isStatic) {
                    // when field name is "prototype", wae are
                    Set(AbsVal.HasField(putInstr.getDeclaredField.getName.toString))
                  } else {
                    Set()
                  }
                  return flowThrough(inputDomain, AbsPath.Local(to), Set(AbsPath.Local(from)), newConstants)
                }
              case javaScriptPropertyRead: JavaScriptPropertyRead =>
                /* Rule 13.1 13.2:

                    x = y.m
                    y -> d
                  --------------------
                    x -> d

                    x = y.m
                    y -> d
                    m = const_string(memberName)
                  --------------------
                    x -> d.memberName
                 */
                val from = javaScriptPropertyRead.getObjectRef
                val to = javaScriptPropertyRead.getDef(0)
                val from1 = javaScriptPropertyRead.getMemberRef
                val withRhsTaint: Option[AbsValMapper] = if (symTable.isConstant(from1) && symTable.getConstantValue(from1) != null) {
                  val fieldName = symTable.getConstantValue(from1).toString
                  Some({
                    case AbsVal.Global(name) =>
                      if (NumCruncher.isNumber(fieldName)) {
                        // indexing
                        AbsVal.Global(name)
                      } else {
                        AbsVal.Global(name + "." + fieldName)
                      }
                    case AbsVal.Instance(className, instruction) =>
                      if (NumCruncher.isNumber(fieldName)) {
                        // indexing
                        AbsVal.Instance(className, instruction)
                      } else {
                        AbsVal.Instance(className + "." + fieldName, instruction)
                      }
                    case x => x
                  })
                } else { None }
                return flowThrough(inputDomain, AbsPath.Local(to), Set(AbsPath.Local(from)), Set(), withRhsTaint)
              case binaryOpInstruction: SSABinaryOpInstruction =>
                /* Rule 14:

                    i: x = y binop z
                  --------------------
                    x -> Instance(binop, i)
                 */
                val lhs = instr.getDef(0)
                val opName = Constants.primBinaryOpName(binaryOpInstruction.getOperator)
                addIntermediateOpNameAndAttrs(binaryOpInstruction, (opName, Map(JsNodeAttr.TYPE -> NodeType.PrimOp.toString)))
                val absVal = AbsVal.Instance(opName, binaryOpInstruction)
                return flowThrough(inputDomain, AbsPath.Local(lhs), Set(), Set(absVal))
              case unaryOpInstruction: SSAUnaryOpInstruction =>
                /* Rule 15:

                    i: x = uop y
                  --------------------
                    x -> Instance(uop, i)
                 */
                val lhs = instr.getDef(0)
                val opName = Constants.primUnaryOpName(unaryOpInstruction.getOpcode)
                addIntermediateOpNameAndAttrs(unaryOpInstruction, (opName, Map(JsNodeAttr.TYPE -> NodeType.PrimOp.toString)))
                val absVal = AbsVal.Instance(opName, unaryOpInstruction)
                return flowThrough(inputDomain, AbsPath.Local(lhs), Set(), Set(absVal))
              case returnInstruction: SSAReturnInstruction =>
                /* Rule 16.1 16.2:

                    i: return x
                    x = constant(v)
                  --------------------
                    @ret -> Constant(v)

                    i: return x
                    x -> d
                  --------------------
                    @ret -> d
                 */
                val newConstants = mutable.Set[AbsVal]()
                val rhs = mutable.Set[AbsPath]()
                val from1 = returnInstruction.getResult
                if (from1 >= 0) {
                  if (symTable.isConstant(from1) && symTable.getConstantValue(from1) != null) {
                    newConstants.add(AbsVal.Constant(symTable.getConstantValue(from1).toString))
                  } else {
                    rhs.add(AbsPath.Local(from1))
                  }
                  return flowThrough(inputDomain, AbsPath.Ret(), rhs.toSet, newConstants.toSet)
                }
              case _ =>
            }
          result
        }
      }
    }

    /** Flow from exit block to call-site
      *
      * Used by `com.ibm.wala.dataflow.IFDS.TabulationSolver#propagateToReturnSites`
      *
      * @param call supergraph node of the call instruction for this return edge.
      * @param src exit node of the callee
      * @return the flow function for a "return" edge in the supergraph from src to dest
      */
    override def getReturnFlowFunction(call: Block, src: Block, dest: Block): IFlowFunction = {
      val invokeInstruction = call.getDelegate.getInstruction.asInstanceOf[JavaScriptInvoke]
      // Inside dest block, v3 corresponds to invokeInstruction.getUse(1)
      // v4 corresponds to invokeInstruction.getUse(2) ...
      if (invokeInstruction == null) {
        return IdentityFlowFunction.identity()
      }
      getInvokeInstructionType(invokeInstruction) match {
        case InvokeType.INVOKE | InvokeType.DISPATCH =>
          new IUnaryFlowFunction {
            override def getTargets(inputDomain: Int): IntSet = {
              val result = MutableSparseIntSet.makeEmpty
              val fact = domain.getMappedObject(inputDomain)
              if (fact.fst.isInstanceOf[AbsPath.Ret]) {
                if (fact.snd != AbsVal.Global(Constants.WALAGlobalContext)) {
                  val lhs = invokeInstruction.getDef
                  result.add(domain.add(Pair.make(AbsPath.Local(lhs), fact.snd)))
                }
              }
              result
            }
          }
        case _ => IdentityFlowFunction.identity()
      }
    }
  }

  private val zeroTainted = AbsPath.Local(0)
  private val zeroTaint = null

  /** Definition of the reaching definitions tabulation problem. Note that we choose to make the problem a <em>partially</em>
    * balanced tabulation problem, where the solver is seeded with the putstatic instructions themselves. The problem is partially
    * balanced since a definition in a callee used as a seed for the analysis may then reach a caller, yielding a "return" without a
    * corresponding "call." An alternative to this approach, used in the Reps-Horwitz-Sagiv POPL95 paper, would be to "lift" the
    * domain of putstatic instructions with a 0 (bottom) element, have a 0->0 transition in all transfer functions, and then seed the
    * analysis with the path edge (main_entry, 0) -> (main_entry, 0). We choose the partially-balanced approach to avoid pollution of
    * the flow functions.
    */
  class DataFlowProblem extends PartiallyBalancedTabulationProblem[Block, CGNode, AbsDomain] {
    private val flowFunctions = new DataFlowFunctions(domain)

    /** path edges corresponding to all putstatic instructions, used as seeds for the analysis
      */
    private val initialSeedsVal: util.Collection[PathEdge[Block]] = collectInitialSeeds

    /** Collect the initial seeds for the analysis
      */
    private def collectInitialSeeds: util.Collection[PathEdge[Block]] = {
      val result: util.Collection[PathEdge[Block]] = new util.HashSet[PathEdge[Block]]()
      val itr = supergraph.getProcedureGraph.iterator
      while (itr.hasNext) {
        val cgNode = itr.next()
        val fakeEntry: Block = getFakeEntry(cgNode)
        val factNum = domain.add(Pair.make(zeroTainted, zeroTaint))
        icfg
          .getSuccNodes(fakeEntry)
          .forEachRemaining((succ: Block) => {
            result.add(PathEdge.createPathEdge(fakeEntry, factNum, succ, factNum))
          })
      }
      result
    }

    /** we use the entry block of the CGNode as the fake entry when propagating from callee to caller with unbalanced parens
      */
    override def getFakeEntry(node: Block): Block = {
      val cgNode = node.getNode
      getFakeEntry(cgNode)
    }

    /** we use the entry block of the CGNode as the "fake" entry when propagating from callee to caller with unbalanced parens
      */
    private def getFakeEntry(cgNode: CGNode): Block = {
      val entriesForProcedure = supergraph.getEntriesForProcedure(cgNode)
      assert(entriesForProcedure.length == 1)
      entriesForProcedure(0)
    }

    override def getFunctionMap: IPartiallyBalancedFlowFunctions[Block] = flowFunctions

    override def getDomain: TabulationDomain[AbsDomain, Block] = domain

    /** we don't need a merge function; the default unioning of tabulation works fine
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

  /** Fact remapped back to abstract domain: a pair of (dependent: Int, dependencies: Set[Int])
    * each value is an Int due to SSA construction
    */
  class FactIterator(intSet: IntSet) extends Iterator[AbsDomain] {
    private val iter = intSet.intIterator

    override def hasNext: Boolean = iter.hasNext

    override def next(): AbsDomain = problem.getDomain.getMappedObject(iter.next())
  }

  /** Collect facts at a given block from solution
    */
  def getFacts(results: TabulationResult[BasicBlockInContext[IExplodedBasicBlock], CGNode, AbsDomain],
               block: Block): Map[AbsPath, Set[AbsVal]] = {
    new FactIterator(results.getResult(block)).toList.groupBy(_.fst).map { case (k, v) => (k, v.map(_.snd).toSet) }
  }
}
