import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.SubGraph
import info.leadinglight.jdot.impl.EdgeNode
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.kex.trace.symbolic.ExecutionResult
import org.jetbrains.research.kfg.container.Container
import java.io.File
import java.nio.file.Path

class UIListener(private val host: String, private val port: Int, private val containers: List<Container>) {
    var visualisation = false
    var graphs: MutableMap<String, Graph> = if (containers.isNotEmpty()) {
        visualisation = true; println("Creating CGF for ${containers[0]}"); createKFGGraph(Path.of("")) // TODO
    } else mutableMapOf()

    init {
        startServer()
    }

    private fun startServer() {
        embeddedServer(Netty, port = port, host = host) {
            println("Server starts at http://$host:$port/")
            install(CORS) {
                anyHost()
            }
            routing {
                static {
                    resource("/style.css", "style.css")
                    resource("/index.js", "index.js")
                    resource("/kex_logo.svg", "kex_logo.svg")
                    resource("/visual.js", "visual.js")
                }

                get("/") {
                    val content = call.resolveResource(if (!visualisation) "index.html" else "graph.html")
                    if (content != null) {
                        call.respond(content)
                    }
                }

                get("/{jar}/{method}") {
                    println("GET: jar - ${call.parameters["jar"]}; method - ${call.parameters["method"]}")
                    when {
                        call.parameters["method"]!!.endsWith("-all")
                                && call.parameters["method"]!!.removeSuffix("-all") == call.parameters["jar"] -> {
                            println("Respond - all methods in ${call.parameters["jar"]}")
                            call.respondText(Json.encodeToString(Methods(graphs.keys.toList())))
                        }
                        call.parameters["method"] == call.parameters["jar"] -> {
                            call.respondText(graphs.values.first().toDot())
                        }
                        else -> {
                            graphs[call.parameters["method"]!!]?.let { it1 -> call.respondText(it1.toDot()) }
                        }
                    }
                }

                get("/{jar}/{method}/{subMethod}") {
                    val sM = call.parameters["subMethod"]!!.replace("%5C", "/")
                    val split = sM.split("static ").last().split(".").drop(1).joinToString()
                    val regex = Regex("""\(.*\)""")
                    val args = (regex.find(split)?.value ?: "").split(",").size
                    val subGraph = graphs.values.find {
                        split.split(regex)
                            .any { s -> s == it.name.split("::")[0] } && (regex.find(it.name)?.value
                            ?: "").split(",").size == args
                    }
                    val graph = graphs.values.find { it.name == call.parameters["method"] }!!
                    println("Expand: method - ${graph.name}; subMethod - ${subGraph?.name}")
                    if (subGraph != null && subGraph != graph) {
                        if (graph.elements.find { it is SubGraph && it.name == subGraph.name } != null) call.respondText("Has already been expanded") else {
                            val node = graph.findNode(subGraph.name.split("::")[0])!!
                            graph.addSubGraph(subGraph.toSubGraph())
                            val edges =
                                graph.findEdges(node)
                                    .map { edge ->
                                        graph.removeEdge(edge)
                                        subGraph.elements
                                            .filter { it is Node && it.attrs.asString.contains(Regex("""(.*throw.*)|(.*return.*)""")) }
                                            .map { node_ ->
                                                Edge((node_ as Node).name, (edge.getElements().last() as EdgeNode).name)
                                            }
                                    }.flatten()
                            graph.addEdge(
                                Edge(
                                    node.name,
                                    (subGraph.elements.find {
                                        it is Node && subGraph.elements.all { e ->
                                            if (e is Edge) (e.getElements()[1] as EdgeNode).name != it.name else true
                                        }
                                    } as Node).name))
                            graph.addEdges(*edges.toTypedArray())
                            call.respondText(graph.toDot())
                        }
                    } else {
                        call.respondText("Can't expand $sM instruction")
                    }
                }

                var fileName = ""
                post("/jar") {
                    val multipartData = call.receiveMultipart()

                    multipartData.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                fileName = part.originalFileName as String
                                val fileBytes = part.streamProvider().readBytes()
                                try {
                                    val file = File(".\\jars\\$fileName")
                                    file.createNewFile()
                                    file.writeBytes(fileBytes)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                println("File: $fileName")
                            }
                        }
                    }
                    try {
                        graphs = createKFGGraph(Path.of(".\\jars\\$fileName"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    visualisation = true
                    call.respondText("File accepted!")
                }
            }
        }.start(wait = true)
    }

    fun callBack(executionResult: ExecutionResult) {

    }
}