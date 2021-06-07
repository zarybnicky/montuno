package montuno.interpreter

import com.oracle.truffle.api.TruffleLanguage
import montuno.Lvl
import montuno.Meta
import montuno.UnifyError
import montuno.interpreter.scope.*
import montuno.syntax.Loc
import montuno.truffle.Compiler

class LocalContext(val ctx: MontunoContext, val env: LocalEnv) {
    fun bind(loc: Loc, n: String?, inserted: Boolean, ty: Val): LocalContext = LocalContext(ctx, env.bind(loc, n, inserted, ty))
    fun define(loc: Loc, n: String, tm: Val, ty: Val): LocalContext = LocalContext(ctx, env.define(loc, n, tm, ty))

    fun eval(t: Term): Val = t.eval(ctx, env.vals)
    fun quote(v: Val, unfold: Boolean, depth: Lvl): Term = v.quote(depth, unfold)

    fun pretty(t: Term): String = t.pretty(NameEnv(ctx.ntbl)).toString()
    fun inline(t: Term): Term = t.inline(ctx, Lvl(0), env.vals)

    fun markOccurs(occurs: IntArray, blockIx: Int, t: Term): Unit = when {
        t is TMeta && t.meta.i == blockIx -> occurs[t.meta.j] += 1
        t is TLet -> { markOccurs(occurs, blockIx, t.type); markOccurs(occurs, blockIx, t.bound); markOccurs(occurs, blockIx, t.body) }
        t is TApp -> { markOccurs(occurs, blockIx, t.lhs); markOccurs(occurs, blockIx, t.rhs); }
        t is TLam -> markOccurs(occurs, blockIx, t.body)
        t is TPi -> { markOccurs(occurs, blockIx, t.bound); markOccurs(occurs, blockIx, t.body) }
        else -> {}
    }
}

class LocalEnv(
    val nameTable: NameTable,
    val vals: VEnv = VEnv(),
    val types: List<Val> = listOf(),
    val names: List<String?> = listOf()
) {
    val lvl: Lvl get() = Lvl(names.size)
    val locals: Array<Boolean> get() {
        val res = mutableListOf<Boolean>()
        for (i in types.indices) res.add(vals.it[i] == null)
        return res.toTypedArray()
    }
    fun bind(loc: Loc, n: String?, inserted: Boolean, ty: Val) = LocalEnv(
        if (n == null) nameTable else nameTable.withName(n, NILocal(loc, lvl, inserted)),
        vals.skip(),
        types + ty,
        names + n
    )
    fun define(loc: Loc, n: String, tm: Val, ty: Val) = LocalEnv(
        nameTable.withName(n, NILocal(loc, lvl, false)),
        vals + tm,
        types + ty,
        names + n
    )
}

class MontunoContext(val env: TruffleLanguage.Env) {
    val top = TopScope(env)
    var ntbl = NameTable()
    var loc: Loc = Loc.Unavailable
    var metas = MetaContext(this)
    val builtins = BuiltinScope(this)
    lateinit var compiler: Compiler

    fun makeLocalContext() = LocalContext(this, LocalEnv(ntbl))
    fun reset() {
        top.reset()
        metas = MetaContext(this)
        loc = Loc.Unavailable
        ntbl = NameTable()
    }
    fun printElaborated() {
        val ctx = makeLocalContext()
        for (i in top.it.indices) {
            for ((j, meta) in metas.it[i].withIndex()) {
                if (!meta.solved) throw UnifyError("Unsolved metablock")
                if (meta.unfoldable) continue
                println("  $i.$j = ${ctx.pretty(meta.term!!)}")
            }
            val topEntry = top.it[i]
            print("${topEntry.name} : ${ctx.pretty(topEntry.type)}")
            val defn = topEntry.defn
            if (defn != null) print(" = ${ctx.pretty(defn)}")
            println()
        }
    }
    fun registerMeta(m: Meta, v: Val) {
        val slot = metas[m]
        slot.solved = true
        slot.term = v.quote(Lvl(0), false)
        slot.unfoldable = slot.term!!.isUnfoldable()
        slot.value = v
    }
    fun registerTop(name: String, loc: Loc, defn: Term?, type: Term) {
        val lvl = Lvl(top.it.size)
        val topEntry = TopEntry(name, lvl, loc, defn, defn?.eval(this, VEnv()))
        ntbl.addName(name, NITop(loc, topEntry))
        top.it.add(topEntry)
        topEntry.type = type
        topEntry.typeV = type.eval(this, VEnv())
    }
    fun registerBuiltins(loc: Loc, ids: List<String>) {
        if ("ALL" in ids) {
            for (b in Builtin.values()) {
                getBuiltin(b.name, loc)
            }
        } else {
            ids.forEach { getBuiltin(it, loc) }
        }
    }
    fun getBuiltin(name: String, loc: Loc = Loc.Unavailable): Pair<Term, Val> {
        val ni = ntbl[name]
        if (ni.isEmpty()) {
            val lvl = Lvl(top.it.size)
            val topEntry = TopEntry(name, lvl, loc, null, null)
            ntbl.addName(name, NITop(loc, topEntry))
            top.it.add(topEntry)
            val (body, type) = builtins.getBuiltin(topEntry, name)
            topEntry.defn = null
            topEntry.defnV = body
            topEntry.type = type.quote(Lvl(0), false)
            topEntry.typeV = type
        }
        return makeLocalContext().inferVar(name)
    }
    fun getBuiltinVal(name: String) = makeLocalContext().inferVar(name).first.eval(this, VEnv())
}
