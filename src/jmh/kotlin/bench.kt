package montuno.bench

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.source.Source
import montuno.MontunoPure
import montuno.MontunoTruffle
import org.graalvm.polyglot.Context
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
abstract class SourceBenchmark {
    abstract val text: CharSequence
    val pureTarget: CallTarget by lazy {
        val ctx = Context.create()
        ctx.enter()
        ctx.initialize("montuno-pure")
        MontunoPure.lang.parse(Source.newBuilder("montuno-pure", text, "<bench>").build())
    }
    val truffleTarget: CallTarget by lazy {
        val ctx = Context.create()
        ctx.enter()
        ctx.initialize("montuno")
        MontunoTruffle.lang.parse(Source.newBuilder("montuno", text, "<bench>").build())
    }
    @Benchmark @Warmup(iterations=20)
    fun montunoPure() = pureTarget.call()
    @Benchmark @Warmup(iterations=20)
    fun montunoTruffle() = truffleTarget.call()
}

open class Add : SourceBenchmark() {
    override val text = "fixNatF (\\rec x. if leq 1000 x then x else rec (add x 1)) 0"
    @Benchmark
    fun kotlin(bh: Blackhole) {
        var x = 0
        while (x <= 1000) { x = Math.addExact(x, 1) }
        bh.consume(x)
    }
}

//
//open class AddLet : SourceBenchmark() {
//    override val text = "let add : Nat -> Nat = \\(x : Nat) -> if le 1000 x then x else add (plus x 1) in add 0"
//}

//fun fib(x: Int): Int = if (x <= 1) x else fib (x - 1) + fib (x - 2)
//open class Fib : SourceBenchmark() {
//    override val text = "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if le x 1 then x else plus (f (minus x 1)) (f (minus x 2))) 15"
//    @Benchmark
//    fun kotlin(bh: Blackhole) {
//        bh.consume(fib(15))
//    }
//}
