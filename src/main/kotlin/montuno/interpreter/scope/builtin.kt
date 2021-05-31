package montuno.interpreter.scope

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import montuno.interpreter.*
import montuno.syntax.Icit
import montuno.truffle.*

@TypeSystemReference(Types::class)
class BuiltinRootNode(@field:Child var node: BuiltinNode, lang: TruffleLanguage<*>) : RootNode(lang) {
    override fun execute(frame: VirtualFrame): Any? = node.run(frame, frame.arguments)
    init {
        callTarget = Truffle.getRuntime().createCallTarget(this)
    }
}

// arity -> TLams
@TypeSystemReference(Types::class)
@ReportPolymorphism
abstract class BuiltinNode : Node() {
    abstract fun run(frame: VirtualFrame, args: Array<Any?>): Any?
}
abstract class Builtin1 : BuiltinNode() {
    abstract fun execute(value: Any?): Any?
    override fun run(frame: VirtualFrame, args: Array<Any?>): Any? = execute(args[args.size - 1])
    @Specialization fun forceTh(value: VThunk) = execute(value.value)
    @Specialization fun forceTt(value: VTop) = execute(value.forceUnfold())
    @Specialization fun forceM(value: VMeta) = execute(value.forceUnfold())
}
abstract class Builtin2 : BuiltinNode() {
    abstract fun execute(left: Any?, right: Any?): Any?
    override fun run(frame: VirtualFrame, args: Array<Any?>): Any? = execute(args[args.size - 2], args[args.size - 1])
    @Specialization fun forceLTh(value: VThunk, r: Any) = execute(value.value, r)
    @Specialization fun forceLTt(value: VTop, r: Any) = execute(value.forceUnfold(), r)
    @Specialization fun forceLM(value: VMeta, r: Any) = execute(value.forceUnfold(), r)
    @Specialization fun forceRTh(r: Any, value: VThunk) = execute(r, value.value)
    @Specialization fun forceRTt(r: Any, value: VTop) = execute(r, value.forceUnfold())
    @Specialization fun forceRM(r: Any, value: VMeta) = execute(r, value.forceUnfold())
}
abstract class Builtin3 : BuiltinNode() {
    abstract fun execute(a: Any?, b: Any?, c: Any?): Any?
    override fun run(frame: VirtualFrame, args: Array<Any?>): Any? = execute(args[args.size - 3], args[args.size - 2], args[args.size - 1])
}
abstract class SuccBuiltin : Builtin1() {
    @Specialization fun succ(value: VNat): Val = VNat(value.n + 1)
}
abstract class AddBuiltin : Builtin2() {
    @Specialization fun add(l: VNat, r: VNat): Val = VNat(l.n + r.n)
}
abstract class SubBuiltin : Builtin2() {
    @Specialization fun add(l: VNat, r: VNat): Val = VNat(l.n - r.n)
}
abstract class NatElimBuiltin : Builtin3() {
    @Specialization fun eq(z: VNat, s: VLam, a: VNat): Val = if (a.n == 0) z else s.app(Icit.Expl, VNat(a.n - 1))
    @Specialization fun forceLTh(value: VThunk, r: Any, s: Any) = execute(value.value, r, s)
    @Specialization fun forceLTt(value: VTop, r: Any, s: Any) = execute(value.forceUnfold(), r, s)
    @Specialization fun forceLM(value: VMeta, r: Any, s: Any) = execute(value.forceUnfold(), r, s)
}
abstract class EqnBuiltin : Builtin2() {
    @Specialization fun eq(l: VNat, r: VNat): Val = VBool(l.n == r.n)
}
abstract class GeqnBuiltin : Builtin2() {
    @Specialization fun eq(l: VNat, r: VNat): Val = VBool(l.n >= r.n)
}
abstract class LeqnBuiltin : Builtin2() {
    @Specialization fun eq(l: VNat, r: VNat): Val = VBool(l.n <= r.n)
}
abstract class CondBuiltin : Builtin3() {
    @Specialization fun cond(c: VBool, l: Val, r: Val): Val = if (c.n) l else r
    @Specialization fun forceLTh(value: VThunk, r: Any, s: Any) = execute(value.value, r, s)
    @Specialization fun forceLTt(value: VTop, r: Any, s: Any) = execute(value.forceUnfold(), r, s)
    @Specialization fun forceLM(value: VMeta, r: Any, s: Any) = execute(value.forceUnfold(), r, s)
}

abstract class FixNatF(language: TruffleLanguage<*>) : Builtin2() {
    private val target: CallTarget = Truffle.getRuntime().createCallTarget(BuiltinRootNode(this, language))
    @CompilerDirectives.CompilationFinal
    private var self: Val? = null
    @Child var dispatch: Dispatch = DispatchNodeGen.create()
    @Specialization
    fun fix(f: VLam, n: VNat): Any? {
        if (self == null) {
            CompilerDirectives.transferToInterpreter()
            val cl = VLam("f", Icit.Expl, VUnit, ConstClosure(arrayOf(), arrayOf(ConstHead(false, "n", Icit.Expl, VUnit)), 2, target))
            self = cl.app(Icit.Expl, cl)
        }
        // only works with Truffle!
        return dispatch.executeDispatch(f.closure.callTarget, arrayOf(self, n))
    }
}

enum class Builtin { Nat, Bool, True, False, zero, succ, add, sub, eqn, leqn, geqn, cond, natElim, fixNatF }

class BuiltinScope(val ctx: MontunoContext) {
    fun getBuiltin(n: String): Pair<Val?, Val> = Builtin.valueOf(n).let { getVal(it) to getType(it) }
    fun getType(t: Builtin): Val = types.computeIfAbsent(t) { createBuiltinType(it) }
    private fun getVal(t: Builtin): Val? = values.computeIfAbsent(t) { createBuiltinValue(it) }

    private val values: MutableMap<Builtin, Val?> = mutableMapOf()
    private val types: MutableMap<Builtin, Val> = mutableMapOf()

    // utilities for creating builtins
    private fun const(v: Val): RootNode = TruffleRootNode(arrayOf(CConstant(v, null)), ctx.top.lang, FrameDescriptor())
    private fun fromHeads(vararg hs: ConstHead, root: RootNode): Val {
        val h = hs.first()
        val hRest = hs.drop(1).toTypedArray()
        return h.toVal(h.bound, ConstClosure(emptyArray(), hRest, hRest.size + 1, root.callTarget))
    }
    private fun buildLam(vararg bs: Builtin, root: BuiltinNode): Val = fromHeads(
        *bs.map { ConstHead(false, null, Icit.Expl, ctx.getBuiltinVal(it.name)) }.toTypedArray(),
        root=BuiltinRootNode(root, ctx.top.lang)
    )
    private fun buildPi(vararg bs: Builtin, root: Builtin): Val = fromHeads(
        *bs.map { ConstHead(true, null, Icit.Expl, ctx.getBuiltinVal(it.name)) }.toTypedArray(),
        root=const(ctx.getBuiltinVal(root.name))
    )

    private fun createBuiltinValue(n: Builtin): Val? = when (n) {
        Builtin.Nat -> VUnit
        Builtin.Bool -> VUnit
        Builtin.True -> VBool(true)
        Builtin.False -> VBool(false)
        Builtin.zero -> VNat(0)
        Builtin.succ -> buildLam(Builtin.Nat, root=SuccBuiltinNodeGen.create())
        Builtin.add -> buildLam(Builtin.Nat, Builtin.Nat, root=AddBuiltinNodeGen.create())
        Builtin.sub -> buildLam(Builtin.Nat, Builtin.Nat, root=SubBuiltinNodeGen.create())
        Builtin.natElim -> buildLam(Builtin.Nat, Builtin.succ, root=NatElimBuiltinNodeGen.create())
        Builtin.leqn -> buildLam(Builtin.Nat, Builtin.Nat, root=LeqnBuiltinNodeGen.create())
        Builtin.geqn -> buildLam(Builtin.Nat, Builtin.Nat, root=GeqnBuiltinNodeGen.create())
        Builtin.eqn -> buildLam(Builtin.Nat, Builtin.Nat, root=EqnBuiltinNodeGen.create())
        Builtin.cond -> buildLam(Builtin.Bool, Builtin.Nat, Builtin.Nat, root=CondBuiltinNodeGen.create())
        Builtin.fixNatF -> fromHeads(
            ConstHead(false, "f", Icit.Expl, getType(Builtin.fixNatF)),
            ConstHead(false, "x", Icit.Expl, ctx.getBuiltinVal("Nat")),
            root=BuiltinRootNode(FixNatFNodeGen.create(ctx.top.lang), ctx.top.lang),
        )
    }
    private fun createBuiltinType(n: Builtin): Val = when (n) {
        Builtin.Nat -> VUnit
        Builtin.Bool -> VUnit
        Builtin.True -> ctx.getBuiltinVal("Bool")
        Builtin.False -> ctx.getBuiltinVal("Bool")
        Builtin.zero -> ctx.getBuiltinVal("Nat")
        Builtin.succ -> buildPi(Builtin.Nat, root=Builtin.Nat)
        Builtin.add -> buildPi(Builtin.Nat, Builtin.Nat, root=Builtin.Nat)
        Builtin.sub -> buildPi(Builtin.Nat, Builtin.Nat, root=Builtin.Nat)
        Builtin.natElim -> buildPi(Builtin.Nat, Builtin.succ, root=Builtin.Nat)
        Builtin.leqn -> buildPi(Builtin.Nat, Builtin.Nat, root=Builtin.Bool)
        Builtin.geqn -> buildPi(Builtin.Nat, Builtin.Nat, root=Builtin.Bool)
        Builtin.eqn -> buildPi(Builtin.Nat, Builtin.Nat, root=Builtin.Bool)
        Builtin.cond -> buildPi(Builtin.Bool, Builtin.Nat, Builtin.Nat, root=Builtin.Nat)
        Builtin.fixNatF -> fromHeads(
            ConstHead(true, null, Icit.Expl, fromHeads(
                ConstHead(true, null, Icit.Expl, getType(Builtin.succ)),
                ConstHead(true, null, Icit.Expl, ctx.getBuiltinVal("Nat")),
                root=const(ctx.getBuiltinVal("Nat")),
            )),
            ConstHead(true, null, Icit.Expl, ctx.getBuiltinVal("Nat")),
            root=const(ctx.getBuiltinVal("Nat")),
        )
    }
}
