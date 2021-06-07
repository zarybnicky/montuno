package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.NodeUtil.concat
import com.oracle.truffle.api.nodes.RootNode
import montuno.interpreter.*
import montuno.syntax.Icit
import java.util.*

abstract class Closure {
    fun inst(v: Val): Val = execute(v) as Val
    fun executeAny(vararg args: Any?): Any? = execute(*args.map { x -> when (x) {
        is Int -> VNat(x)
        else -> x
    } }.toTypedArray())
    abstract fun execute(vararg args: Any?): Any?
    abstract val callTarget: CallTarget
}

class PureRootNode(val ctx: MontunoContext, val body: Term, lang: TruffleLanguage<MontunoContext>) : RootNode(lang) {
    init {
        callTarget = Truffle.getRuntime().createCallTarget(this)
    }
    override fun execute(frame: VirtualFrame): Any? = body.eval(ctx, VEnv(frame.arguments))
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
data class PureClosure(val ctx: MontunoContext, val env: VEnv, val body: Term) : TruffleObject, Closure() {
    override val callTarget: CallTarget by lazy { // fallback to Truffle if necessary
        val (heads, bodyTerm) = stripHeads(body)
        val body = PureRootNode(ctx, bodyTerm, ctx.top.lang).callTarget
        TruffleClosure(ctx, env.it, heads, heads.size + 1, body).callTarget
    }
    @ExportMessage fun isExecutable() = true
    @ExportMessage override fun execute(vararg args: Any?): Val {
        assert(args.size == 1)
        return body.eval(ctx, VEnv(concat(env.it, args)))
    }
}

data class ClosureHead(val isPi: Boolean, val name: String?, val icit: Icit, val bound: Term) {
    fun toVal(bound: Val, closure: Closure): Val = when {
        isPi -> VPi(name, icit, bound, closure)
        else -> VLam(name, icit, bound, closure)
    }
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class TruffleClosure (
    @JvmField val ctx: MontunoContext,
    @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val papArgs: Array<out Any?>,
    @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val heads: Array<ClosureHead>,
    @JvmField val maxArity: Int,
    override val callTarget: CallTarget
) : TruffleObject, Closure() {
    val arity: Int = heads.size + 1
    @ExportMessage fun isExecutable() = true
    @Throws(ArityException::class, UnsupportedTypeException::class)
    @ExportMessage override fun execute(vararg args: Any?): Any? = when {
        args.isEmpty() -> TODO("impossible")
        args.size < arity -> {
            val newArgs = concat(papArgs, args)
            val head = heads[args.size - 1]
            val remaining = heads.copyOfRange(args.size, heads.size)
            val closure = TruffleClosure(ctx, newArgs, remaining, maxArity, callTarget)
            head.toVal(head.bound.eval(ctx, VEnv(newArgs)), closure)
        }
        args.size == arity -> callTarget.call(*concat(papArgs, args))
        else -> {
            val resArgs = concat(papArgs, args)
            val g = callTarget.call(*Arrays.copyOfRange(resArgs, 0, maxArity)) as Val
            val rest = Arrays.copyOfRange(resArgs, maxArity, resArgs.size).map { SApp(Icit.Expl, it as Val) }
            VSpine(rest.toTypedArray()).applyTo(g)
        }
    }
}

data class ConstHead(val isPi: Boolean, val name: String?, val icit: Icit, val bound: Val) {
    fun toVal(bound: Val, closure: Closure): Val = when {
        isPi -> VPi(name, icit, bound, closure)
        else -> VLam(name, icit, bound, closure)
    }
}
@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class ConstClosure (
    @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val papArgs: Array<Any?>,
    @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val heads: Array<ConstHead>,
    @JvmField val maxArity: Int,
    override val callTarget: CallTarget
) : TruffleObject, Closure() {
    val arity: Int = heads.size + 1
    @ExportMessage fun isExecutable() = true
    @Throws(ArityException::class, UnsupportedTypeException::class)
    @ExportMessage override fun execute(vararg args: Any?): Any? = when {
        args.isEmpty() -> TODO("impossible")
        args.size < arity -> {
            val newArgs = concat(papArgs, args)
            val head = heads[args.size - 1]
            val remaining = heads.copyOfRange(args.size, heads.size)
            val closure = ConstClosure(newArgs, remaining, maxArity, callTarget)
            head.toVal(head.bound, closure)
        }
        args.size == arity -> callTarget.call(*concat(papArgs, args))
        else -> {
            val resArgs = concat(papArgs, args)
            val g = callTarget.call(*Arrays.copyOfRange(resArgs, 0, maxArity)) as Val
            val rest = Arrays.copyOfRange(resArgs, maxArity, resArgs.size).map { SApp(Icit.Expl, it as Val) }
            VSpine(rest.toTypedArray()).applyTo(g)
        }
    }
}

class TruffleRootNode(@field:Children val nodes: Array<Code>, lang: TruffleLanguage<MontunoContext>, fd: FrameDescriptor) : RootNode(lang, fd) {
    init {
        callTarget = Truffle.getRuntime().createCallTarget(this)
    }
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any? {
        var res: Any? = VUnit
        for (e in nodes) {
            res = e.execute(frame)
        }
        return res
    }
}