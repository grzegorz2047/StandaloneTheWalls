import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

group = "pl.grzegorz2047.standalonethewalls"
version = "0.1.0-SNAPSHOT"

val junitBomDependency = libs.junit.bom
val junitJupiterDependency = libs.junit.jupiter
val assertjDependency = libs.assertj.core
val junitLauncherDependency = libs.junit.platform.launcher

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
        withSourcesJar()
    }

    dependencies {
        add("testImplementation", platform(junitBomDependency))
        add("testImplementation", junitJupiterDependency)
        add("testImplementation", assertjDependency)
        add("testRuntimeOnly", junitLauncherDependency)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
        }
    }
}

val engineFreeModules = listOf(
    "shared",
    "game-domain",
    "protocol",
    "map-format",
    "server",
    "bot-client",
)
val engineFreeSourceDirectories =
    engineFreeModules.map { module -> layout.projectDirectory.dir("$module/src") }

val verifyArchitecture = tasks.register("verifyArchitecture") {
    group = "verification"
    description = "Fails when renderer dependencies leak into engine-free modules."
    inputs.files(engineFreeSourceDirectories)
        .withPathSensitivity(PathSensitivity.RELATIVE)

    doLast {
        inputs.files.asFileTree.matching {
            include("**/*.java", "**/*.kt")
        }.files.forEach { sourceFile ->
            val source = sourceFile.readText()
            if ("com.jme3" in source || "org.lwjgl" in source) {
                throw GradleException(
                    "Renderer dependency found in engine-free source: ${sourceFile.invariantSeparatorsPath}"
                )
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyArchitecture)
}
