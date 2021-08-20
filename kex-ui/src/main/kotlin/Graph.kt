import org.jetbrains.research.kthelper.algorithm.Viewable

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