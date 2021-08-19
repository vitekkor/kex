package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ValueTerm(override val type: KexType, override val name: String) : Term() {
    override val subTerms by lazy { emptyList<Term>() }

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}