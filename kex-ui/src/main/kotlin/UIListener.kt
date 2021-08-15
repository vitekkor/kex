import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.trace.symbolic.ExecutionResult
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kthelper.logging.log
import java.time.Duration

class UIListener(
    host: String,
    port: Int,
    private val containers: List<Container>,
    private val context: ExecutionContext
) {
    private val kfgGraphs = createKFGGraphs(containers, context)

    private lateinit var client: DefaultWebSocketSession

    init {
        startServer(host, port)
    }

    companion object {
        var uiListener: UIListener? = null

        fun ui(containers: List<Container>, context: ExecutionContext): UIListener? {
            uiListener = if (kexConfig.uiEnabled) {
                log.info("UI mode is enabled")
                val host = kexConfig.getStringValue("ui", "host", "localhost")
                val port = kexConfig.getIntValue("ui", "port", 8080)
                UIListener(host, port, containers, context)
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
                                val r = Json.decodeFromString<Response>(text)
                                if (r.code == 2)
                                    outgoing.send(Frame.Text(Json.encodeToString(Response(2, containers.first().name))))
                                else
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
                            val methods = Methods(kfgGraphs[call.parameters["jar"]]!!.map {
                                it.name.split("::").drop(1).joinToString("")
                            })
                            call.respondText(Json.encodeToString(methods))
                        }
                        call.parameters["method"] == call.parameters["jar"] -> {
                            val response = Response(0, kfgGraphs[call.parameters["jar"]]!!.first().toJson())
                            call.respondText(Json.encodeToString(response))
                        }
                        else -> {
                            kfgGraphs[call.parameters["jar"]]?.find { it.name.split("::")[1] == call.parameters["method"] }
                                ?.let { call.respondText(it.toJson()) }
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
                        /*if (graph.elements.find { it is SubGraph && it.name == subGraph.name } != null) call.respondText(
                            "Has already been expanded"
                        ) else {*/
                        // TODO refactor
                        val update = Update(graph.getNodeId(subGraph.name.split("::")[0]), subGraph.toJson())
                        val response = Response(1, Json.encodeToString(update))
                        call.respondText(Json.encodeToString(response))
                    } else {
                        call.respondText("Can't expand $sM instruction")
                    }
                }
            }
        }.start(wait = true)
    }

    private fun createKFGGraphs(
        jars: List<Container>,
        context: ExecutionContext
    ): MutableMap<String, MutableList<KFGGraph>> {
        println("Creating CGF for ${containers.map { it.path }}")
        log.info("Creating CGF for ${containers.map { it.path }}")
        val kfgGraphs = mutableMapOf<String, MutableList<KFGGraph>>()
        for (jar in jars) {
            val cm = context.cm
            cm.initialize(jar)
            val graphs = mutableListOf<KFGGraph>()
            for (klass in cm.concreteClasses) {
                for (method in klass.allMethods) {
                    if (!method.isNative && method.isNotEmpty())
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

    @Serializable
    data class Response(val code: Int, val message: String)

    @Serializable
    data class Update(val nodeId: Int, val subGraph: String)

    fun callBack(executionResult: ExecutionResult) = runBlocking {
        executionResult.trace.trace.trace.forEach {
            println("Instruction - ${it.print()}")
        }
    }
}