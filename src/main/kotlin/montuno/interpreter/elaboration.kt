package montuno.interpreter

import montuno.ElabError
import montuno.Lvl
import montuno.MetaInsertion
import montuno.interpreter.scope.Builtin
import montuno.interpreter.scope.NILocal
import montuno.interpreter.scope.NITop
import montuno.syntax.*

fun LocalContext.check(t: PreTerm, want: Val): Term {
    ctx.loc = t.loc
    val v = want.forceUnfold()
    return when {
        t is RLam && v is VPi && t.arg.match(v) -> {
            val inner = bind(t.loc, t.bind.name, false, v.bound)
            val body = inner.check(t.body, v.closure.inst(VLocal(env.lvl)))
            TLam(t.bind.name, v.icit, v.bound.quote(env.lvl, false), body)
        }
        v is VPi && v.icit == Icit.Impl -> {
            val inner = bind(t.loc, v.name, true, v.bound)
            val body = inner.check(t, v.closure.inst(VLocal(env.lvl)))
            TLam(v.name, Icit.Impl, v.bound.quote(env.lvl, false), body)
        }
        t is RHole -> ctx.metas.freshMeta(env, want.quote(env.lvl, false))
        t is RLet -> {
            val a = if (t.type == null) ctx.metas.freshType(env) else check(t.type, VUnit)
            val va = eval(a)
            val tm = check(t.defn, va)
            val vt = eval(tm)
            val u = define(t.loc, t.n, va, vt).check(t.body, want)
            TLet(t.n, a, tm, u)
        }
        t is RPair && v is VSg -> {
            val lhs = check(t.lhs, v.bound)
            val rhs = check(t.rhs, v.closure.inst(eval(lhs)))
            TPair(lhs, rhs)
        }
        else -> {
            val (tt, has) = infer(MetaInsertion.Yes, t)
            unify(env.lvl, ConvState.Rigid, has, want)
            tt
        }
    }
}

fun LocalContext.insertMetas(mi: MetaInsertion, c: Pair<Term, Val>): Pair<Term, Val> {
    var (t, va) = c
    when (mi) {
        MetaInsertion.No -> {}
        MetaInsertion.Yes -> {
            var vaf = va.forceMeta()
            while (vaf is VPi && vaf.icit == Icit.Impl) {
                val m = ctx.metas.freshType(env)
                t = TApp(Icit.Impl, t, m)
                va = vaf.closure.inst(eval(m))
                vaf = va.forceMeta()
            }
        }
        is MetaInsertion.UntilName -> {
            var vaf = va.forceMeta()
            while (vaf is VPi && vaf.icit == Icit.Impl) {
                if (vaf.name == mi.n) {
                    return t to va
                }
                val m = ctx.metas.freshType(env)
                t = TApp(Icit.Impl, t, m)
                va = vaf.closure.inst(eval(m))
                vaf = va.forceMeta()
            }
            throw ElabError(ctx.loc, "No named arg ${mi.n}")
        }
    }
    return t to va
}

fun LocalContext.inferVar(n: String): Pair<Term, Val> {
    for (ni in env.nameTable[n].asReversed()) {
        if (ni is NITop) {
            return TTop(ni.lvl, ctx.top[ni.lvl]) to ctx.top[ni.lvl].typeV
        }
        if (ni is NILocal && !ni.inserted) {
            return TLocal(ni.lvl.toIx(env.lvl)) to env.types[ni.lvl.it]
        }
    }
    throw ElabError(null, "Variable $n out of scope")
}

fun LocalContext.infer(mi: MetaInsertion, r: PreTerm): Pair<Term, Val> {
    ctx.loc = r.loc
    return when (r) {
        is RVar -> insertMetas(mi, inferVar(r.n))
        is RLet -> {
            val a = if (r.type == null) ctx.metas.freshType(env) else check(r.type, VUnit)
            val gva = eval(a)
            val t = check(r.defn, gva)
            val gvt = eval(t)
            val (u, gvb) = define(r.loc, r.n, gvt, gva).infer(mi, r.body)
            TLet(r.n, a, t, u) to gvb
        }
        is RPi -> {
            val n = r.bind.name
            val a = check(r.type, VUnit)
            val b = bind(r.loc, n, false, eval(a)).check(r.body, VUnit)
            TPi(n, r.icit, a, b) to VUnit
        }
        is RApp -> {
            val icit = if (r.arg.icit == Icit.Expl) Icit.Expl else Icit.Impl
            val (t, va) = infer(r.arg.metaInsertion, r.rator)
            val v = va.forceUnfold()
            if (v !is VPi) throw ElabError(r.loc, "function type expected, instead got $v")
            if (v.icit != icit) throw ElabError(r.loc, "Icit mismatch")
            val u = check(r.rand, v.bound)
            insertMetas(mi, TApp(v.icit, t, u) to v.closure.inst(eval(u)))
        }
        is RLam -> {
            val n = r.bind.name
            val icit = r.arg.icit ?: throw ElabError(r.loc, "named lambda")
            val a = ctx.metas.freshType(env)
            val va = eval(a)
            val (t, vb) = bind(r.loc, n, false, va).infer(MetaInsertion.Yes, r.body)
            val b = quote(vb, false, env.lvl + 1)
            insertMetas(mi, TLam(n, icit, a, t) to VPi(n, icit, va, ctx.compiler.buildClosure(b, VEnv())))
        }
        is RSg -> {
            val n = r.bind.name
            val a = check(r.type, VUnit)
            val b = bind(r.loc, n, false, eval(a)).check(r.body, VUnit)
            TSg(n, a, b) to VUnit
        }
        is RPair -> {
            val (t, va) = infer(mi, r.lhs)
            val (u, vb) = infer(mi, r.rhs)
            val b = quote(vb, false, env.lvl)
            TPair(t, u) to VSg(null, va, ctx.compiler.buildClosure(b, VEnv()))
        }
        is RProj1 -> {
            val (t, va) = infer(mi, r.body)
            val v = va.forceUnfold()
            if (v !is VSg) throw ElabError(r.loc, "sigma type expected, instead got $v")
            TProj1(t) to v.bound
        }
        is RProj2 -> {
            val (t, va) = infer(mi, r.body)
            val v = va.forceUnfold()
            if (v !is VSg) throw ElabError(r.loc, "sigma type expected, instead got $v")
            TProj2(t) to v.closure.inst(eval(t).proj1())
        }
        is RProjF -> {
            val (t, va) = infer(mi, r.body)
            var i = 0
            var sg = va.forceUnfold()
            while (sg is VSg) {
                if (sg.name == r.field) return TProjF(r.field, t, i) to sg.bound
                i += 1
                sg = sg.proj2().forceUnfold()
            }
            // let go :: S.Tm -> V.Val -> V.Ty -> Int -> IO Infer
            //     go topT t sg i = case forceFUE cxt sg of
            //         V.Sg x a au b bu
            //             | NName topX == x -> pure $ Infer (S.ProjField topT x i) a au
            ///            | otherwise       -> go topT (vProj2 t) (b $$$ unS (vProj2 t)) (i + 1)
            throw ElabError(r.loc, "no such field ${r.field} in $va")
        }

        is RU -> TUnit to VUnit
        is RNat -> TNat(r.n) to ctx.builtins.getType(Builtin.Nat)
        is RHole -> {
            val m1 = ctx.metas.freshType(env)
            val m2 = ctx.metas.freshMeta(env, m1)
            m1 to eval(m2)
        }
    }
}

fun getIds(r: PreTerm): List<String> = when (r) {
    is RVar -> listOf(r.n)
    is RApp -> getIds(r.rator) + getIds(r.rand)
    else -> throw RuntimeException("Invalid format of a BUILTIN pragma: $r")
}

fun checkDeclaration(top: MontunoContext, loc: Loc, n: String, ty: PreTerm) {
    top.metas.newMetaBlock()
    var a = top.makeLocalContext().check(ty, VUnit)
    top.metas.simplifyMetaBlock()
    a = top.makeLocalContext().inline(a)
    top.registerTop(n, loc, null, a)
}

fun checkDefinition(top: MontunoContext, loc: Loc, n: String, ty: PreTerm?, tm: PreTerm) {
    top.metas.newMetaBlock()
    val ctx = LocalContext(top, LocalEnv(top.ntbl))
    var (a, va) = if (ty != null) {
        val a = ctx.check(ty, VUnit)
        a to ctx.eval(a)
    } else try {
        val va = ctx.inferVar(n).second
        ctx.quote(va, false, Lvl(0)) to va
    } catch (e: ElabError) {
        TUnit to VUnit
    }
    var t = ctx.check(tm, va)
    top.metas.simplifyMetaBlock()
    a = ctx.inline(a)
    t = ctx.inline(t)
    top.registerTop(n, loc, t, a)
}

fun checkTerm(top: MontunoContext, e: PreTerm): Pair<Term, Val> {
    top.metas.newMetaBlock()
    val ctx = LocalContext(top, LocalEnv(top.ntbl))
    var (a, t) = ctx.infer(MetaInsertion.No, e)
    top.metas.simplifyMetaBlock()
    a = ctx.inline(a)
    return a to t
}
