plugins {
    id("org.jetbrains.kotlin.jvm") version "2.+"
    application
}

group = "net.karpelevitch"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(19)
}
application {
    mainClass = "net.karpelevitch.Main5Kt"
}

task("runMem", JavaExec::class) {
    mainClass.set("net.karpelevitch.WithMemoizationKt")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs = listOf("-mx100g")
}

task("runS", JavaExec::class) {
    mainClass.set("net.karpelevitch.SimplifiedKt")
    classpath = sourceSets.main.get().runtimeClasspath
//    jvmArgs = listOf("-mx100g")
}