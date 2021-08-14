import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KFGGraph(val name: String) {

    private val nodes = ArrayList<Node>()
    private val links = ArrayList<Link>()

    @Serializable
    data class Node(val id: Int, val name: String)

    @Serializable
    data class Link(val source: Int, val target: Int)

    @Serializable
    internal data class KFGJson(val name: String, val nodes: List<Node>, val links: List<Link>)

    fun addNode(name: String) {
        nodes.add(Node(nodes.size, name))
    }

    fun addLink(source: String, target: String) {
        links.add(Link(getNodeId(source), getNodeId(target)))
    }

    fun getNodeId(name: String): Int {
        return nodes.find { it.name.contains(name) }?.id ?: -1
    }

    fun toJson(): String {
        return Json.encodeToString(KFGJson(name, nodes, links))
    }
}