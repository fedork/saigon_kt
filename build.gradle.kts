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
    implementation("com.google.guava:guava:33.4.8-jre")
    implementation("org.apache.commons:commons-math3:3.+")
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

task("fwd", JavaExec::class) {
    mainClass.set("net.karpelevitch.ForwardKt")
    classpath = sourceSets.main.get().runtimeClasspath
}

task("cir", JavaExec::class) {
    mainClass.set("net.karpelevitch.Main6Kt")
    classpath = sourceSets.main.get().runtimeClasspath
}

task("runS", JavaExec::class) {
    mainClass.set("net.karpelevitch.SimplifiedKt")
    classpath = sourceSets.main.get().runtimeClasspath
//    jvmArgs = listOf("-mx100g")
}