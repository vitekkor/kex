package org.jetbrains.research.kex

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.collections.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.trace.symbolic.ExecutionResult
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kthelper.logging.log
import java.time.Duration
import kotlin.io.path.name

class UIListener(
    host: String,
    port: Int,
    private val containers: List<Container>,
    context: ExecutionContext
) {
    private val kfgGraphs = createKFGGraphs(containers, context)
    private val traces = mutableListOf<ExecutionResult>()

    private var client: DefaultWebSocketSession? = null

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
                    resource("/info.png", "info.png")
                    resource("/warn.png", "warn.png")
                    resource("/visual.js", "visual.js")
                }

                webSocket("/") {
                    client = this
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                val r = Json.decodeFromString<Response>(text)
                                when (r.code) {
                                    20 -> processTrace(r.message.toInt())
                                    3 -> outgoing.send(Frame.Text(Response(3, containers.first().path.name).toJson()))
                                    else -> outgoing.send(Frame.Text("YOU SAID: $text"))
                                }
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
                                it.name.split("::").drop(1).joinToString("::")
                            })
                            call.respondText(Json.encodeToString(methods))
                        }
                        call.parameters["method"] == call.parameters["jar"] -> {
                            val response = Response(0, kfgGraphs[call.parameters["jar"]]!!.first().toJson())
                            call.respondText(response.toJson())
                        }
                        else -> {
                            kfgGraphs[call.parameters["jar"]]!!.parallelStream().filter {
                                it.name.split("::").drop(1).joinToString("::") == call.parameters["method"]
                            }.findFirst().get()
                                .let { call.respondText(Response(0, it.toJson()).toJson()) }
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
                    println("Expand: subMethod - ${subGraph?.name}")
                    if (subGraph != null)
                        call.respondText(Response(1, subGraph.toJson()).toJson())
                    else
                        call.respondText(Response(10, "Can't expand $sM instruction").toJson())

                }
            }
        }.start(wait = false)
        log.info("Server starts at http://$host:$port/")
        println("Ready to continue?")
        readLine()
        //Thread.sleep(3000)
    }

    private fun createKFGGraphs(
        jars: List<Container>,
        context: ExecutionContext
    ): MutableMap<String, MutableList<KFGGraph>> = runBlocking {
        println("Creating CGF for ${containers.map { it.path }}")
        log.info("Creating CGF for ${containers.map { it.path }}")
        val kfgGraphs = mutableMapOf<String, MutableList<KFGGraph>>()
        for (jar in jars) {
            val cm = context.cm
            cm.initialize(jar)
            val graphs = ConcurrentList<KFGGraph>()
            cm.concreteClasses.asFlow().collect { klass ->
                klass.allMethods.asFlow().collect { method ->
                    if (!method.isNative && method.isNotEmpty())
                        graphs.add(method.toGraph(method.name + "::" + method.prototype.replace("/", ".")))
                }
            }
            kfgGraphs[jar.path.name] = graphs
        }
        log.info("Created")
        println("Created")
        return@runBlocking kfgGraphs
    }

    @Serializable
    data class Methods(val methods: List<String>)

    @Serializable
    data class Response(val code: Int, val message: String) {
        fun toJson(): String {
            return Json.encodeToString(this)
        }
    }

    @Serializable
    data class Trace(val method: String, val nodesId: List<String>) {
        fun toJson(): String {
            return Json.encodeToString(this)
        }
    }

    fun callBack(executionResult: ExecutionResult) = runBlocking {
        val name = executionResult.getMethodName() ?: return@runBlocking
        traces.add(executionResult)
        client?.send(Frame.Text(Response(2, name).toJson()))
    }

    private suspend fun processTrace(index: Int) {
        val executionResult = traces[index]
        val name = executionResult.getMethodName() ?: return
        val graph = kfgGraphs.values.first().parallelStream().filter { it.name == name }.findFirst().get()
        val nodesId = executionResult.trace.trace.trace.map {
            graph.getNodeId(
                it.parent.print()
                    .replace(Regex(""" *\t//predecessors *|\t"""), "")
                    .replace("\\\"", "\"")
            )
        }
        val resp = Response(20, Trace(name, nodesId).toJson()).toJson()
        log.debug("RESPONSE - $resp")
        client!!.send(Frame.Text(resp))
    }

    private fun ExecutionResult.getMethodName(): String? {
        val method = trace.trace.trace.firstOrNull()?.parent?.parent ?: return null
        return method.name + "::" + method.prototype.replace("/", ".")
    }
}