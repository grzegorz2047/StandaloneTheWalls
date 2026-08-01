import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

group = "pl.grzegorz2047.standalonethewalls"
version = "0.1.0-SNAPSHOT"

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
        add("testImplementation", platform(libs.junit.bom))
        add("testImplementation", libs.junit.jupiter)
        add("testImplementation", libs.assertj.core)
        add("testRuntimeOnly", libs.junit.platform.launcher)
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

val verifyArchitecture by tasks.registering {
    group = "verification"
    description = "Fails when renderer dependencies leak into engine-free modules."
    inputs.files(engineFreeModules.map { file("$it/src") })

    doLast {
        engineFreeModules.forEach { module ->
            fileTree("$module/src") {
                include("**/*.java", "**/*.kt")
            }.forEach { sourceFile ->
                val source = sourceFile.readText()
                if ("com.jme3" in source || "org.lwjgl" in source) {
                    throw GradleException(
                        "Renderer dependency found in engine-free module $module: ${sourceFile.relativeTo(rootDir)}"
                    )
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyArchitecture)
}
