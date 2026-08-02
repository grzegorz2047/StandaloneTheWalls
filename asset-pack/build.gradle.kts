import java.io.File

plugins {
    `java-library`
}

description = "Pinned runtime asset-pack lock, verification, and atomic cache"

val repositoryFiles = rootProject.fileTree(rootProject.rootDir) {
    exclude(
        ".asset-cache/**",
        ".git/**",
        ".gradle/**",
        ".idea/**",
        "**/build/**",
    )
}

val verifyAssetSourcePolicy = tasks.register("verifyAssetSourcePolicy") {
    group = "verification"
    description = "Rejects large or runtime-asset binaries from ordinary Git history."
    inputs.files(repositoryFiles)

    doLast {
        val prohibitedExtensions = setOf(
            "7z",
            "blend",
            "fbx",
            "glb",
            "gltf",
            "jpeg",
            "jpg",
            "mp3",
            "ogg",
            "otf",
            "png",
            "rar",
            "ttf",
            "wav",
            "zip",
        )
        val fixtureMarker = "/asset-pack/src/test/resources/fixtures/"
        val maximumRepositoryBytes = 5L * 1024L * 1024L
        val maximumFixtureBytes = 256L * 1024L
        val violations = mutableListOf<String>()

        inputs.files.files
            .filter { file -> file.isFile }
            .sortedBy { file -> file.invariantSeparatorsPath }
            .forEach { file ->
                val normalizedPath = file.absolutePath.replace(File.separatorChar, '/')
                val fixture = fixtureMarker in normalizedPath
                val extension = file.extension.lowercase()
                if (extension in prohibitedExtensions && !fixture) {
                    violations.add("$normalizedPath: prohibited runtime-asset extension")
                }
                val maximum = if (fixture) maximumFixtureBytes else maximumRepositoryBytes
                if (file.length() > maximum) {
                    violations.add("$normalizedPath: file exceeds the repository byte limit")
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Asset source policy violations:\n" + violations.joinToString("\n"),
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyAssetSourcePolicy)
}

tasks.register<JavaExec>("syncAssets") {
    group = "asset management"
    description = "Downloads and atomically verifies packs pinned by assets/assets.lock.json."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("pl.grzegorz2047.standalonethewalls.assets.AssetPackSyncMain")
    args(
        rootProject.file("assets/assets.lock.json").absolutePath,
        rootProject.file(".asset-cache").absolutePath,
    )
}
