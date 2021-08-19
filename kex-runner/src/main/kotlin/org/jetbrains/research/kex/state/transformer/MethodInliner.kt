package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.NullTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.collection.dequeOf

private val defaultDepth = kexConfig.getIntValue("inliner", "depth", 5)

interface Inliner<T> : RecollectingTransformer<Inliner<T>> {
    val im: MethodManager.InlineManager
    val psa: PredicateStateAnalysis
    val inlineSuffix: String
    var inlineIndex: Int
    var hasInlined: Boolean

    fun getInlinedMethod(callTerm: CallTerm): Method? = callTerm.method

    fun isInlinable(method: Method): Boolean = im.isInlinable(method)

    override fun apply(ps: PredicateState): PredicateState {
        hasInlined = false
        return super.apply(ps)
    }

    fun buildMappings(callTerm: CallTerm, method: Method, returnTerm: Term?): Pair<List<Predicate>, Map<Term, Term>> {
        val casts = mutableListOf<Predicate>()
        val mappings = hashMapOf<Term, Term>()
        if (!callTerm.isStatic) {
            val `this` = term { `this`(method.klass.kexType) }
            mappings[`this`] = callTerm.owner
        }
        if (returnTerm != null) {
            val retval = term { `return`(method) }
            mappings[retval] = returnTerm
        }

        for ((index, argType) in method.argTypes.withIndex()) {
            val argTerm = term { arg(argType.kexType, index) }
            when (val calledArg = callTerm.arguments[index]) {
                is NullTerm -> {
                    val casted = state {
                        val newArg = generate(argTerm.type)
                        mappings[argTerm] = newArg
                        newArg equality calledArg
                    }
                    casts += casted
                }
                else -> {
                    mappings[argTerm] = calledArg
                }
            }
        }

        return casts to mappings
    }

    fun prepareInlinedState(method: Method, mappings: Map<Term, Term>): PredicateState? {
        if (method.isEmpty()) return null

        val builder = psa.builder(method)
        val endState = builder.methodState ?: return null

        return TermRenamer("inlined${inlineIndex++}", mappings).apply(endState)
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val calledMethod = call.method
        if (!isInlinable(calledMethod)) return predicate

        val inlinedMethod = getInlinedMethod(call) ?: return predicate
        val (casts, mappings) = buildMappings(call, inlinedMethod, predicate.lhvUnsafe)

        val inlinedState = prepareInlinedState(inlinedMethod, mappings) ?: return predicate
        casts.onEach { currentBuilder += it }
        currentBuilder += inlinedState
        hasInlined = true
        return nothing()
    }
}

class MethodInliner(
    override val psa: PredicateStateAnalysis,
    override val inlineSuffix: String = "inlined",
    override var inlineIndex: Int = 0
) : Inliner<MethodInliner> {
    override val im = MethodManager.InlineManager
    override val builders = dequeOf(StateBuilder())
    override var hasInlined: Boolean = false
}

class ConstructorInliner(
    override val psa: PredicateStateAnalysis,
    override val inlineSuffix: String = "ctor.inlined",
    override var inlineIndex: Int = 0
) : Inliner<ConstructorInliner> {
    override val im = MethodManager.InlineManager
    override val builders = dequeOf(StateBuilder())
    override var hasInlined: Boolean = false

    override fun isInlinable(method: Method): Boolean = super.isInlinable(method) && method.isConstructor
}

class RecursiveConstructorInliner(
    override val psa: PredicateStateAnalysis,
    override val inlineSuffix: String = "recursive.ctor.inlined"
) : Inliner<RecursiveConstructorInliner> {
    override val im = MethodManager.InlineManager
    override var inlineIndex = 0
    override val builders = dequeOf(StateBuilder())
    override var hasInlined: Boolean = false

    override fun apply(ps: PredicateState): PredicateState {
        hasInlined = false
        var current = ps
        var depth = 0
        do {
            val cii = ConstructorInliner(psa, inlineSuffix, inlineIndex)
            current = cii.apply(current)
            hasInlined = cii.hasInlined
            inlineIndex = cii.inlineIndex
            ++depth
        } while (hasInlined)
        hasInlined = depth > 1
        return current
    }
}


class RecursiveInliner<T>(
    override val psa: PredicateStateAnalysis,
    override val inlineSuffix: String = "recursive",
    val maxDepth: Int = defaultDepth,
    val inlinerBuilder: (Int) -> Inliner<T>
) : Inliner<RecursiveInliner<T>> {
    override val im = MethodManager.InlineManager
    override var inlineIndex = 0
    override val builders = dequeOf(StateBuilder())
    override var hasInlined: Boolean = false

    override fun apply(ps: PredicateState): PredicateState {
        hasInlined = false
        var current = ps
        var depth = 0
        do {
            val cii = inlinerBuilder(inlineIndex)
            current = cii.apply(current)
            hasInlined = cii.hasInlined
            inlineIndex = cii.inlineIndex
            ++depth
        } while (hasInlined && depth < maxDepth)
        hasInlined = depth > 1
        return current
    }
}
