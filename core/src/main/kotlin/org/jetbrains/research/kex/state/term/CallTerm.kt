package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Method

@InheritorOf("Term")
@Serializable
class CallTerm(
        override val type: KexType,
        val owner: Term,
        @Contextual val method: Method,
        val arguments: List<Term>) : Term() {
    override val name = "$owner.${method.name}(${arguments.joinToString()})"
    override val subTerms by lazy { listOf(owner) + arguments }

    val isStatic: Boolean
        get() = owner is StaticClassRefTerm

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tOwner = t.transform(owner)
        val tArguments = arguments.map { t.transform(it) }
        return when {
            tOwner == owner && tArguments == arguments -> this
            else -> term { tf.getCall(method, tOwner, tArguments) }
        }
    }
}