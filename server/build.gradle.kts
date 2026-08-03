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
    implementation(project(":identity-registry"))
    implementation(project(":identity-registry-file"))
    implementation(project(":identity-registry-http"))
    implementation(project(":identity-policy"))
    implementation(project(":identity-policy-sqlite"))
    implementation(project(":transport-bctls"))

    implementation(libs.bouncycastle.provider)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation(project(":client")) {
        isTransitive = false
    }
    testImplementation(libs.bouncycastle.provider)
    testImplementation(libs.bouncycastle.pkix)
}

application {
    mainClass = "pl.grzegorz2047.standalonethewalls.server.ServerMain"
}

tasks.named<CreateStartScripts>("startScripts") {
    applicationName = "sunderfront-server"
}

val credentialGeneratorScripts = tasks.register<CreateStartScripts>("credentialGeneratorScripts") {
    applicationName = "sunderfront-server-credentials"
    mainClass =
        "pl.grzegorz2047.standalonethewalls.server.release.ServerCredentialsMain"
    classpath = files(tasks.named<Jar>("jar"), configurations.runtimeClasspath)
    outputDir = layout.buildDirectory.dir("generated-scripts/server-credentials").get().asFile
}

distributions {
    named("main") {
        distributionBaseName = "sunderfront-server"
        contents {
            from(rootProject.file("release/server/README.md"))
            from(rootProject.file("release/server/README-PL.txt"))
            from(rootProject.file("release/server/1_GENERUJ_CREDENTIALS.bat"))
            from(rootProject.file("release/server/2_URUCHOM_SERWER.bat"))
            from(rootProject.file("release/windows/require-java-21.bat")) {
                into("tools/windows")
            }
            from(rootProject.file("release/server/config")) {
                into("config")
            }
            from(rootProject.file("release/server/data")) {
                into("data")
            }
            from(rootProject.file("release/server/credentials")) {
                into("credentials")
            }
            from(credentialGeneratorScripts) {
                into("bin")
                filePermissions {
                    unix("rwxr-xr-x")
                }
            }
        }
    }
}

tasks.named<Zip>("distZip") {
    archiveFileName = "sunderfront-server-${project.version}.zip"
}

tasks.named<Tar>("distTar") {
    enabled = false
}
