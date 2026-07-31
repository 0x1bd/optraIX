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
    mainClass.set("org.kvxd.optraix.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

apply(from = "gradle/tools.gradle.kts")
