package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.TransformerBase
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.Predicate
import java.util.*

@TransformerBase
interface RecollectingTransformer<T> : Transformer<RecollectingTransformer<T>> {
    val builders: Deque<StateBuilder>

    val currentBuilder: StateBuilder
        get() = builders.last

    val state: PredicateState
        get() = currentBuilder.apply()

    override fun apply(ps: PredicateState): PredicateState {
        super.transform(ps)
        return state.simplify()
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val newChoices = arrayListOf<PredicateState>()
        for (choice in ps.choices) {
            builders.add(StateBuilder())
            super.transformBase(choice)

            newChoices.add(currentBuilder.apply())
            builders.pollLast()
        }
        currentBuilder += newChoices
        return ps
    }

    override fun transformPredicate(predicate: Predicate): Predicate {
        val result = super.transformPredicate(predicate)
        if (result != nothing()) currentBuilder += result
        return result
    }
}