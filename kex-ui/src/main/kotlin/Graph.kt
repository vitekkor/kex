import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.SubGraph
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.impl.AbstractElement
import info.leadinglight.jdot.impl.Attrs
import info.leadinglight.jdot.impl.EdgeNode
import org.jetbrains.research.kthelper.algorithm.Viewable
import java.util.ArrayDeque

fun Viewable.toGraph(name: String): KFGGraph {
    val graph = KFGGraph(name)

    graphView.forEach {
        graph.addNode(it.label.replace(Regex(""" *\\l *"""), "\n"))
    }

    for (node in graphView) {
        for (successor in node.successors) {
            graph.addLink(
                node.label.replace(Regex(""" *\\l *"""), "\n"),
                successor.label.replace(Regex(""" *\\l *"""), "\n")
            )
        }
    }
    return graph
}

fun Graph.toSubGraph(): SubGraph {
    for (i in elements.indices) {
        var element = elements[i]
        if (element is Node) {
            element = element.copy("${this.name.split("::")[0]}::${element.name}")
            elements[i] = element
        } else {
            element as Edge
            for (j in element.getElements().indices) {
                val newEdgeNode =
                    EdgeNode("${this.name.split("::")[0]}::" + (element.getElements()[j] as EdgeNode).name)
                element.getElements()[j] = newEdgeNode
            }
        }
    }
    return KFGSubGraph(this.name).apply { elements.addAll(this@toSubGraph.elements); setBgColor(Color.X11.transparent) }
}

fun Graph.findNode(name: String): Node? {
    val elements = ArrayDeque<AbstractElement>().apply { addAll(elements) }
    while (elements.isNotEmpty()) {
        when (val element = elements.poll()) {
            // TODO: maybe simplify the condition or, on the contrary, complicate
            is Node -> if (element.attrs.asString.replace(Regex("""\\l|\s"""), "").replace("\\\"", "\"")
                    .contains(name)
            ) return element
            is SubGraph -> elements.addAll(element.elements)
        }
    }
    return null
}

fun Graph.findEdges(node: Node): List<Edge> {
    val elements = ArrayDeque<AbstractElement>().apply { addAll(elements) }
    val result = mutableListOf<Edge>()
    while (elements.isNotEmpty()) {
        when (val element = elements.poll()) {
            is Edge -> if ((element.getElements().first() as EdgeNode).name == node.name) result.add(element)
            is SubGraph -> elements.addAll(element.elements)
        }
    }
    return result
}

fun Graph.removeEdge(edge: Edge): Boolean {
    if (elements.remove(edge)) return true
    val subGraphs = ArrayDeque<SubGraph>().apply { addAll(elements.filterIsInstance<SubGraph>()) }
    while (subGraphs.isNotEmpty()) {
        val subElements = subGraphs.poll().elements
        if (subElements.remove(edge)) return true
        subGraphs.addAll(subElements.filterIsInstance<SubGraph>())
    }
    return false
}

fun Graph.expand(subGraph: Graph): Graph {
    val node = findNode(subGraph.name.split("::")[0])!!
    addSubGraph(subGraph.toSubGraph())
    val edges = findEdges(node).map { edge ->
        removeEdge(edge)
        subGraph.elements
            .filter { it is Node && it.attrs.asString.contains(Regex("""(.*throw.*)|(.*return.*)""")) }
            .map { node_ ->
                Edge((node_ as Node).name, (edge.getElements().last() as EdgeNode).name)
            }
    }.flatten()
    val targetName = subGraph.elements.find {
        it is Node && subGraph.elements.all { e -> if (e is Edge) (e.getElements()[1] as EdgeNode).name != it.name else true }
    } as Node
    addEdge(Edge(node.name, targetName.name))
    addEdges(*edges.toTypedArray())
    return this
}

@Suppress("UNCHECKED_CAST")
fun Edge.getElements(): ArrayList<AbstractElement> {
    val f = this::class.java.getDeclaredField("_elements").apply { isAccessible = true }
    return f.get(this) as ArrayList<AbstractElement>
}

@Suppress("UNCHECKED_CAST")
fun Node.copy(newName: String? = null): Node {
    val node = Node(newName ?: name)
    val f = this.attrs::class.java.getDeclaredField("_attrs").apply { isAccessible = true }
    (f.get(this.attrs) as Map<Attrs.Key, Any>).forEach { (key, value) -> node.attrs[key] = value }
    return node
}