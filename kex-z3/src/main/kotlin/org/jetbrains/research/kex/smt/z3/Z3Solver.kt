package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.*
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.smt.*
import org.jetbrains.research.kex.smt.Solver
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.transformer.collectPointers
import org.jetbrains.research.kex.state.transformer.collectVariables
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.debug
import org.jetbrains.research.kthelper.logging.log
import kotlin.math.max

private val timeout = kexConfig.getIntValue("smt", "timeout", 3) * 1000
private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
private val logFormulae = kexConfig.getBooleanValue("smt", "logFormulae", false)
private val printSMTLib = kexConfig.getBooleanValue("smt", "logSMTLib", false)
private val simplifyFormulae = kexConfig.getBooleanValue("smt", "simplifyFormulae", false)

@Solver("z3")
class Z3Solver(val tf: TypeFactory) : AbstractSMTSolver {
    val ef = Z3ExprFactory()

    override fun isReachable(state: PredicateState) =
        isPathPossible(state, state.path)

    override fun isPathPossible(state: PredicateState, path: PredicateState): Result = check(state, path) { it }


    override fun isViolated(state: PredicateState, query: PredicateState): Result = check(state, query) { !it }

    fun check(state: PredicateState, query: PredicateState, queryBuilder: (Bool_) -> Bool_): Result {
        if (logQuery) {
            log.run {
                debug("Z3 solver check")
                debug("State: $state")
                debug("Query: $query")
            }
        }

        val ctx = Z3Context(ef)

        val converter = Z3Converter(tf)
        converter.init(state)
        val z3State = converter.convert(state, ef, ctx)
        val z3query = converter.convert(query, ef, ctx)

        log.debug("Check started")
        val result = check(z3State, queryBuilder(z3query))
        log.debug("Check finished")
        return when (result.first) {
            Status.UNSATISFIABLE -> Result.UnsatResult
            Status.UNKNOWN -> Result.UnknownResult(result.second as String)
            Status.SATISFIABLE -> Result.SatResult(collectModel(ctx, result.second as Model, state))
        }
    }

    private fun check(state: Bool_, query: Bool_): Pair<Status, Any> {
        val solver = buildTactics().solver ?: unreachable { log.error("Can't create solver") }

        val (state_, query_) = when {
            simplifyFormulae -> state.simplify() to query.simplify()
            else -> state to query
        }

        if (logFormulae) {
            log.run {
                debug("State: $state_")
                debug("Query: $query_")
            }
        }

        solver.add(state_.asAxiom() as BoolExpr)
        solver.add(ef.buildSubtypeAxioms(tf).asAxiom() as BoolExpr)
        solver.add(ef.buildConstClassAxioms().asAxiom() as BoolExpr)
        solver.add(query_.axiom as BoolExpr)
        solver.add(query_.expr as BoolExpr)

        log.debug("Running z3 solver")
        if (printSMTLib) {
            log.debug("SMTLib formula:")
            log.debug(solver)
        }
        val result = solver.check(query.expr as BoolExpr) ?: unreachable { log.error("Solver error") }
        log.debug("Solver finished")

        return when (result) {
            Status.SATISFIABLE -> {
                val model = solver.model ?: unreachable { log.error("Solver result does not contain model") }
                if (logFormulae) log.debug(model)
                result to model
            }
            Status.UNSATISFIABLE -> {
                val core = solver.unsatCore.toList()
                log.debug("Unsat core: $core")
                result to core
            }
            Status.UNKNOWN -> {
                val reason = solver.reasonUnknown
                log.debug(reason)
                result to reason
            }
        }
    }

    private fun buildTactics(): Tactic {
        Z3Params.load().forEach { (name, value) ->
            Global.setParameter(name, value.toString())
        }

        val ctx = ef.ctx
        val tactic = Z3Tactics.load().map {
            val tactic = ctx.mkTactic(it.type)
            val params = ctx.mkParams()
            it.params.forEach { (name, value) ->
                when (value) {
                    is Value.BoolValue -> params.add(name, value.value)
                    is Value.IntValue -> params.add(name, value.value)
                    is Value.DoubleValue -> params.add(name, value.value)
                    is Value.StringValue -> params.add(name, value.value)
                }
            }
            ctx.with(tactic, params)
        }.reduce { a, b -> ctx.andThen(a, b) }
        return ctx.tryFor(tactic, timeout)
    }

    private fun Z3Context.recoverProperty(
        ptr: Term,
        memspace: Int,
        type: KexType,
        model: Model,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = Z3Converter(tf).convert(ptr, ef, this) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val typeSize = Z3ExprFactory.getTypeSize(type)
        val startProp = when (typeSize) {
            TypeSize.WORD -> getWordInitialProperty(memspace, name)
            TypeSize.DWORD -> getDWordInitialProperty(memspace, name)
        }
        val endProp = when (typeSize) {
            TypeSize.WORD -> getWordProperty(memspace, name)
            TypeSize.DWORD -> getDWordProperty(memspace, name)
        }

        val startV = startProp.load(ptrExpr)
        val endV = endProp.load(ptrExpr)

        val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
        val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))
        return modelStartV to modelEndV
    }

    private fun MutableMap<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>.recoverProperty(
        ctx: Z3Context,
        ptr: Term,
        memspace: Int,
        type: KexType,
        model: Model,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = Z3Converter(tf).convert(ptr, ef, ctx) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val modelPtr = Z3Unlogic.undo(model.evaluate(ptrExpr.expr, true))

        val (modelStartT, modelEndT) = ctx.recoverProperty(ptr, memspace, type, model, name)
        val typePair = this.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
            hashMapOf<Term, Term>() to hashMapOf()
        }
        typePair.first[modelPtr] = modelStartT
        typePair.second[modelPtr] = modelEndT
        return modelStartT to modelEndT
    }

    private fun collectModel(ctx: Z3Context, model: Model, vararg states: PredicateState): SMTModel {
        val (ptrs, vars) = states.fold(setOf<Term>() to setOf<Term>()) { acc, ps ->
            acc.first + collectPointers(ps) to acc.second + collectVariables(ps)
        }

        val assignments = vars.associateWith {
            val expr = Z3Converter(tf).convert(it, ef, ctx)
            val z3expr = expr.expr

            val evaluatedExpr = model.evaluate(z3expr, true)
            Z3Unlogic.undo(evaluatedExpr)
        }.toMutableMap()

        val memories = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
        val properties = hashMapOf<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
        val arrays = hashMapOf<Int, MutableMap<Term, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
        val strings = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
        val typeMap = hashMapOf<Term, KexType>()

        for ((type, value) in ef.typeMap) {
            val actualValue = Z3Unlogic.undo(model.evaluate(value.expr, true))
            typeMap[actualValue] = type
        }

        val indices = hashSetOf<Term>()
        for (ptr in ptrs) {
            val memspace = ptr.memspace

            when (ptr) {
                is ArrayLoadTerm -> {}
                is ArrayIndexTerm -> {
                    val arrayPtrExpr = Z3Converter(tf).convert(ptr.arrayRef, ef, ctx) as? Ptr_
                        ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
                    val indexExpr = Z3Converter(tf).convert(ptr.index, ef, ctx) as? Int_
                        ?: unreachable { log.error("Non integer expr for index in $ptr") }

                    val modelPtr = Z3Unlogic.undo(model.evaluate(arrayPtrExpr.expr, true))
                    val modelIndex = Z3Unlogic.undo(model.evaluate(indexExpr.expr, true))

                    val modelStartArray = ctx.readArrayInitialMemory(arrayPtrExpr, ptr.arrayRef.memspace)
                    val modelArray = ctx.readArrayMemory(arrayPtrExpr, ptr.arrayRef.memspace)

                    val cast = { arrayVal: DWord_ ->
                        when (Z3ExprFactory.getTypeSize((ptr.arrayRef.type as KexArray).element)) {
                            TypeSize.WORD -> Word_.forceCast(arrayVal)
                            TypeSize.DWORD -> arrayVal
                        }
                    }
                    val initialValue = Z3Unlogic.undo(
                        model.evaluate(
                            cast(
                                DWord_.forceCast(modelStartArray.load(indexExpr))
                            ).expr,
                            true
                        )
                    )
                    val value = Z3Unlogic.undo(
                        model.evaluate(
                            cast(
                                DWord_.forceCast(modelArray.load(indexExpr))
                            ).expr,
                            true
                        )
                    )

                    val arrayPair = arrays.getOrPut(ptr.arrayRef.memspace, ::hashMapOf).getOrPut(modelPtr) {
                        hashMapOf<Term, Term>() to hashMapOf()
                    }
                    arrayPair.first[modelIndex] = initialValue
                    arrayPair.second[modelIndex] = value
                }
                is FieldLoadTerm -> {}
                is FieldTerm -> {
                    val name = "${ptr.klass}.${ptr.fieldName}"
                    properties.recoverProperty(
                        ctx,
                        ptr.owner,
                        memspace,
                        (ptr.type as KexReference).reference,
                        model,
                        name
                    )
                    properties.recoverProperty(ctx, ptr.owner, memspace, ptr.type, model, "type")
                }
                else -> {
                    val startMem = ctx.getWordInitialMemory(memspace)
                    val endMem = ctx.getWordMemory(memspace)

                    val ptrExpr = Z3Converter(tf).convert(ptr, ef, ctx) as? Ptr_
                        ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }

                    val startV = startMem.load(ptrExpr)
                    val endV = endMem.load(ptrExpr)

                    val modelPtr = Z3Unlogic.undo(model.evaluate(ptrExpr.expr, true))
                    val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
                    val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))

                    memories.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
                    memories.getValue(memspace).first[modelPtr] = modelStartV
                    memories.getValue(memspace).second[modelPtr] = modelEndV
                    if (ptr.type.isString) {
//                        val modelStartStr = ctx.readInitialStringMemory(ptrExpr, memspace)
//                        val modelStr = ctx.readStringMemory(ptrExpr, memspace)
//                        val startStringVal = Z3Unlogic.undo(model.evaluate(modelStartStr.expr, true))
//                        val stringVal = Z3Unlogic.undo(model.evaluate(modelStr.expr, true))
//                        strings.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
//                        strings.getValue(memspace).first[modelPtr] = startStringVal
//                        strings.getValue(memspace).second[modelPtr] = stringVal
                    } else if (ptr.type.isArray) {
                        val (startLength, endLength) = properties.recoverProperty(ctx, ptr, memspace, KexInt(), model, "length")
                        val maxLen = max(startLength.numericValue.toInt(), endLength.numericValue.toInt())
                        for (i in 0 until maxLen) {
                            val indexTerm = term { ptr[i] }
                            if (indexTerm !in ptrs)
                                indices += indexTerm
                        }
                    }

                    properties.recoverProperty(ctx, ptr, memspace, ptr.type, model, "type")

                    ktassert(assignments.getOrPut(ptr) { modelPtr } == modelPtr)
                }
            }
        }
        for (ptr in indices) {
            ptr as ArrayIndexTerm
            val memspace = ptr.arrayRef.memspace
            val arrayPtrExpr = Z3Converter(tf).convert(ptr.arrayRef, ef, ctx) as? Ptr_
                ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
            val indexExpr = Z3Converter(tf).convert(ptr.index, ef, ctx) as? Int_
                ?: unreachable { log.error("Non integer expr for index in $ptr") }

            val modelPtr = Z3Unlogic.undo(model.evaluate(arrayPtrExpr.expr, true))
            val modelIndex = Z3Unlogic.undo(model.evaluate(indexExpr.expr, true))

            val modelStartArray = ctx.readArrayInitialMemory(arrayPtrExpr, memspace)
            val modelArray = ctx.readArrayMemory(arrayPtrExpr, memspace)

            val cast = { arrayVal: DWord_ ->
                when (Z3ExprFactory.getTypeSize((ptr.arrayRef.type as KexArray).element)) {
                    TypeSize.WORD -> Word_.forceCast(arrayVal)
                    TypeSize.DWORD -> arrayVal
                }
            }
            val initialValue = Z3Unlogic.undo(
                model.evaluate(
                    cast(
                        DWord_.forceCast(modelStartArray.load(indexExpr))
                    ).expr,
                    true
                )
            )
            val value = Z3Unlogic.undo(
                model.evaluate(
                    cast(
                        DWord_.forceCast(modelArray.load(indexExpr))
                    ).expr,
                    true
                )
            )

            val arrayPair = arrays.getOrPut(memspace, ::hashMapOf).getOrPut(modelPtr) {
                hashMapOf<Term, Term>() to hashMapOf()
            }
            arrayPair.first[modelIndex] = initialValue
            arrayPair.second[modelIndex] = value
        }
        return SMTModel(
            assignments,
            memories.map { (memspace, pair) -> memspace to MemoryShape(pair.first, pair.second) }.toMap(),
            properties.map { (memspace, names) ->
                memspace to names.map { (name, pair) -> name to MemoryShape(pair.first, pair.second) }.toMap()
            }.toMap(),
            arrays.map { (memspace, values) ->
                memspace to values.map { (addr, pair) -> addr to MemoryShape(pair.first, pair.second) }.toMap()
            }.toMap(),
            strings.map { (memspace, pair) -> memspace to MemoryShape(pair.first, pair.second) }.toMap(),
            typeMap
        )
    }

    override fun close() {
        ef.ctx.close()
    }
}