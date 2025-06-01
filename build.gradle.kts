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
    mainClass = "net.karpelevitch.MainKt"
    applicationDefaultJvmArgs = listOf("-mx15g")
}