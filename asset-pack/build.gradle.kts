import java.io.File

plugins {
    `java-library`
}

description = "Pinned runtime asset-pack lock, verification, and atomic cache"

val prohibitedAssetExtensions = setOf(
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
val maximumRepositoryFileBytes = 5L * 1024L * 1024L
val maximumFixtureBytes = 256L * 1024L
val fixturePrefix = "asset-pack/src/test/resources/fixtures/"
val repositoryRootDirectory = rootProject.rootDir

val verifyAssetSourcePolicy = tasks.register("verifyAssetSourcePolicy") {
    group = "verification"
    description = "Rejects large or runtime-asset binaries from ordinary Git history."

    val repositoryFiles = rootProject.fileTree(repositoryRootDirectory) {
        exclude(
            ".asset-cache/**",
            ".git/**",
            ".gradle/**",
            ".idea/**",
            "**/build/**",
        )
    }
    inputs.files(repositoryFiles)

    doLast {
        val violations = mutableListOf<String>()
        inputs.files.files
            .filter { file -> file.isFile }
            .sortedBy { file -> file.invariantSeparatorsPath }
            .forEach { file ->
                val relative =
                    repositoryRootDirectory
                        .toPath()
                        .relativize(file.toPath())
                        .toString()
                        .replace(File.separatorChar, '/')
                val fixture = relative.startsWith(fixturePrefix)
                val extension = file.extension.lowercase()
                if (extension in prohibitedAssetExtensions && !fixture) {
                    violations.add("$relative: prohibited runtime-asset extension")
                }
                val maximum = if (fixture) maximumFixtureBytes else maximumRepositoryFileBytes
                if (file.length() > maximum) {
                    violations.add("$relative: file exceeds the repository byte limit")
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
