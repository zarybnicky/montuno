package montuno.interpreter.scope

import montuno.Lvl
import montuno.Meta
import montuno.interpreter.Term
import montuno.interpreter.Val
import montuno.syntax.Loc

data class TopEntry(val name: String, val lvl: Lvl, val loc: Loc, var defn: Term?, var defnV: Val?) {
    lateinit var type: Term
    lateinit var typeV: Val
}
class MetaEntry(val loc: Loc, val meta: Meta, val type: Val) {
    var term: Term? = null
    var value: Val? = null
    var solved: Boolean = false
    var unfoldable: Boolean = false
}
