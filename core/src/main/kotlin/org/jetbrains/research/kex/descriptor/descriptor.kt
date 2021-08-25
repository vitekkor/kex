package org.jetbrains.research.kex.descriptor

import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.basic
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }

class NoConcreteInstanceException(val klass: Class) : Exception()

val Class.isInstantiable: Boolean
    get() = when {
        visibilityLevel > this.visibility -> false
        this.isAbstract -> false
        this.isInterface -> false
        else -> true
    }

fun KexClass.concreteClass(cm: ClassManager): KexClass {
    val kfgKlass = this.kfgClass(cm.type)
    val concrete = when {
        kfgKlass !is ConcreteClass -> throw NoConcreteInstanceException(kfgKlass)
        kfgKlass.isInstantiable -> kfgKlass
        else -> ConcreteInstanceGenerator[kfgKlass]
    }
    return concrete.kexType
}

fun KexType.concrete(cm: ClassManager): KexType = when (this) {
    is KexClass -> `try` { this.concreteClass(cm) }.getOrDefault(this)
    is KexReference -> KexReference(this.reference.concrete(cm))
    else -> this
}

sealed class Descriptor(term: Term, type: KexType) {
    var term = term
        protected set

    var type = type
        protected set

    val query: PredicateState get() = collectQuery(mutableSetOf())
    val asString: String get() = print(mutableMapOf())
    val depth: Int get() = countDepth(setOf(), mutableMapOf())

    val typeInfo: PredicateState get() = generateTypeInfo(mutableSetOf())

    operator fun contains(other: Descriptor): Boolean = this.contains(other, mutableSetOf())

    override fun toString() = asString
    infix fun eq(other: Descriptor) = this.structuralEquality(other, mutableSetOf<Pair<Descriptor, Descriptor>>())
    infix fun neq(other: Descriptor) = !(this eq other)

    abstract fun print(map: MutableMap<Descriptor, String>): String
    abstract fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean
    abstract fun collectQuery(set: MutableSet<Descriptor>): PredicateState

    abstract fun countDepth(visited: Set<Descriptor>, cache: MutableMap<Descriptor, Int>): Int
    abstract fun concretize(cm: ClassManager, visited: MutableSet<Descriptor> = mutableSetOf()): Descriptor
    abstract fun deepCopy(copied: MutableMap<Descriptor, Descriptor> = mutableMapOf()): Descriptor
    abstract fun reduce(visited: MutableSet<Descriptor> = mutableSetOf()): Descriptor
    abstract fun generateTypeInfo(visited: MutableSet<Descriptor> = mutableSetOf()): PredicateState
    abstract fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean
}

sealed class ConstantDescriptor(term: Term, type: KexType) : Descriptor(term, type) {

    override fun concretize(cm: ClassManager, visited: MutableSet<Descriptor>) = this
    override fun deepCopy(copied: MutableMap<Descriptor, Descriptor>) = this
    override fun reduce(visited: MutableSet<Descriptor>) = this
    override fun generateTypeInfo(visited: MutableSet<Descriptor>) = emptyState()

    override fun countDepth(visited: Set<Descriptor>, cache: MutableMap<Descriptor, kotlin.Int>) = 1
    override fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean = this.term == other.term

    object Null : ConstantDescriptor(term { generate(KexNull()) }, KexNull()) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = null"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality null }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>) =
            other is Null
    }

    class Bool(val value: Boolean) : ConstantDescriptor(term { generate(KexBool()) }, KexBool()) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Bool) return false
            return this.value == other.value
        }
    }

    class Byte(val value: kotlin.Byte) : ConstantDescriptor(term { generate(KexByte()) }, KexByte()) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Byte) return false
            return this.value == other.value
        }
    }

    class Char(val value: kotlin.Char) : ConstantDescriptor(term { generate(KexChar()) }, KexChar()) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Char) return false
            return this.value == other.value
        }
    }

    class Short(val value: kotlin.Short) : ConstantDescriptor(term { generate(KexShort()) }, KexShort()) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Short) return false
            return this.value == other.value
        }
    }

    class Int(val value: kotlin.Int) : ConstantDescriptor(term { generate(KexInt()) }, KexInt()) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Int) return false
            return this.value == other.value
        }
    }

    class Long(val value: kotlin.Long) : ConstantDescriptor(term { generate(KexLong()) }, KexLong()) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Long) return false
            return this.value == other.value
        }
    }

    class Float(val value: kotlin.Float) : ConstantDescriptor(term { generate(KexFloat()) }, KexFloat()) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Float) return false
            return this.value == other.value
        }
    }

    class Double(val value: kotlin.Double) : ConstantDescriptor(term { generate(KexDouble()) }, KexDouble()) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Double) return false
            return this.value == other.value
        }
    }
}

@Suppress("UNCHECKED_CAST")
sealed class FieldContainingDescriptor<T : FieldContainingDescriptor<T>>(
    term: Term,
    klass: KexClass
) :
    Descriptor(term, klass) {
    var klass = klass
        protected set

    val fields = mutableMapOf<Pair<String, KexType>, Descriptor>()

    operator fun get(key: Pair<String, KexType>) = fields[key]
    operator fun get(field: String, type: KexType) = get(field to type)

    operator fun set(key: Pair<String, KexType>, value: Descriptor) {
        fields[key] = value
    }

    operator fun set(field: String, type: KexType, value: Descriptor) = set(field to type, value)

    fun remove(field: String, type: KexType): Descriptor? = fields.remove(field to type)
    fun remove(field: Pair<String, KexType>): Descriptor? = fields.remove(field)

    fun merge(other: T): T {
        val newFields = other.fields + this.fields
        this.fields.clear()
        this.fields.putAll(newFields)
        return this as T
    }

    fun accept(other: T): T {
        val newFields = other.fields.mapValues { it.value.deepCopy(mutableMapOf(other to this)) }
        this.fields.clear()
        this.fields.putAll(newFields)
        return this as T
    }

    override fun print(map: MutableMap<Descriptor, String>): String {
        if (this in map) return ""//map[this]!!
        map[this] = term.name
        return buildString {
            appendLine("$term = $klass {")
            for ((field, value) in fields) {
                appendLine("    $field = ${value.term}")
            }
            appendLine("}")
            for ((_, value) in fields) {
                appendLine(value.print(map))
            }
        }
    }

    override fun collectQuery(set: MutableSet<Descriptor>): PredicateState {
        if (this in set) return emptyState()
        set += this
        return basic {
            axiom { term inequality null }
            for ((field, value) in fields) {
                val fieldTerm = term.field(field.second, field.first)
                append(value.collectQuery(set))
                require { fieldTerm.load() equality value.term }
            }
        }
    }

    override fun concretize(cm: ClassManager, visited: MutableSet<Descriptor>): T {
        if (this in visited) return this as T
        visited += this

        this.klass = klass.concreteClass(cm)
        this.type = klass
        this.term = term { generate(type) }
        for ((field, value) in fields.toMap()) {
            fields[field] = value.concretize(cm, visited)
        }

        return this as T
    }

    override fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean {
        if (this in visited) return false
        if (this == other) return true
        visited += this
        if (fields.values.any { it.contains(other, visited) }) return true
        return false
    }

    override fun reduce(visited: MutableSet<Descriptor>): T {
        if (this in visited) return this as T
        visited += this

        for ((field, value) in fields.toMap()) {
            when {
                value eq descriptor { default(field.second) } -> fields.remove(field)
                else -> fields[field] = value.reduce(visited)
            }
        }

        return this as T
    }

    override fun generateTypeInfo(visited: MutableSet<Descriptor>): PredicateState {
        if (this in visited) return emptyState()
        visited += this

        val instanceOfTerm = term { generate(KexBool()) }
        return basic {
            axiom { instanceOfTerm equality (term `is` this@FieldContainingDescriptor.type) }
            axiom { instanceOfTerm equality true }
            for ((key, field) in this@FieldContainingDescriptor.fields) {
                val typeInfo = field.generateTypeInfo(visited)
                if (typeInfo.isNotEmpty) {
                    state {
                        field.term equality this@FieldContainingDescriptor.term.field(key.second, key.first).load()
                    }
                    append(typeInfo)
                }
            }
        }
    }

    override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
        if (this == other) return true
        if (other !is FieldContainingDescriptor<*>) return false
        if (this to other in map) return true
        if (this.klass != other.klass) return false

        map += this to other
        for ((field, type) in this.fields.keys.intersect(other.fields.keys)) {
            val thisValue = this[field, type] ?: return false
            val otherValue = other[field, type] ?: return false
            if (!thisValue.structuralEquality(otherValue, map)) return false
        }
        return true
    }

    override fun countDepth(visited: Set<Descriptor>, cache: MutableMap<Descriptor, Int>): Int {
        if (this in cache) return cache[this]!!
        if (this in visited) return 0
        val newVisited = visited + this
        var maxDepth = 0
        for (value in fields.values) {
            maxDepth = maxOf(maxDepth, value.countDepth(newVisited, cache))
        }
        cache[this] = maxDepth + 1
        return maxDepth + 1
    }
}

class ObjectDescriptor(klass: KexClass) :
    FieldContainingDescriptor<ObjectDescriptor>(term { generate(klass) }, klass) {
    override fun deepCopy(copied: MutableMap<Descriptor, Descriptor>): Descriptor {
        if (this in copied) return copied[this]!!
        val copy = ObjectDescriptor(klass)
        copied[this] = copy
        for ((field, value) in fields) {
            copy[field] = value.deepCopy(copied)
        }
        return copy
    }
}

class ClassDescriptor(type: KexClass) :
    FieldContainingDescriptor<ClassDescriptor>(term { staticRef(type) }, type) {
    override fun deepCopy(copied: MutableMap<Descriptor, Descriptor>): Descriptor {
        if (this in copied) return copied[this]!!
        val copy = ClassDescriptor(type as KexClass)
        copied[this] = copy
        for ((field, value) in fields) {
            copy[field] = value.deepCopy(copied)
        }
        return copy
    }

    override fun generateTypeInfo(visited: MutableSet<Descriptor>): PredicateState {
        if (this in visited) return emptyState()
        visited += this

        return basic {
            for ((key, field) in this@ClassDescriptor.fields) {
                val typeInfo = field.generateTypeInfo(visited)
                if (typeInfo.isNotEmpty) {
                    state {
                        field.term equality this@ClassDescriptor.term.field(key.second, key.first).load()
                    }
                    append(typeInfo)
                }
            }
        }
    }

    override fun concretize(cm: ClassManager, visited: MutableSet<Descriptor>): ClassDescriptor {
        if (this in visited) return this
        visited += this

        for ((field, value) in fields.toMap()) {
            fields[field] = value.concretize(cm, visited)
        }

        return this
    }

    fun filterFinalFields(cm: ClassManager): ClassDescriptor {
        val kfgClass = klass.kfgClass(cm.type)
        for ((name, type) in fields.keys.toSet()) {
            val kfgField = kfgClass.getField(name, type.getKfgType(cm.type))
            if (kfgField.isFinal) remove(name, type)
        }
        return this
    }
}

class ArrayDescriptor(val elementType: KexType, val length: Int) :
    Descriptor(term { generate(KexArray(elementType)) }, KexArray(elementType)) {
    val elements = mutableMapOf<Int, Descriptor>()

    operator fun set(index: Int, value: Descriptor) {
        elements[index] = value
    }

    operator fun get(index: Int) = elements[index]

    override fun print(map: MutableMap<Descriptor, String>): String {
        if (this in map) return ""//map[this]!!
        map[this] = term.name
        return buildString {
            appendLine("$term = $elementType[${this@ArrayDescriptor.length}] {")
            for ((index, value) in elements) {
                appendLine("    $index = ${value.term}")
            }
            appendLine("}")
            for ((_, value) in elements) {
                appendLine(value.print(map))
            }
        }
    }

    override fun collectQuery(set: MutableSet<Descriptor>): PredicateState {
        if (this in set) return emptyState()
        set += this
        return basic {
            axiom { term inequality null }
            elements.forEach { (index, element) ->
                append(element.collectQuery(set))
                require { term[index].load() equality element.term }
            }
            require { term.length() equality const(length) }
        }
    }


    override fun concretize(cm: ClassManager, visited: MutableSet<Descriptor>): ArrayDescriptor {
        if (this in visited) return this
        visited += this
        return this
    }

    override fun deepCopy(copied: MutableMap<Descriptor, Descriptor>): Descriptor {
        if (this in copied) return copied[this]!!
        val copy = ArrayDescriptor(elementType, length)
        copied[this] = copy
        for ((index, value) in elements) {
            copy[index] = value.deepCopy(copied)
        }
        return copy
    }

    override fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean {
        if (this in visited) return false
        if (this == other) return true
        visited += this
        if (elements.values.any { it.contains(other, visited) }) return true
        return false
    }

    override fun reduce(visited: MutableSet<Descriptor>): Descriptor {
        if (this in visited) return this
        visited += this

        for ((index, value) in elements.toMap()) {
            when {
                value eq descriptor { default(elementType) } -> elements.remove(index)
                else -> elements[index] = value.reduce(visited)
            }
        }

        return this
    }

    override fun generateTypeInfo(visited: MutableSet<Descriptor>): PredicateState {
        if (this in visited) return emptyState()
        visited += this

        val instanceOfTerm = term { generate(KexBool()) }
        return basic {
            axiom { instanceOfTerm equality (term `is` this@ArrayDescriptor.type) }
            axiom { instanceOfTerm equality true }
            for ((index, element) in this@ArrayDescriptor.elements) {
                val typeInfo = element.generateTypeInfo(visited)
                if (typeInfo.isNotEmpty) {
                    state { element.term equality this@ArrayDescriptor.term[index].load() }
                    append(typeInfo)
                }
            }
        }
    }

    override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
        if (this == other) return true
        if (other !is ArrayDescriptor) return false
        if (this to other in map) return true
        if (this.elementType != other.elementType) return false
        if (this.length != other.length) return false

        map += this to other
        for (index in this.elements.keys.intersect(other.elements.keys)) {
            val thisValue = this[index] ?: return false
            val otherValue = other[index] ?: return false
            val res = thisValue.structuralEquality(otherValue, map)
            if (!res) return false
        }
        return true
    }

    override fun countDepth(visited: Set<Descriptor>, cache: MutableMap<Descriptor, Int>): Int {
        if (this in cache) return cache[this]!!
        if (this in visited) return 0
        val newVisited = visited + this
        var maxDepth = 0
        for (value in elements.values) {
            maxDepth = maxOf(maxDepth, value.countDepth(newVisited, cache))
        }
        cache[this] = maxDepth + 1
        return maxDepth + 1
    }
}

open class DescriptorBuilder {
    val `null` = ConstantDescriptor.Null
    fun const(@Suppress("UNUSED_PARAMETER") nothing: Nothing?) = `null`
    fun const(value: Boolean) = ConstantDescriptor.Bool(value)
    fun const(number: Number) = when (number) {
        is Byte -> ConstantDescriptor.Byte(number)
        is Short -> ConstantDescriptor.Short(number)
        is Int -> ConstantDescriptor.Int(number)
        is Long -> ConstantDescriptor.Long(number)
        is Float -> ConstantDescriptor.Float(number)
        is Double -> ConstantDescriptor.Double(number)
        else -> unreachable { log.error("Unknown number $number") }
    }

    fun const(type: KexType, value: String): Descriptor = descriptor {
        when (type) {
            is KexNull -> const(null)
            is KexBool -> const(value.toBoolean())
            is KexByte -> const(value.toByte())
            is KexChar -> const(value[0])
            is KexShort -> const(value.toShort())
            is KexInt -> const(value.toInt())
            is KexLong -> const(value.toLong())
            is KexFloat -> const(value.toFloat())
            is KexDouble -> const(value.toDouble())
            else -> unreachable { log.error("Could not generate default descriptor value for unknown type $type") }
        }
    }

    fun const(char: Char) = ConstantDescriptor.Char(char)
    fun const(klass: KexClass) = ClassDescriptor(klass)

    fun `object`(type: KexClass): ObjectDescriptor = ObjectDescriptor(type)
    fun array(length: Int, elementType: KexType): ArrayDescriptor = ArrayDescriptor(elementType, length)

    fun default(type: KexType, nullable: Boolean): Descriptor = descriptor {
        when (type) {
            is KexBool -> const(false)
            is KexByte -> const(0.toByte())
            is KexChar -> const(0.toChar())
            is KexShort -> const(0.toShort())
            is KexInt -> const(0)
            is KexLong -> const(0L)
            is KexFloat -> const(0.0F)
            is KexDouble -> const(0.0)
            is KexClass -> if (nullable) `null` else `object`(type)
            is KexArray -> if (nullable) `null` else array(0, type.element)
            is KexReference -> default(type.reference, nullable)
            else -> unreachable { log.error("Could not generate default descriptor value for unknown type $type") }
        }
    }

    fun default(type: KexType): Descriptor = default(type, true)
}

fun descriptor(body: DescriptorBuilder.() -> Descriptor): Descriptor =
    DescriptorBuilder().body()

val descriptorContext get() = DescriptorBuilder()