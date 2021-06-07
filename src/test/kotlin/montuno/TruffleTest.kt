package montuno

import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Test

abstract class SourceTest {
    abstract val src: String
    abstract fun check(x: Value)
    @Test fun pure() = makeCtx().use { ctx ->
        val x = ctx.eval("montuno-pure", src)
        check(x)
    }
    @Test fun truffle() = makeCtx().use { ctx ->
        val x = ctx.eval("montuno", src)
        check(x)
    }
}

class IdTest : SourceTest() {
    override val src = "id : {A}->A->A = \\x.x; {-# NORMAL id #-}"
    override fun check(x: Value) = assert(x.toString() == "λ {A} x. x")
}
class IdPartialTest : SourceTest() {
    override val src: String = "id : {A}->A->A = \\x.x; {-# NORMAL id {Unit} #-}"
    override fun check(x: Value) = assert(x.toString() == "λ x. x")
}
class IdFullTest : SourceTest() {
    override val src: String = "id : {A}->A->A = \\x.x; {-# NORMAL id {Unit} Unit #-}"
    override fun check(x: Value) = assert(x.toString() == "Unit")
}
class ConstIdTest : SourceTest() {
    override val src: String = "id:{A}->A->A=\\x.x; const:{A B}->A->B->A=\\x y.x;{-# NORMAL const id #-}"
    override fun check(x: Value) = assert(x.toString() == "λ y x. x")
}
class CountDownTest : SourceTest() {
    override val src: String = "{-# BUILTIN ALL #-}; {-# NORMAL fixNatF (\\rec x. cond (eqn x 0) 0 (rec (sub x 1))) 1 #-}"
    override fun check(x: Value) = assert(x.asInt() == 0)
}
class CountUpTest : SourceTest() {
    override val src: String = "{-# BUILTIN ALL #-}; {-# NORMAL fixNatF (\\rec x. cond (leqn 1000 x) x (rec (add x 1))) 0 #-}"
    override fun check(x: Value) = assert(x.asInt() == 1000)
}
class PolyglotCountUpTest : SourceTest() {
    override val src: String = "{-# BUILTIN ALL #-}; {-# NORMAL fixNatF (\\rec x. cond (leqn 1000 x) x (rec (add x 1))) #-}"
    override fun check(x: Value) {
        val res = x.execute(0)
        assert(res.asInt() == 1000)
    }
}
class FibTest : SourceTest() {
    override val src: String = "{-# BUILTIN ALL #-}; {-# NORMAL fixNatF (\\rec x. cond (leqn x 1) x (add (rec (sub x 1)) (rec (sub x 2)))) 7 #-}"
    override fun check(x: Value) = assert(x.asInt() == 13)
}

class PolyglotIdTest : SourceTest() {
    override val src: String = "\\x.x"
    override fun check(x: Value) = assert(x.execute(5).asInt() == 5)
}
class PolyglotConstTest : SourceTest() {
    override val src: String = "const:{A B}->A->B->A=\\x y.x; {-# NORMAL const #-}"
    override fun check(x: Value) = assert(x.execute(5).execute(42).asInt() == 5)
}
class PolyglotConstIdTest : SourceTest() {
    override val src: String = "id:{A}->A->A=\\x.x; const:{A B}->A->B->A=\\x y.x; {-# NORMAL const id #-}"
    override fun check(x: Value) = assert(x.execute(42).execute(5).asInt() == 5)
}
