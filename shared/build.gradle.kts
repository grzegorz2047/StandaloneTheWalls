import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-library`
}

abstract class GenerateBuildProvenanceTask : DefaultTask() {
    @get:Input abstract val repositoryCommit: Property<String>

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val input = repositoryCommit.get()
        val normalized =
            if (input == "unavailable") {
                input
            } else {
                check(input.length == 40 || input.length == 64) {
                    "Repository commit must be a full 40- or 64-character Git object id"
                }
                check(
                    input.all { character ->
                        character in '0'..'9' ||
                            character in 'a'..'f' ||
                            character in 'A'..'F'
                    }
                ) {
                    "Repository commit must be hexadecimal"
                }
                input.lowercase()
            }
        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(
            "schemaVersion=1\nrepositoryCommit=$normalized\n",
            Charsets.UTF_8,
        )
    }
}

val repositoryCommitInput =
    providers.gradleProperty("sunderfrontRepositoryCommit")
        .orElse(providers.environmentVariable("GITHUB_SHA"))
        .orElse("unavailable")
val generatedBuildProvenanceResources = layout.buildDirectory.dir("generated/build-provenance-resources")
val generateBuildProvenance =
    tasks.register<GenerateBuildProvenanceTask>("generateBuildProvenance") {
        repositoryCommit.set(repositoryCommitInput)
        outputFile.set(
            generatedBuildProvenanceResources.map {
                it.file("META-INF/sunderfront-build.properties")
            }
        )
    }

sourceSets {
    main {
        resources.srcDir(generatedBuildProvenanceResources)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(generateBuildProvenance)
}
