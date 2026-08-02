import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs) apply false
}

group = "pl.grzegorz2047.standalonethewalls"
version = "0.1.0-SNAPSHOT"

val junitBomDependency = libs.junit.bom
val junitJupiterDependency = libs.junit.jupiter
val assertjDependency = libs.assertj.core
val junitLauncherDependency = libs.junit.platform.launcher
val googleJavaFormatVersion = libs.versions.google.java.format.get()
val jacocoVersion = libs.versions.jacoco.get()

allprojects {
    repositories {
        mavenCentral()
    }

    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }

    val configurationsToLock = configurations
    tasks.register("resolveDependencyLocks") {
        group = "build setup"
        description = "Resolves this project's configurations for lock and verification maintenance."
        notCompatibleWithConfigurationCache(
            "Maintenance task intentionally resolves every configuration in its own project",
        )

        doFirst {
            if (!gradle.startParameter.isWriteDependencyLocks) {
                throw GradleException("resolveDependencyLocks must be run with --write-locks")
            }
        }

        doLast {
            configurationsToLock
                .filter { configuration -> configuration.isCanBeResolved }
                .sortedBy { configuration -> configuration.name }
                .forEach { configuration -> configuration.resolve() }
        }
    }
}

spotless {
    java {
        target("**/src/**/*.java")
        targetExclude("**/build/**")
        googleJavaFormat(googleJavaFormatVersion).aosp()
        removeUnusedImports()
        formatAnnotations()
        forbidWildcardImports()
    }

    format("gradleKotlin") {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("repositoryText") {
        target(
            "*.md",
            "docs/**/*.md",
            "**/README.md",
            "**/*.json",
            "**/*.yml",
            "**/*.yaml",
            ".editorconfig",
            ".gitattributes",
            ".gitignore",
        )
        targetExclude("**/build/**", ".gradle/**", "assets/assets.lock.json")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "com.github.spotbugs")

    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
        withSourcesJar()
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = jacocoVersion
    }

    extensions.configure<SpotBugsExtension> {
        ignoreFailures.set(false)
        showStackTraces.set(true)
        showProgress.set(false)
        effort.set(Effort.MAX)
        reportLevel.set(Confidence.LOW)
        maxHeapSize.set("1g")
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

    tasks.withType<SpotBugsTask>().configureEach {
        reports.create("html") {
            required.set(true)
            outputLocation.set(project.layout.buildDirectory.file("reports/spotbugs/$name.html"))
            setStylesheet("fancy-hist.xsl")
        }
        reports.create("xml") {
            required.set(true)
            outputLocation.set(project.layout.buildDirectory.file("reports/spotbugs/$name.xml"))
        }
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named<Test>("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestReport"))
        dependsOn(tasks.withType<SpotBugsTask>())
    }
}

val engineFreeModules = listOf(
    "shared",
    "game-domain",
    "protocol",
    "map-format",
    "server",
    "bot-client",
    "transport-bctls",
    "identity-registry",
    "identity-registry-file",
    "identity-registry-http",
    "identity-policy",
    "identity-policy-sqlite",
)
val engineFreeSourceDirectories =
    engineFreeModules.map { module -> layout.projectDirectory.dir("$module/src") }
val productionSourceDirectories =
    subprojects.map { project -> layout.projectDirectory.dir("src/main") }

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
                    "Renderer dependency found in engine-free source: ${sourceFile.invariantSeparatorsPath}",
                )
            }
        }
    }
}

val verifySourcePolicy = tasks.register("verifySourcePolicy") {
    group = "verification"
    description = "Rejects unsafe logging and Java native serialization in production sources."
    inputs.files(productionSourceDirectories)
        .withPathSensitivity(PathSensitivity.RELATIVE)

    doLast {
        val violations = mutableListOf<String>()
        inputs.files.asFileTree.matching {
            include("**/*.java", "**/*.kt")
        }.files.sortedBy { it.invariantSeparatorsPath }.forEach { sourceFile ->
            val source = sourceFile.readText()
            val forbidden = listOf(
                "System.out" to "System.out",
                "System.err" to "System.err",
                ".printStackTrace(" to "printStackTrace",
                "ObjectInputStream" to "Java ObjectInputStream",
                "ObjectOutputStream" to "Java ObjectOutputStream",
                "java.io.Serializable" to "java.io.Serializable",
            )
            forbidden.filter { (needle, _) -> needle in source }.forEach { (_, label) ->
                violations.add("${sourceFile.invariantSeparatorsPath}: $label")
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Production source policy violations:\n" + violations.joinToString("\n"),
            )
        }
    }
}

val resolveAndLockAll = tasks.register("resolveAndLockAll") {
    group = "build setup"
    description = "Runs each project's lock-maintenance task under its own project lock."
    dependsOn(allprojects.map { project -> project.tasks.named("resolveDependencyLocks") })
    notCompatibleWithConfigurationCache(
        "Maintenance aggregate intentionally executes configuration-resolution tasks",
    )
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
    dependsOn(verifyArchitecture)
    dependsOn(verifySourcePolicy)
}
