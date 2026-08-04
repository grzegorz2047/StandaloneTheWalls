import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    application
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":game-domain"))
    implementation(project(":protocol"))
    implementation(project(":map-format"))
    implementation(project(":transport-bctls"))

    implementation(libs.jme3.core)
    implementation(libs.jme3.desktop)
    implementation(libs.jme3.plugins)
    runtimeOnly(libs.jme3.lwjgl3)

    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass = "pl.grzegorz2047.standalonethewalls.client.ClientMain"
}

val uiFontSourceSha256 =
    "694d12d0f3fb2be696dbbde93eee3ccbdee766751d836eb1fbe8aab2d439d38a"
val uiFontAtlasSha256 =
    "8f7a0cdc32475bd7843a5cd00cf10c1d009823e209f5994709a54cb6cb0e3a65"
val uiFontMetadataSha256 =
    "d948290489c23fac65273f7e431d9bd2d345647a715000daa262479a2b807c94"
val uiFontChunkPaths =
    listOf(
        "Interface/Fonts/SunderfrontUI-Regular.png.b64",
        "Interface/Fonts/SunderfrontUI-Regular.png.b64.01",
        "Interface/Fonts/SunderfrontUI-Regular.png.b64.02",
        "Interface/Fonts/SunderfrontUI-Regular.png.b64.03",
        "Interface/Fonts/SunderfrontUI-Regular.png.b64.04",
        "Interface/Fonts/SunderfrontUI-Regular.png.b64.05",
    )
val uiFontChunkFiles = uiFontChunkPaths.map { layout.projectDirectory.file("src/main/resources/$it") }
val uiFontDefaultMetadata =
    layout.projectDirectory.file("src/main/resources/Interface/Fonts/Default.fnt")
val uiFontNamedMetadata =
    layout.projectDirectory.file("src/main/resources/Interface/Fonts/SunderfrontUI-Regular.fnt")
val uiFontLicense =
    layout.projectDirectory.file("src/main/resources/Interface/Fonts/OFL-Andika.txt")
val uiFontManifest = rootProject.layout.projectDirectory.file("assets/ASSET_MANIFEST.json")
val generatedUiFontResources = layout.buildDirectory.dir("generated/ui-font-resources")

val assembleUiFontAtlas = tasks.register("assembleUiFontAtlas") {
    inputs.files(uiFontChunkFiles)
    val outputFile =
        generatedUiFontResources.map {
            it.file("Interface/Fonts/SunderfrontUI-Regular.png")
        }
    outputs.file(outputFile)

    doLast {
        val encoded =
            uiFontChunkFiles.joinToString(separator = "") { chunk ->
                val file = chunk.asFile
                check(file.isFile) { "Missing UI font atlas chunk: ${file.name}" }
                file.readText(Charsets.US_ASCII).filterNot(Char::isWhitespace)
            }
        val atlas = Base64.getDecoder().decode(encoded)
        check(atlas.size == 30_355) {
            "Unexpected UI font atlas size: ${atlas.size}"
        }
        val digest =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(atlas))
        check(digest == uiFontAtlasSha256) {
            "Unexpected UI font atlas SHA-256: $digest"
        }
        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeBytes(atlas)
    }
}

val verifyUiFontProvenance = tasks.register("verifyUiFontProvenance") {
    inputs.files(uiFontDefaultMetadata, uiFontNamedMetadata, uiFontLicense, uiFontManifest)

    doLast {
        fun sha256(bytes: ByteArray): String =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

        val defaultMetadata = uiFontDefaultMetadata.asFile.readBytes()
        val namedMetadata = uiFontNamedMetadata.asFile.readBytes()
        check(defaultMetadata.contentEquals(namedMetadata)) {
            "Default and named UI font metadata differ"
        }
        check(sha256(defaultMetadata) == uiFontMetadataSha256) {
            "Unexpected UI font metadata SHA-256"
        }

        val license = uiFontLicense.asFile.readText(Charsets.UTF_8)
        check(license.contains("SIL OPEN FONT LICENSE Version 1.1")) {
            "UI font OFL license text is missing"
        }
        check(license.contains("Reserved Font Names \"Andika\" and \"SIL\"")) {
            "UI font reserved-name notice is missing"
        }

        val manifest = uiFontManifest.asFile.readText(Charsets.UTF_8)
        for (
            required in
                listOf(
                    "\"id\": \"sunderfront_ui_font\"",
                    "\"license\": \"OFL-1.1\"",
                    uiFontSourceSha256,
                    uiFontAtlasSha256,
                    uiFontMetadataSha256,
                )
        ) {
            check(manifest.contains(required)) {
                "UI font asset manifest is missing: $required"
            }
        }
    }
}

sourceSets {
    main {
        resources.srcDir(generatedUiFontResources)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(assembleUiFontAtlas)
    exclude("Interface/Fonts/*.png.b64*")
}

tasks.named("check") {
    dependsOn(verifyUiFontProvenance)
}

tasks.named<CreateStartScripts>("startScripts") {
    applicationName = "sunderfront-client"
}

val directConnectSmokeScripts = tasks.register<CreateStartScripts>("directConnectSmokeScripts") {
    applicationName = "sunderfront-direct-connect-smoke"
    mainClass =
        "pl.grzegorz2047.standalonethewalls.client.release.DirectConnectSmokeMain"
    classpath = files(tasks.named<Jar>("jar"), configurations.runtimeClasspath)
    outputDir = layout.buildDirectory.dir("generated-scripts/direct-connect-smoke").get().asFile
}

distributions {
    named("main") {
        distributionBaseName = "sunderfront-client"
        contents {
            from(rootProject.file("release/client/README.md"))
            from(rootProject.file("release/client/README-PL.txt"))
            from(rootProject.file("release/client/URUCHOM_KLIENTA.bat"))
            from(rootProject.file("release/windows/require-java-21.bat")) {
                into("tools/windows")
            }
            from(rootProject.file("assets/assets.lock.json")) {
                into("assets")
            }
            from(directConnectSmokeScripts) {
                into("tools")
                filePermissions {
                    unix("rwxr-xr-x")
                }
            }
        }
    }
}

tasks.named<Zip>("distZip") {
    archiveFileName = "sunderfront-client-${project.version}.zip"
}

tasks.named<Tar>("distTar") {
    enabled = false
}
