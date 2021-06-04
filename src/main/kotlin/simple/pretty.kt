package simple

enum class Prec {
    Atom,
    App,
    Pi,
    Let
}

fun par(l: Prec, r: Prec, x: String): String = if (l < r) "($x)" else x

fun Term.pretty(ns: NameEnv?, p: Prec = Prec.Atom): String = when (this) {
    is TVar -> ns[ix]
    is TNat -> n.toString()
    is TApp -> par(p, Prec.App, arg.pretty(ns, Prec.App) + " " + body.pretty(ns, Prec.Atom))
    is TLam -> {
        var x = ns.fresh(arg)
        var nsLocal = ns + x
        var rest = body
        var b = "λ $x"
        while (rest is TLam) {
            x = nsLocal.fresh(rest.arg)
            nsLocal += x
            rest = rest.body
            b += " $x"
        }
        par(p, Prec.Let, b + ". " + rest.pretty(nsLocal, Prec.Let))
    }
    is TU -> "*"
    is TPi -> when (arg) {
        "_" -> par(p, Prec.Pi, ty.pretty(ns, Prec.App) + " " + body.pretty(ns + "_", Prec.Pi))
        else -> {
            var x = ns.fresh(arg)
            var b = "($x : " + ty.pretty(ns, Prec.Let) + ")"
            var nsLocal = ns + x
            var rest = body
            while (rest is TPi) {
                x = nsLocal.fresh(rest.arg)
                b += if (x == "_") " → " + rest.ty.pretty(nsLocal, Prec.App)
                else " ($x : " + rest.ty.pretty(nsLocal, Prec.Let) + ")"
                nsLocal += x
                rest = rest.body
            }
            par(p, Prec.Pi, b + " → " + rest.pretty(nsLocal, Prec.Pi))
        }
    }
    is TLet -> {
        val d = ": " + ty.pretty(ns, Prec.Let) + "\n= " + bind.pretty(ns, Prec.Let)
        val r = "let $n $d\nin " + body.pretty(ns + n, Prec.Let)
        par(p, Prec.Let, r)
    }
}