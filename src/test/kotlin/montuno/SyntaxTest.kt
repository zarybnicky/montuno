package montuno

import montuno.syntax.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyntaxTest {
    @Test
    fun testLetNatExpr() {
        assertEquals(
            parsePreSyntaxExpr("let i : Nat = 5 in i"),
            RLet(
                Loc.Range(0, 20),
                "i",
                RVar(Loc.Range(8, 3), "Nat"),
                RNat(Loc.Range(14, 1), 5),
                RVar(Loc.Range(19, 1), "i")
            )
        )
    }

    @Test
    fun testNfTop() {
        assertEquals(
            parsePreSyntax("{-# PRETTY 5 #-}"),
            listOf(RCommand(
                Loc.Range(0, 16),
                Pragma.PRETTY,
                RNat(Loc.Range(11, 1), 5)
            )),
        )
    }
}
