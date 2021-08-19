import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package.Companion.defaultPackage
import org.jetbrains.research.kfg.container.JarContainer
import org.jetbrains.research.kfg.util.Flags
import java.nio.file.Path

fun main(args: Array<String>) {
    val p1 = Path.of("C:\\Users\\vitek\\.m2\\repository\\com\\vitekkor\\gpsBackend-jvm\\1.0-SNAPSHOT\\gpsBackend-jvm-1.0-SNAPSHOT.jar")
    val p2 = Path.of("D:\\IdeaProjects\\kex\\kex-test-0.0.1-jar-with-dependencies.jar")
    val jar = JarContainer(p1, defaultPackage)
    val context = ExecutionContext(
        ClassManager(KfgConfig(Flags.readAll, failOnError = true)),
        defaultPackage,
        ClassLoader.getPlatformClassLoader(),
        EasyRandomDriver(),
        listOf()
    )
    UIListener("localhost", 8080, listOf(jar), context)
}