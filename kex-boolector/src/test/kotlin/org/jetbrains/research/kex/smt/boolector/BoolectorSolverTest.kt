package org.jetbrains.research.kex.smt.boolector

import org.jetbrains.research.boolector.Btor
import org.jetbrains.research.kex.KexTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

class BoolectorSolverTest : KexTest() {
    @Test
    fun testRunnable() {
        val ef = BoolectorExprFactory()

        val a = ef.makeInt("a")
        val b = ef.makeInt("b")
        val c = ef.makeInt("c")

        val zero = ef.makeIntConst(0)
        val one = ef.makeIntConst(1)

        val query = c neq zero

        val state = c eq (ef.if_<Int_>(a gt b).then(zero).`else`(one))

        query.asAxiom().assertForm()
        state.asAxiom().assertForm()
        val res = ef.ctx.check()
        assertEquals(Btor.Status.SAT, res)
        ef.ctx.release()
    }

    @Test
    fun testDefaultMemory() {
        val ef = BoolectorExprFactory()
        val checkExpr = { e: Dynamic_ ->
            e.axiom.toBoolNode().assume()
            BoolectorEngine.negate(ef.ctx, e.expr).assume()
            ef.ctx.check() == Btor.Status.UNSAT
        }
        val memory = ef.makeDefaultMemory<Word_>("mem", ef.makeIntConst(0xFF))
        for (i in 0..128) {
            assertTrue(checkExpr(memory[ef.makePtrConst(i)] eq Word_.makeConst(ef.ctx, 0xFF)))
        }
        ef.ctx.release()
    }

    @Test
    fun testMergeMemory() {
        val ef = BoolectorExprFactory()

        val default = BoolectorContext(ef)
        val memA = BoolectorContext(ef)
        val memB = BoolectorContext(ef)

        val ptr = ef.makePtr("ptr")
        val a = ef.makeIntConst(0xDEAD)
        val b = ef.makeIntConst(0xBEEF)
        val z = ef.makeIntConst(0xABCD)

        val cond = ef.makeInt("cond")
        val condA = cond eq a
        val condB = cond eq b

        memA.writeWordMemory(ptr, 0, a)
        memB.writeWordMemory(ptr, 0, b)

        val merged = BoolectorContext.mergeContexts("merged", default, mapOf(
                condA to memA,
                condB to memB
        ))

        val c = merged.readWordMemory(ptr, 0)

        val checkExprIn = { e: Bool_, `in`: Dynamic_ ->
            `in`.asAxiom().assume()

            val pred = ef.makeBool("\$CHECK$")
            val ptrll = ef.makePtr("ptrll")
            (ptrll eq c).asAxiom().assume()
            (pred implies !e).asAxiom().assume()

            val prede = pred.expr
            prede.assume()
            val res = ef.ctx.check()
            res == Btor.Status.UNSAT
        }

        assertTrue(checkExprIn(c eq a, cond eq a))
        assertFalse(checkExprIn(c eq a, cond eq z))
        assertTrue(checkExprIn(c eq b, cond eq b))
        ef.ctx.release()
    }

    @Test
    fun testLogic() {
        val ctx = Btor()
        BoolectorEngine.initialize()

        val checkExpr = { expr: Bool_ ->
            expr.axiom.toBoolNode().assume()
            expr.expr.toBoolNode().not().assume()
            val res = ctx.check()
            res == Btor.Status.UNSAT
        }

        val `true` = Bool_.makeConst(ctx, true)
        val `false` = Bool_.makeConst(ctx, false)
        val expr = !(`true` and `false`)
        assertTrue(checkExpr(expr))
        assertTrue(checkExpr(`true` or `false`))
        assertTrue(checkExpr(!(`true` eq `false`)))
        assertTrue(checkExpr(`true` neq `false`))

        val a = Word_.makeConst(ctx, 0xFF)
        val b = Word_.makeConst(ctx, 0xFF)
        assertTrue(checkExpr(a eq b))

        val d = Long_.makeConst(ctx, 0xFF)
        val e = Word_.makeConst(ctx, 0xFF)
        assertTrue(checkExpr(d eq e))
        ctx.release()
    }
}

