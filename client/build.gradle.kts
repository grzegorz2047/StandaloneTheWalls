import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import java.util.zip.CRC32
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    application
}

abstract class AssembleUiFontAtlasTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val chunks: ConfigurableFileCollection

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @get:Input abstract val expectedEncodedLength: Property<Int>

    @get:Input abstract val expectedDecodedSize: Property<Int>

    @get:Input abstract val expectedSha256: Property<String>

    @TaskAction
    fun assemble() {
        val cleanedChunks =
            chunks.files.sortedBy { it.name }.map { file ->
                check(file.isFile) { "Missing UI font atlas chunk: ${file.name}" }
                file.readText(Charsets.US_ASCII).filterNot(Char::isWhitespace)
            }
        val encoded = cleanedChunks.joinToString(separator = "")
        check(encoded.length == expectedEncodedLength.get()) {
            "Unexpected UI font atlas Base64 length: ${encoded.length}"
        }
        val atlas = Base64.getDecoder().decode(encoded)
        check(atlas.size == expectedDecodedSize.get()) {
            "Unexpected UI font atlas size: ${atlas.size}"
        }
        check(isValidPng(atlas)) {
            "UI font atlas is not a complete CRC-valid PNG"
        }
        val digest =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(atlas))
        check(digest == expectedSha256.get()) {
            "Unexpected UI font atlas SHA-256: $digest"
        }
        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeBytes(atlas)
    }

    private fun isValidPng(bytes: ByteArray): Boolean {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (bytes.size < signature.size || !bytes.copyOfRange(0, 8).contentEquals(signature)) {
            return false
        }
        var offset = signature.size
        while (offset + 12 <= bytes.size) {
            val length = readUnsignedInt(bytes, offset)
            if (length > Int.MAX_VALUE) {
                return false
            }
            val dataLength = length.toInt()
            val typeOffset = offset + 4
            val dataOffset = typeOffset + 4
            val crcOffset = dataOffset + dataLength
            if (crcOffset + 4 > bytes.size) {
                return false
            }
            val crc = CRC32()
            crc.update(bytes, typeOffset, 4 + dataLength)
            if (crc.value != readUnsignedInt(bytes, crcOffset)) {
                return false
            }
            val type = String(bytes, typeOffset, 4, Charsets.US_ASCII)
            offset = crcOffset + 4
            if (type == "IEND") {
                return dataLength == 0 && offset == bytes.size
            }
        }
        return false
    }

    private fun readUnsignedInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
}

abstract class VerifyUiFontProvenanceTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultMetadata: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val namedMetadata: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val license: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifest: RegularFileProperty

    @get:Input abstract val expectedSourceSha256: Property<String>

    @get:Input abstract val expectedAtlasSha256: Property<String>

    @get:Input abstract val expectedMetadataSha256: Property<String>

    @TaskAction
    fun verify() {
        fun sha256(bytes: ByteArray): String =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

        val defaultBytes = defaultMetadata.get().asFile.readBytes()
        val namedBytes = namedMetadata.get().asFile.readBytes()
        check(defaultBytes.contentEquals(namedBytes)) {
            "Default and named UI font metadata differ"
        }
        check(sha256(defaultBytes) == expectedMetadataSha256.get()) {
            "Unexpected UI font metadata SHA-256"
        }

        val licenseText = license.get().asFile.readText(Charsets.UTF_8)
        check(licenseText.contains("SIL OPEN FONT LICENSE Version 1.1")) {
            "UI font OFL license text is missing"
        }
        check(licenseText.contains("Reserved Font Names \"Andika\" and \"SIL\"")) {
            "UI font reserved-name notice is missing"
        }

        val manifestText = manifest.get().asFile.readText(Charsets.UTF_8)
        for (
            required in
                listOf(
                    "\"id\": \"sunderfront_ui_font\"",
                    "\"license\": \"OFL-1.1\"",
                    expectedSourceSha256.get(),
                    expectedAtlasSha256.get(),
                    expectedMetadataSha256.get(),
                )
        ) {
            check(manifestText.contains(required)) {
                "UI font asset manifest is missing: $required"
            }
        }
    }
}

abstract class VerifyGraphicsBenchmarkScriptsTask : DefaultTask() {
    @get:InputDirectory abstract val scriptsDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val directory = scriptsDirectory.get().asFile
        val unixScript = directory.resolve("sunderfront-graphics-benchmark")
        val windowsScript = directory.resolve("sunderfront-graphics-benchmark.bat")
        check(unixScript.isFile) { "Missing Unix graphics benchmark start script" }
        check(windowsScript.isFile) { "Missing Windows graphics benchmark start script" }
    }
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
    "44721d69ff470c19e9ae10809a3242434fb903644c7d0aa9375223fbd970b385"
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
val generatedUiFontResources = layout.buildDirectory.dir("generated/ui-font-resources")

val assembleUiFontAtlas = tasks.register<AssembleUiFontAtlasTask>("assembleUiFontAtlas") {
    chunks.from(uiFontChunkFiles)
    outputFile.set(
        generatedUiFontResources.map {
            it.file("Interface/Fonts/SunderfrontUI-Regular.png")
        }
    )
    expectedEncodedLength.set(40_476)
    expectedDecodedSize.set(30_357)
    expectedSha256.set(uiFontAtlasSha256)
}

val verifyUiFontProvenance =
    tasks.register<VerifyUiFontProvenanceTask>("verifyUiFontProvenance") {
        defaultMetadata.set(
            layout.projectDirectory.file("src/main/resources/Interface/Fonts/Default.fnt")
        )
        namedMetadata.set(
            layout.projectDirectory.file(
                "src/main/resources/Interface/Fonts/SunderfrontUI-Regular.fnt"
            )
        )
        license.set(
            layout.projectDirectory.file("src/main/resources/Interface/Fonts/OFL-Andika.txt")
        )
        manifest.set(rootProject.layout.projectDirectory.file("assets/ASSET_MANIFEST.json"))
        expectedSourceSha256.set(uiFontSourceSha256)
        expectedAtlasSha256.set(uiFontAtlasSha256)
        expectedMetadataSha256.set(uiFontMetadataSha256)
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

val graphicsBenchmarkScriptsDirectory =
    layout.buildDirectory.dir("generated-scripts/graphics-benchmark")
val graphicsBenchmarkScripts = tasks.register<CreateStartScripts>("graphicsBenchmarkScripts") {
    applicationName = "sunderfront-graphics-benchmark"
    mainClass =
        "pl.grzegorz2047.standalonethewalls.client.performance.GraphicsBenchmarkManualMain"
    classpath = files(tasks.named<Jar>("jar"), configurations.runtimeClasspath)
    outputDir = graphicsBenchmarkScriptsDirectory.get().asFile
}
val verifyGraphicsBenchmarkScripts =
    tasks.register<VerifyGraphicsBenchmarkScriptsTask>("verifyGraphicsBenchmarkScripts") {
        dependsOn(graphicsBenchmarkScripts)
        scriptsDirectory.set(graphicsBenchmarkScriptsDirectory)
    }

tasks.named("check") {
    dependsOn(verifyUiFontProvenance, verifyGraphicsBenchmarkScripts)
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
            from(graphicsBenchmarkScripts) {
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
