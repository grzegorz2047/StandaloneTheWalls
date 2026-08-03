import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip

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
    runtimeOnly(libs.jme3.lwjgl3)

    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass = "pl.grzegorz2047.standalonethewalls.client.ClientMain"
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
