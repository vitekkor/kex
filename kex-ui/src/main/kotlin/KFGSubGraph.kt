import info.leadinglight.jdot.SubGraph

class KFGSubGraph(name: String) : SubGraph(name) {
    override fun toDot(): String {
        val dot = StringBuilder()
        dot.append("{")
        if (attrs.has()) {
            dot.append("graph [${attrs.asString}] ")
        }
        for (e in elements) {
            dot.append(e.toDot())
        }
        dot.append("} ")
        return dot.toString()
    }
}