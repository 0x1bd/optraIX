plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kmcprotocol)
    application
}

group = "org.kvxd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net")
}

dependencies {
    implementation(libs.kmcprotocol.extensions)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.brigadier)

    testImplementation(kotlin("test"))
}

kmcProtocol {
    versions("1.20.4")
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

application {
    mainClass.set("org.kvxd.gogolmc.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "redstone engine throughput benchmark"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.kvxd.gogolmc.bench.RedstoneBench")
    jvmArgs(listOf("-Xmx6g") + ((project.findProperty("benchJvm") as String? ?: "").split(" ").filter { it.isNotEmpty() }))
    args = (project.findProperty("benchArgs") as String? ?: "").split(" ").filter { it.isNotEmpty() }
}

tasks.register<JavaExec>("tickbench") {
    group = "verification"
    description = "server tick loop overhead benchmark"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.kvxd.gogolmc.bench.TickLoopBench")
}

tasks.register<JavaExec>("benchprof") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.kvxd.gogolmc.bench.RedstoneBench")
    systemProperty("opt3xOnly", "1")
    val recording = layout.buildDirectory.file("opt3x.jfr").get().asFile
    doFirst { recording.parentFile?.mkdirs() }
    jvmArgs("-Xmx6g", "-XX:StartFlightRecording=duration=60s,filename=$recording,settings=profile")
    args = (project.findProperty("benchArgs") as String? ?: "60 40 30").split(" ")
}

tasks.register<JavaExec>("importworld") {
    group = "tools"
    description = "convert a vanilla anvil world into a gogolmc world file"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.kvxd.gogolmc.tools.AnvilImport")
    jvmArgs("-Xmx6g")
    args = (project.findProperty("importArgs") as String? ?: "").split(" ").filter { it.isNotEmpty() }
}
