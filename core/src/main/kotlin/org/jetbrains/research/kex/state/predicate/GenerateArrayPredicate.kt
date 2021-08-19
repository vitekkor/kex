package org.jetbrains.research.kex.state.predicate

import kotlinx.serialization.Contextual
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

@InheritorOf("Predicate")
@Serializable
class GenerateArrayPredicate(
    val lhv: Term,
    val length: Term,
    val generator: Term,
    @Required
    override val type: PredicateType = PredicateType.State(),
    @Required
    @Contextual
    override val location: Location = Location()
) : Predicate() {
    override val operands by lazy { listOf(lhv, length, generator) }
    val elementType: KexType get() = (lhv.type as KexArray).element

    override fun print() = "$lhv = generate<$elementType[]>($length) { $generator }"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tLhv = t.transform(lhv)
        val tLength = t.transform(length)
        val tGenerator = t.transform(generator)
        return when {
            tLhv == lhv && tLength == length && tGenerator == generator -> this
            else -> predicate(type, location) { generateArray(tLhv, tLength, tGenerator) }
        }
    }
}