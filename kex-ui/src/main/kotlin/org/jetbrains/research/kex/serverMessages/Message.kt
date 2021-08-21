package org.jetbrains.research.kex.serverMessages

import KFGGraph
import kotlinx.serialization.Serializable

@Serializable
sealed class Message

@Serializable
data class Trace(val nodesId: List<String>): Message()

@Serializable
class Graph private constructor(val graph: String): Message() {
    constructor(graph: KFGGraph): this(graph.toJson())
}
