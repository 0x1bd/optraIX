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
    jvmArgs("-Xmx6g")
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
    jvmArgs("-Xmx6g",
        "-XX:StartFlightRecording=duration=60s,filename=/tmp/claude-1000/-home-kvxd-IdeaProjects-gogolmc/f869ba00-614b-4c62-a55c-2c3ae049ac4a/scratchpad/opt3x.jfr,settings=profile")
    args = listOf("60", "40", "30")
}
