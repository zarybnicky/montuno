package montuno.interpreter

import montuno.interpreter.scope.NameEnv
import montuno.syntax.Icit

fun par(c: Boolean, x: String): String = if (c) "($x)" else x

fun Term.pretty(ns: NameEnv, p: Boolean = false): String = when (this) {
    is TMeta -> "?${meta.i}.${meta.j}"
    is TTop -> slot.name
    is TLocal -> ns[ix]
    is TUnit -> "Unit"
    is TNat -> n.toString()
    is TBool -> n.toString()
//    is TForeign -> "[$lang|$code|" + type.pretty(ns, false) + "]"
    is TLet -> {
        val d = ": " + type.pretty(ns, false) + "\n= " + bound.pretty(ns, false)
        val r = "let $name $d\nin " + body.pretty(ns + name, false)
        par(p, r)
    }
    is TApp -> par(p, lhs.pretty(ns, true) + " " + when (icit) {
        Icit.Impl -> "{" + rhs.pretty(ns, false) + "}"
        Icit.Expl -> rhs.pretty(ns, true)
    })
    is TLam -> {
        var x = ns.fresh(name)
        var nsLocal = ns + x
        var b = when (icit) {
            Icit.Expl -> "λ $x"
            Icit.Impl -> "λ {$x}"
        }
        var rest = body
        while (rest is TLam) {
            x = nsLocal.fresh(rest.name)
            nsLocal += x
            b += when (rest.icit) {
                Icit.Expl -> " $x"
                Icit.Impl -> " {$x}"
            }
            rest = rest.body
        }
        par(p, b + ". " + rest.pretty(nsLocal, false))
    }
    is TPi -> {
        var x = ns.fresh(name)
        var b = when {
            x == "_" -> bound.pretty(ns, true)
            icit == Icit.Impl -> "{$x : " + bound.pretty(ns, false) + "}"
            else -> "($x : " + bound.pretty(ns, false) + ")"
        }
        var nsLocal = ns + x
        var rest = body
        while (rest is TPi) {
            x = nsLocal.fresh(rest.name)
            b += when {
                x == "_" -> " → " + rest.bound.pretty(nsLocal, true)
                rest.icit == Icit.Expl -> " ($x : " + rest.bound.pretty(nsLocal, false) + ")"
                else -> " {$x : " + rest.bound.pretty(nsLocal, false) + "}"
            }
            nsLocal += x
            rest = rest.body
        }
        par(p, b + " → " + rest.pretty(nsLocal, p))
    }
    is TPair -> {
        val items = mutableListOf<String>()
        var x = this
        while (x is TPair) {
            items.add(x.lhs.pretty(ns, false))
            x = x.rhs
        }
        items.add(x.pretty(ns, false))
        par(p, items.joinToString(","))
    }
    is TProj1 -> body.pretty(ns, true) + ".1"
    is TProj2 -> body.pretty(ns, true) + ".2"
    is TProjF -> body.pretty(ns, true) + ".$name"
    is TSg -> {
        val arg = bound.pretty(ns, false)
        val l = if (name != null) "($name : $arg)" else arg
        par(p, l + " × " + body.pretty(ns, true))
    }

}
