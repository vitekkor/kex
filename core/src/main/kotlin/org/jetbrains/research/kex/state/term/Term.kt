package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.BaseType
import org.jetbrains.research.kex.InheritanceInfo
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.TypeInfo
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kthelper.assert.fail
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.defaultHashCode
import org.jetbrains.research.kthelper.logging.log

@BaseType("Term")
@Serializable
abstract class Term : TypeInfo {
    abstract val name: String
    abstract val subTerms: List<Term>
    abstract val type: KexType

    companion object {

        val terms = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("Term.json")
                    ?: fail { log.error("Could not load term inheritance info") }
            val inheritanceInfo = InheritanceInfo.fromJson(resource.bufferedReader().readText())
            resource.close()

            inheritanceInfo.inheritors.associate {
                it.name to loader.loadClass(it.inheritorClass)
            }
        }

        val reverse = terms.map { it.value to it.key }.toMap()
    }

    abstract fun <T : Transformer<T>> accept(t: Transformer<T>): Term

    override fun toString() = name
    override fun hashCode() = defaultHashCode(name, type, subTerms)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Term
        return this.name == other.name && this.type == other.type && this.subTerms == other.subTerms
    }

    override val inheritors get() = terms
    override val reverseMapping get() = reverse
}

val Term.isNamed
    get() = when (this) {
        is ArgumentTerm -> true
        is ReturnValueTerm -> true
        is ValueTerm -> true
        is ArrayIndexTerm -> true
        is FieldTerm -> true
        is ConstStringTerm -> true
        is ConstClassTerm -> true
        is StaticClassRefTerm -> true
        else -> false
    }

val Term.isConst
    get() = when (this) {
        is ConstBoolTerm -> true
        is ConstByteTerm -> true
        is ConstCharTerm -> true
        is ConstClassTerm -> true
        is StaticClassRefTerm -> true
        is ConstDoubleTerm -> true
        is ConstFloatTerm -> true
        is ConstIntTerm -> true
        is ConstLongTerm -> true
        is ConstShortTerm -> true
        is ConstStringTerm -> true
        else -> false
    }

val Term.numericValue: Number get() = when (this) {
    is ConstByteTerm -> value
    is ConstCharTerm -> value.code
    is ConstShortTerm -> value
    is ConstIntTerm -> value
    is ConstLongTerm -> value
    is ConstFloatTerm -> value
    is ConstDoubleTerm -> value
    else -> unreachable { log.error("Trying to get value of term: $this with type $type") }
}