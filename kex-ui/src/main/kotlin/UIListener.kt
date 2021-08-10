import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.SubGraph
import info.leadinglight.jdot.enums.Color
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.trace.symbolic.ExecutionResult
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kthelper.logging.log
import java.time.Duration

class UIListener(host: String, port: Int, private val containers: List<Container>) {
    private val kfgGraphs = createKFGGraphs(containers)

    private lateinit var client: DefaultWebSocketSession

    init {
        //startServer(host, port)
    }

    companion object {
        var uiListener: UIListener? = null

        fun ui(containers: List<Container>): UIListener? {
            uiListener = if (kexConfig.uiEnabled) {
                log.info("UI mode is enabled")
                val host = kexConfig.getStringValue("ui", "host", "localhost")
                val port = kexConfig.getIntValue("ui", "port", 8080)
                UIListener(host, port, containers)
            } else null
            return uiListener
        }
    }

    private fun startServer(host: String, port: Int) {
        embeddedServer(Netty, port = port, host = host) {
            println("Server starts at http://$host:$port/")
            install(CORS) {
                anyHost()
            }
            install(WebSockets) {
                timeout = Duration.ofMillis(Long.MAX_VALUE)
            }
            routing {
                static {
                    resource("/", "graph.html")
                    resource("/style.css", "style.css")
                    resource("/kex_logo.svg", "kex_logo.svg")
                    resource("/visual.js", "visual.js")
                }

                webSocket("/") { // websocketSession
                    client = this
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                println("YOU SAID: $text")
                                outgoing.send(Frame.Text("YOU SAID: $text"))
                                if (text.equals("bye", ignoreCase = true)) {
                                    close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
                                }
                            }
                        }
                    }
                }

                get("/{jar}/{method}") {
                    println("GET: jar - ${call.parameters["jar"]}; method - ${call.parameters["method"]}")
                    when {
                        call.parameters["method"]!!.endsWith("-all")
                                && call.parameters["method"]!!.removeSuffix("-all") == call.parameters["jar"] -> {
                            println("Respond - all methods in ${call.parameters["jar"]}")
                            call.respondText(Json.encodeToString(Methods(kfgGraphs.keys.toList())))
                        }
                        /*call.parameters["method"] == call.parameters["jar"] -> {
                            call.respondText(graphs.values.first().toDot())
                        }*/
                        else -> {
                            kfgGraphs[call.parameters["jar"]]?.find { it.name.split("::")[1] == call.parameters["method"] }
                                ?.let { call.respondText(it.toDot()) }
                        }
                    }
                }

                get("/{jar}/{method}/{subMethod}") {
                    val graphs = kfgGraphs[call.parameters["jar"]]!!
                    val sM = call.parameters["subMethod"]!!.replace("%5C", "/")
                    val split = sM.split("static ").last().split(".").drop(1).joinToString()
                    val regex = Regex("""\(.*\)""")
                    val args = (regex.find(split)?.value ?: "").split(",").size
                    val subGraph = graphs.find {
                        split.split(regex)
                            .any { s -> s == it.name.split("::")[0] } && (regex.find(it.name)?.value
                            ?: "").split(",").size == args
                    }
                    val graph = graphs.find { it.name == call.parameters["method"] }!!
                    println("Expand: method - ${graph.name}; subMethod - ${subGraph?.name}")
                    if (subGraph != null && subGraph != graph) {
                        if (graph.elements.find { it is SubGraph && it.name == subGraph.name } != null) call.respondText(
                            "Has already been expanded"
                        ) else {
                            call.respondText(graph.expand(subGraph).toDot())
                        }
                    } else {
                        call.respondText("Can't expand $sM instruction")
                    }
                }

                /*var fileName = ""
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
                }*/
            }
        }.start(wait = true)
    }

    private fun createKFGGraphs(jars: List<Container>): MutableMap<String, MutableList<Graph>> {
        log.info("Creating CGF for ${containers.map { it.path }}")
        val kfgGraphs = mutableMapOf<String, MutableList<Graph>>()
        for (jar in jars) {
            val cm = ClassManager(KfgConfig(Flags.readAll, failOnError = true))
            cm.initialize(jar)
            val graphs = mutableListOf<Graph>()
            for (klass in cm.concreteClasses) {
                for (method in klass.allMethods) {
                    if (!method.isNative)
                        graphs.add(method.toGraph(method.name + "::" + method.prototype.replace("/", ".")))
                }
            }
            kfgGraphs[jar.name] = graphs
            //jar.update(cm, jar.path, jar.classLoader)
        }
        return kfgGraphs
    }

    @Serializable
    data class Methods(val methods: List<String>)

    //@Serializable
    //data class Response(val)

    fun callBack(executionResult: ExecutionResult) {
        //highlightPath(graphs.values, instructions, method)
    }

    fun highlightPath(graphs: MutableCollection<Graph>, path: List<String>, method: String) {
        val graph =
            graphs.find { method.contains(it.name.split("::")[1]) && it.name.split("::")[0] == method.split("_")[1] }!!
        for (instruction in path) {
            val node = graph.findNode(instruction.replace(Regex("""\s"""), "")) ?: continue
            node.setColor(Color.X11.red)
        }
    }
}