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
    implementation(project(":identity-policy"))
    implementation(project(":identity-policy-sqlite"))

    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass = "pl.grzegorz2047.standalonethewalls.server.ServerMain"
}
