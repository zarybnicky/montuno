package montuno.interpreter.scope

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import montuno.interpreter.*
import montuno.syntax.Icit
import montuno.truffle.*

@TypeSystemReference(Types::class)
class BuiltinRootNode(@field:Child var node: BuiltinNode, lang: TruffleLanguage<*>) : RootNode(lang) {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any? {
        for (i in frame.arguments.indices) {
            var v = frame.arguments[i]
            if (v is VThunk) {
                v = v.value
            }
            if (v is VMeta) {
                v = v.forceMeta()
            }
            if (v is VTop) {
                v = v.forceUnfold()
            }
            if (v is VLocal) {
                return VTop(node.slot, VSpine(frame.arguments.map { x -> SApp(Icit.Expl, x as Val) }.toTypedArray()))
            }
            frame.arguments[i] = v
        }
        return node.run(frame, frame.arguments)
    }
    init {
        callTarget = Truffle.getRuntime().createCallTarget(this)
    }
}

@TypeSystemReference(Types::class)
@ReportPolymorphism
abstract class BuiltinNode(val slot: TopEntry, val arity: Int) : Node() {
    abstract fun run(frame: VirtualFrame, args: Array<Any?>): Any?
}
abstract class Builtin1(slot: TopEntry) : BuiltinNode(slot, 1) {
    abstract fun execute(value: Any?): Any?
    override fun run(frame: VirtualFrame, args: Array<Any?>): Any? = execute(args[args.size - 1])
    @Specialization fun forceTh(value: VThunk) = execute(value.value)
    @Specialization fun forceTt(value: VTop) = execute(value.forceUnfold())
    @Specialization fun forceM(value: VMeta) = execute(value.forceUnfold())
}
abstract class Builtin2(slot: TopEntry) : BuiltinNode(slot, 2) {
    abstract fun execute(left: Any?, right: Any?): Any?
    override fun run(frame: VirtualFrame, args: Array<Any?>): Any? = execute(args[args.size - 2], args[args.size - 1])
    @Specialization fun forceLTh(value: VThunk, r: Any) = execute(value.value, r)
    @Specialization fun forceLTt(value: VTop, r: Any) = execute(value.forceUnfold(), r)
    @Specialization fun forceLM(value: VMeta, r: Any) = execute(value.forceUnfold(), r)
    @Specialization fun forceRTh(r: Any, value: VThunk) = execute(r, value.value)
    @Specialization fun forceRTt(r: Any, value: VTop) = execute(r, value.forceUnfold())
    @Specialization fun forceRM(r: Any, value: VMeta) = execute(r, value.forceUnfold())
}
abstract class Builtin3(slot: TopEntry) : BuiltinNode(slot, 3) {
    abstract fun execute(a: Any?, b: Any?, c: Any?): Any?
    override fun run(frame: VirtualFrame, args: Array<Any?>): Any? = execute(args[args.size - 3], args[args.size - 2], args[args.size - 1])
}
abstract class SuccBuiltin(slot: TopEntry) : Builtin1(slot) {
    @Specialization fun succ(value: VNat): Val = VNat(value.n + 1)
}
abstract class AddBuiltin(slot: TopEntry) : Builtin2(slot) {
    @Specialization fun add(l: VNat, r: VNat): Val = VNat(l.n + r.n)
}
abstract class SubBuiltin(slot: TopEntry) : Builtin2(slot) {
    @Specialization fun add(l: VNat, r: VNat): Val = VNat(l.n - r.n)
}
abstract class NatElimBuiltin(slot: TopEntry) : Builtin3(slot) {
    @Specialization fun eq(z: VNat, s: VLam, a: VNat): Val = if (a.n == 0) z else s.app(Icit.Expl, VNat(a.n - 1))
    @Specialization fun forceLTh(value: VThunk, r: Any, s: Any) = execute(value.value, r, s)
    @Specialization fun forceLTt(value: VTop, r: Any, s: Any) = execute(value.forceUnfold(), r, s)
    @Specialization fun forceLM(value: VMeta, r: Any, s: Any) = execute(value.forceUnfold(), r, s)
}
abstract class EqnBuiltin(slot: TopEntry) : Builtin2(slot) {
    @Specialization fun eq(l: VNat, r: VNat): Val = VBool(l.n == r.n)
}
abstract class GeqnBuiltin(slot: TopEntry) : Builtin2(slot) {
    @Specialization fun eq(l: VNat, r: VNat): Val = VBool(l.n >= r.n)
}
abstract class LeqnBuiltin(slot: TopEntry) : Builtin2(slot) {
    @Specialization fun eq(l: VNat, r: VNat): Val = VBool(l.n <= r.n)
}
abstract class CondBuiltin(slot: TopEntry) : Builtin3(slot) {
    @Specialization fun cond(c: VBool, l: Val, r: Val): Val = if (c.n) l else r
    @Specialization fun forceLTh(value: VThunk, r: Any, s: Any) = execute(value.value, r, s)
    @Specialization fun forceLTt(value: VTop, r: Any, s: Any) = execute(value.forceUnfold(), r, s)
    @Specialization fun forceLM(value: VMeta, r: Any, s: Any) = execute(value.forceUnfold(), r, s)
}

@Suppress("LeakingThis")
abstract class FixNatF(slot: TopEntry, language: TruffleLanguage<*>) : Builtin2(slot) {
    private val target: CallTarget = Truffle.getRuntime().createCallTarget(BuiltinRootNode(this, language))
    @Child var dispatch: Dispatch = DispatchNodeGen.create()
    @Specialization
    fun fix(f: VLam, n: VNat): Any {
        val cl = VLam("n", Icit.Expl, VUnit, ConstClosure(arrayOf(f), arrayOf(), 2, target))
        return dispatch.executeDispatch(f.closure.callTarget, arrayOf(cl, n))
    }
}

enum class Builtin { Nat, Bool, True, False, zero, succ, add, sub, eqn, leqn, geqn, cond, natElim, fixNatF }

class BuiltinScope(val ctx: MontunoContext) {
    fun getBuiltin(t: TopEntry, n: String): Pair<Val?, Val> = Builtin.valueOf(n).let { getVal(t, it) to getType(it) }
    fun getType(t: Builtin): Val = types.computeIfAbsent(t) { createBuiltinType(it) }
    private fun getVal(t: TopEntry, i: Builtin): Val? = values.computeIfAbsent(i) { createBuiltinValue(t, it) }

    private val values: MutableMap<Builtin, Val?> = mutableMapOf()
    private val types: MutableMap<Builtin, Val> = mutableMapOf()

    // utilities for creating builtins
    private fun const(v: Val): RootNode = TruffleRootNode(arrayOf(CConstant(v, null)), ctx.top.lang, FrameDescriptor())
    private fun fromHeads(vararg hs: ConstHead, root: RootNode): Val {
        val h = hs.first()
        val hRest = hs.drop(1).toTypedArray()
        return h.toVal(h.bound, ConstClosure(emptyArray(), hRest, hRest.size + 1, root.callTarget))
    }
    private fun buildLam(vararg heads: Builtin, root: BuiltinNode): Val = fromHeads(
        *heads.map { ConstHead(false, null, Icit.Expl, ctx.getBuiltinVal(it.name)) }.toTypedArray(),
        root=BuiltinRootNode(root, ctx.top.lang)
    )
    private fun buildPi(vararg heads: Builtin, root: Builtin): Val = fromHeads(
        *heads.map { ConstHead(true, null, Icit.Expl, ctx.getBuiltinVal(it.name)) }.toTypedArray(),
        root=const(ctx.getBuiltinVal(root.name))
    )

    private fun createBuiltinValue(t: TopEntry, n: Builtin): Val? = when (n) {
        Builtin.Nat -> VUnit
        Builtin.Bool -> VUnit
        Builtin.True -> VBool(true)
        Builtin.False -> VBool(false)
        Builtin.zero -> VNat(0)
        Builtin.succ -> buildLam(Builtin.Nat, root=SuccBuiltinNodeGen.create(t))
        Builtin.add -> buildLam(Builtin.Nat, Builtin.Nat, root=AddBuiltinNodeGen.create(t))
        Builtin.sub -> buildLam(Builtin.Nat, Builtin.Nat, root=SubBuiltinNodeGen.create(t))
        Builtin.natElim -> buildLam(Builtin.Nat, Builtin.succ, root=NatElimBuiltinNodeGen.create(t))
        Builtin.leqn -> buildLam(Builtin.Nat, Builtin.Nat, root=LeqnBuiltinNodeGen.create(t))
        Builtin.geqn -> buildLam(Builtin.Nat, Builtin.Nat, root=GeqnBuiltinNodeGen.create(t))
        Builtin.eqn -> buildLam(Builtin.Nat, Builtin.Nat, root=EqnBuiltinNodeGen.create(t))
        Builtin.cond -> buildLam(Builtin.Bool, Builtin.Nat, Builtin.Nat, root=CondBuiltinNodeGen.create(t))
        Builtin.fixNatF -> fromHeads(
            ConstHead(false, "f", Icit.Expl, getType(Builtin.fixNatF)),
            ConstHead(false, "x", Icit.Expl, ctx.getBuiltinVal("Nat")),
            root=BuiltinRootNode(FixNatFNodeGen.create(t, ctx.top.lang), ctx.top.lang),
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
