import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer

val testClasspath = the<SourceSetContainer>()["test"].runtimeClasspath

fun propertyArgs(name: String, fallback: String = ""): List<String> =
    ((findProperty(name) as String?) ?: fallback).split(" ").filter { it.isNotEmpty() }

fun tool(
    name: String,
    mainName: String,
    describe: String,
    taskGroup: String = "verification",
    argsProperty: String = "benchArgs",
    defaultArgs: String = "",
    configure: JavaExec.() -> Unit = {},
) = tasks.register(name, JavaExec::class.java) {
    group = taskGroup
    description = describe
    classpath = testClasspath
    mainClass.set(mainName)
    jvmArgs("-Xmx6g")
    args = propertyArgs(argsProperty, defaultArgs)
    configure()
}

tool("bench", "org.kvxd.optraix.bench.RedstoneBench", "redstone engine throughput benchmark") {
    jvmArgs(propertyArgs("benchJvm"))
}

tool("abbench", "org.kvxd.optraix.bench.OptraIxAbBench", "optraix chain fusion A/B benchmark")

tool("worldbench", "org.kvxd.optraix.bench.WorldAbBench", "optraix chain fusion A/B benchmark on a saved world")

tool("tickbench", "org.kvxd.optraix.bench.TickLoopBench", "server tick loop overhead benchmark")

tool(
    "benchprof",
    "org.kvxd.optraix.bench.RedstoneBench",
    "optraix benchmark under Java Flight Recorder",
    defaultArgs = "60 40 30",
) {
    systemProperty("optraixOnly", "1")
    val recording = layout.buildDirectory.file("optraix.jfr").get().asFile
    doFirst { recording.parentFile?.mkdirs() }
    jvmArgs("-XX:StartFlightRecording=duration=60s,filename=$recording,settings=profile")
}

tool(
    "importworld",
    "org.kvxd.optraix.tools.AnvilImport",
    "convert a vanilla anvil world into a optraix world file",
    taskGroup = "tools",
    argsProperty = "importArgs",
)
