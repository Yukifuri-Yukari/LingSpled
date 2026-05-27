plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.gradleup.shadow") version "9.3.2"
}

group = "yukifuri.lang.lingspled.compiler"
version = "Indev-0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(files(File("$rootDir/versions/deps").listFiles()))
}

kotlin {
    jvmToolchain(22)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName = "lingspled-compiler"
    archiveClassifier = "full"
}

tasks.jar {
    archiveBaseName = "lingspled-compiler"
    manifest {
        attributes(
            "Main-Class" to "yukifuri.lang.lingspled.compiler.MainKt",
            "Class-Path" to configurations.runtimeClasspath.get().joinToString(" ") {
                it.name
            }
        )
    }
}
