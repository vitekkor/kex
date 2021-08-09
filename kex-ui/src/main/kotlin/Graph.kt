import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.SubGraph
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.impl.AbstractElement
import info.leadinglight.jdot.impl.Attrs
import info.leadinglight.jdot.impl.EdgeNode
import org.jetbrains.research.kthelper.algorithm.Viewable
import java.util.ArrayDeque

fun Viewable.toGraph(name: String): Graph {
    val graph = Graph(name)

    graph.addNodes(*graphView.map {
        Node(it.name).setShape(Shape.box).setLabel(it.label).setFontSize(12.0)
    }.toTypedArray())

    graph.setBgColor(Color.X11.transparent)

    for (node in graphView) {
        for (successor in node.successors) {
            graph.addEdge(Edge(node.name, successor.name))
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
            is Node -> if (element.attrs.asString.replace(Regex("""\\l|\s"""), "").replace("\\\"", "\"").contains(name)) return element
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