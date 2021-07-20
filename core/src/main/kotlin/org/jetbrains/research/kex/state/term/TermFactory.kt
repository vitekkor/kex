package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.ir.value.instruction.UnaryOpcode
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

object TermFactory {
    fun getThis(type: KexType) = getValue(type, "this")
    fun getArgument(argument: Argument) = getArgument(argument.type.kexType, argument.index)
    fun getArgument(type: KexType, index: Int) = ArgumentTerm(type, index)

    fun getConstant(const: Constant) = when (const) {
        is BoolConstant -> getBool(const)
        is ByteConstant -> getByte(const)
        is ShortConstant -> getShort(const)
        is CharConstant -> getChar(const)
        is IntConstant -> getInt(const)
        is LongConstant -> getLong(const)
        is FloatConstant -> getFloat(const)
        is DoubleConstant -> getDouble(const)
        is StringConstant -> getString(const)
        is NullConstant -> getNull()
        is ClassConstant -> getClass(const)
        else -> unreachable { log.error("Unknown constant type: $const of type ${const::class}") }
    }

    fun <T : Number> getConstant(number: T) = when (number) {
        is Long -> getLong(number)
        is Int -> getInt(number)
        is Short -> getShort(number)
        is Byte -> getByte(number)
        is Double -> getDouble(number)
        is Float -> getFloat(number)
        else -> unreachable("Unknown numeric type")
    }

    fun getTrue() = getBool(true)
    fun getFalse() = getBool(false)
    fun getBool(value: Boolean) = ConstBoolTerm(value)
    fun getBool(const: BoolConstant) = getBool(const.value)
    fun getByte(value: Byte) = ConstByteTerm(value)
    fun getByte(const: ByteConstant) = getByte(const.value)
    fun getShort(value: Short) = ConstShortTerm(value)
    fun getShort(const: ShortConstant) = getShort(const.value)
    fun getChar(value: Char) = ConstCharTerm(value)
    fun getChar(const: CharConstant) = getChar(const.value)
    fun getInt(value: Int) = ConstIntTerm(value)
    fun getInt(const: IntConstant) = getInt(const.value)
    fun getLong(value: Long) = ConstLongTerm(value)
    fun getLong(const: LongConstant) = getLong(const.value)
    fun getFloat(value: Float) = ConstFloatTerm(value)
    fun getFloat(const: FloatConstant) = getFloat(const.value)
    fun getDouble(value: Double) = ConstDoubleTerm(value)
    fun getDouble(const: DoubleConstant) = getDouble(const.value)
    fun getString(type: KexType, value: String) = ConstStringTerm(type, value)
    fun getString(value: String) = ConstStringTerm(KexClass("java/lang/String"), value)
    fun getString(const: StringConstant) = getString(const.value)
    fun getNull() = NullTerm()
    fun getClass(`class`: Class) = getClass(KexClass(`class`.fullName))
    fun getClass(klass: KexType) = ConstClassTerm(klass)
    fun getClass(const: ClassConstant) = ConstClassTerm(const.type.kexType)

    fun getUnaryTerm(operand: Term, opcode: UnaryOpcode) = when (opcode) {
        UnaryOpcode.NEG -> getNegTerm(operand)
        UnaryOpcode.LENGTH -> getArrayLength(operand)
    }

    fun getArrayLength(arrayRef: Term) = getArrayLength(KexInt(), arrayRef)
    fun getArrayLength(type: KexType, arrayRef: Term) = ArrayLengthTerm(type, arrayRef)

    fun getArrayIndex(arrayRef: Term, index: Term): Term {
        val arrayType = arrayRef.type as? KexArray
            ?: unreachable { log.debug("Non-array type of array load term operand") }
        return getArrayIndex(KexReference(arrayType.element), arrayRef, index)
    }

    fun getArrayIndex(type: KexType, arrayRef: Term, index: Term) = ArrayIndexTerm(type, arrayRef, index)

    fun getNegTerm(operand: Term) = getNegTerm(operand.type, operand)
    fun getNegTerm(type: KexType, operand: Term) = NegTerm(type, operand)

    fun getArrayLoad(arrayRef: Term): Term {
        val arrayType = arrayRef.type as? KexReference
            ?: unreachable { log.debug("Non-array type of array load term operand") }
        return getArrayLoad(arrayType.reference, arrayRef)
    }

    fun getArrayLoad(type: KexType, arrayRef: Term) = ArrayLoadTerm(type, arrayRef)

    fun getFieldLoad(type: KexType, field: Term) = FieldLoadTerm(type, field)

    fun getBinary(tf: TypeFactory, opcode: BinaryOpcode, lhv: Term, rhv: Term): Term {
        val merged = mergeTypes(tf, setOf(lhv.type, rhv.type))
        return getBinary(merged, opcode, lhv, rhv)
    }

    fun getBinary(type: KexType, opcode: BinaryOpcode, lhv: Term, rhv: Term) = BinaryTerm(type, opcode, lhv, rhv)

    fun getBound(ptr: Term) = getBound(KexInt(), ptr)
    fun getBound(type: KexType, ptr: Term) = BoundTerm(type, ptr)

    fun getCall(method: Method, arguments: List<Term>) = getCall(method.returnType.kexType, method, arguments)
    fun getCall(method: Method, objectRef: Term, arguments: List<Term>) =
        getCall(method.returnType.kexType, objectRef, method, arguments)

    fun getCall(type: KexType, method: Method, arguments: List<Term>) =
        CallTerm(type, getClass(method.klass), method, arguments)

    fun getCall(type: KexType, objectRef: Term, method: Method, arguments: List<Term>) =
        CallTerm(type, objectRef, method, arguments)

    fun getCast(type: KexType, operand: Term) = CastTerm(type, operand)
    fun getCmp(opcode: CmpOpcode, lhv: Term, rhv: Term): Term {
        val resType = when (opcode) {
            CmpOpcode.CMPG -> KexInt()
            CmpOpcode.CMPL -> KexInt()
            else -> KexBool()
        }
        return getCmp(resType, opcode, lhv, rhv)
    }

    fun getContains(arrayRef: Term, value: Term): Term = ContainsTerm(arrayRef, value)

    fun getEquals(lhv: Term, rhv: Term): Term {
        ktassert(lhv.type is KexPointer) { log.error("Non-pointer type in equals") }
        ktassert(rhv.type is KexPointer) { log.error("Non-pointer type in equals") }
        return EqualsTerm(lhv, rhv)
    }

    fun getCmp(type: KexType, opcode: CmpOpcode, lhv: Term, rhv: Term) = CmpTerm(type, opcode, lhv, rhv)

    fun getField(type: KexType, owner: Term, name: String) = FieldTerm(type, owner, name)
    fun getField(type: KexType, classType: Class, name: String) = FieldTerm(type, getClass(classType), name)

    fun getInstanceOf(checkedType: KexType, operand: Term) = InstanceOfTerm(checkedType, operand)

    fun getReturn(method: Method) = getReturn(method.returnType.kexType, method)
    fun getReturn(type: KexType, method: Method) = ReturnValueTerm(type, method)

    fun getValue(value: Value) = when (value) {
        is Argument -> getArgument(value)
        is Constant -> getConstant(value)
        is ThisRef -> getThis(value.type.kexType)
        else -> getValue(value.type.kexType, value.toString())
    }

    fun getValue(type: KexType, name: String) = ValueTerm(type, name)

    fun getUndef(type: KexType) = UndefTerm(type)

    fun getLambda(type: KexType,  params: List<Term>, body: PredicateState) = LambdaTerm(type, params, body)
}

abstract class TermBuilder {
    val tf = TermFactory

    private object TermGenerator {
        private var index = 0

        val nextName: String get() = "term${index++}"

        fun nextTerm(type: KexType) = term { value(type, nextName) }
    }

    fun generate(type: KexType) = TermGenerator.nextTerm(type)

    fun `this`(type: KexType) = tf.getThis(type)

    fun arg(argument: Argument) = tf.getArgument(argument)
    fun arg(type: KexType, index: Int) = tf.getArgument(type, index)

    fun const(constant: Constant) = tf.getConstant(constant)
    fun const(bool: Boolean) = tf.getBool(bool)
    fun const(str: String) = tf.getString(str)
    fun const(char: Char) = tf.getChar(char)
    fun <T : Number> const(number: T) = tf.getConstant(number)
    fun const(@Suppress("UNUSED_PARAMETER") nothing: Nothing?) = tf.getNull()
    fun `class`(klass: Class) = tf.getClass(klass)
    fun `class`(klass: KexType) = tf.getClass(klass)

    fun Term.apply(opcode: UnaryOpcode) = tf.getUnaryTerm(this, opcode)
    operator fun Term.not() = tf.getUnaryTerm(this, UnaryOpcode.NEG)
    fun Term.length() = tf.getUnaryTerm(this, UnaryOpcode.LENGTH)

    operator fun Term.get(index: Term) = tf.getArrayIndex(this, index)
    operator fun Term.get(index: Int) = tf.getArrayIndex(this, const(index))

    fun Term.load() = when (this) {
        is ArrayIndexTerm -> tf.getArrayLoad(this)
        is FieldTerm -> {
            val type = (this.type as KexReference).reference
            tf.getFieldLoad(type, this)
        }
        else -> unreachable { log.error("Unknown term type in load: $this") }
    }

    infix fun Term.add(rhv: Term) = tf.getBinary(type, BinaryOpcode.ADD, this, rhv)
    operator fun Term.plus(rhv: Term) = this add rhv

    infix fun Term.sub(rhv: Term) = tf.getBinary(type, BinaryOpcode.SUB, this, rhv)
    operator fun Term.minus(rhv: Term) = this sub rhv

    infix fun Term.mul(rhv: Term) = tf.getBinary(type, BinaryOpcode.MUL, this, rhv)
    operator fun Term.times(rhv: Term) = this mul rhv

    operator fun Term.div(rhv: Term) = tf.getBinary(type, BinaryOpcode.DIV, this, rhv)
    operator fun Term.rem(rhv: Term) = tf.getBinary(type, BinaryOpcode.REM, this, rhv)

    infix fun Term.shl(shift: Term) = tf.getBinary(type, BinaryOpcode.SHL, this, shift)
    infix fun Term.shr(shift: Term) = tf.getBinary(type, BinaryOpcode.SHR, this, shift)
    infix fun Term.ushr(shift: Term) = tf.getBinary(type, BinaryOpcode.USHR, this, shift)

    infix fun Term.and(rhv: Term) = tf.getBinary(type, BinaryOpcode.AND, this, rhv)
    infix fun Term.and(bool: Boolean) = tf.getBinary(type, BinaryOpcode.AND, this, const(bool))
    infix fun Term.or(rhv: Term) = tf.getBinary(type, BinaryOpcode.OR, this, rhv)
    infix fun Term.or(bool: Boolean) = tf.getBinary(type, BinaryOpcode.OR, this, const(bool))
    infix fun Term.xor(rhv: Term) = tf.getBinary(type, BinaryOpcode.XOR, this, rhv)
    infix fun Term.xor(bool: Boolean) = tf.getBinary(type, BinaryOpcode.XOR, this, const(bool))

    infix fun Term.implies(rhv: Term) = !this or rhv
    infix fun Term.implies(rhv: Boolean) = !this or rhv

    fun Term.apply(types: TypeFactory, opcode: BinaryOpcode, rhv: Term) = tf.getBinary(types, opcode, this, rhv)
    fun Term.apply(type: KexType, opcode: BinaryOpcode, rhv: Term) = tf.getBinary(type, opcode, this, rhv)
    fun Term.apply(opcode: CmpOpcode, rhv: Term) = tf.getCmp(opcode, this, rhv)

    infix fun Term.eq(rhv: Term) = tf.getCmp(CmpOpcode.EQ, this, rhv)
    infix fun <T : Number> Term.eq(rhv: T) = tf.getCmp(CmpOpcode.EQ, this, const(rhv))
    infix fun Term.eq(rhv: Boolean) = tf.getCmp(CmpOpcode.EQ, this, const(rhv))
    infix fun Term.eq(rhv: Nothing?) = tf.getCmp(CmpOpcode.EQ, this, const(rhv))

    infix fun Term.neq(rhv: Term) = tf.getCmp(CmpOpcode.NEQ, this, rhv)
    infix fun <T : Number> Term.neq(rhv: T) = tf.getCmp(CmpOpcode.NEQ, this, const(rhv))
    infix fun Term.neq(rhv: Boolean) = tf.getCmp(CmpOpcode.NEQ, this, const(rhv))
    infix fun Term.neq(rhv: Nothing?) = tf.getCmp(CmpOpcode.NEQ, this, const(rhv))

    infix fun Term.lt(rhv: Term) = tf.getCmp(CmpOpcode.LT, this, rhv)
    infix fun <T : Number> Term.lt(rhv: T) = tf.getCmp(CmpOpcode.LT, this, const(rhv))
    infix fun Term.lt(rhv: Boolean) = tf.getCmp(CmpOpcode.LT, this, const(rhv))
    infix fun Term.lt(rhv: Nothing?) = tf.getCmp(CmpOpcode.LT, this, const(rhv))

    infix fun Term.gt(rhv: Term) = tf.getCmp(CmpOpcode.GT, this, rhv)
    infix fun <T : Number> Term.gt(rhv: T) = tf.getCmp(CmpOpcode.GT, this, const(rhv))
    infix fun Term.gt(rhv: Boolean) = tf.getCmp(CmpOpcode.GT, this, const(rhv))
    infix fun Term.gt(rhv: Nothing?) = tf.getCmp(CmpOpcode.GT, this, const(rhv))

    infix fun Term.le(rhv: Term) = tf.getCmp(CmpOpcode.LE, this, rhv)
    infix fun <T : Number> Term.le(rhv: T) = tf.getCmp(CmpOpcode.LE, this, const(rhv))
    infix fun Term.le(rhv: Boolean) = tf.getCmp(CmpOpcode.LE, this, const(rhv))
    infix fun Term.le(rhv: Nothing?) = tf.getCmp(CmpOpcode.LE, this, const(rhv))

    infix fun Term.ge(rhv: Term) = tf.getCmp(CmpOpcode.GE, this, rhv)
    infix fun <T : Number> Term.ge(rhv: T) = tf.getCmp(CmpOpcode.GE, this, const(rhv))
    infix fun Term.ge(rhv: Boolean) = tf.getCmp(CmpOpcode.GE, this, const(rhv))
    infix fun Term.ge(rhv: Nothing?) = tf.getCmp(CmpOpcode.GE, this, const(rhv))

    infix fun Term.cmp(rhv: Term) = tf.getCmp(CmpOpcode.CMP, this, rhv)
    infix fun Term.cmpg(rhv: Term) = tf.getCmp(CmpOpcode.CMPG, this, rhv)
    infix fun Term.cmpl(rhv: Term) = tf.getCmp(CmpOpcode.CMPL, this, rhv)

    infix fun Term.`in`(arrayRef: Term) = tf.getContains(arrayRef, this)

    infix fun Term.equls(rhv: Term) = tf.getEquals(this, rhv)

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(message = "not used in current SMT model")
    fun Term.bound() = tf.getBound(this)

    fun Term.call(method: Method, arguments: List<Term>) = tf.getCall(method, this, arguments)

    fun Term.field(type: KexReference, name: String) = tf.getField(type, this, name)
    fun Term.field(type: KexType, name: String) = tf.getField(KexReference(type), this, name)

    infix fun Term.`as`(type: KexType) = tf.getCast(type, this)
    infix fun Term.`is`(type: KexType) = tf.getInstanceOf(type, this)

    fun `return`(method: Method) = tf.getReturn(method)

    fun value(value: Value) = tf.getValue(value)
    fun value(type: KexType, name: String) = tf.getValue(type, name)
    fun undef(type: KexType) = tf.getUndef(type)

    fun lambda(type: KexType, params: List<Term>, bodyBuilder: () -> PredicateState) =
        lambda(type, params, bodyBuilder())

    fun lambda(type: KexType, params: List<Term>, body: PredicateState) =
        tf.getLambda(type, params, body)

    fun lambda(type: KexType, vararg params: Term, bodyBuilder: () -> PredicateState) =
        lambda(type, *params, body = bodyBuilder())


    fun lambda(type: KexType, vararg params: Term, body: PredicateState) =
        tf.getLambda(type, params.toList(), body)

    object Terms : TermBuilder()
}

inline fun term(body: TermBuilder.() -> Term) = TermBuilder.Terms.body()