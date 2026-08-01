plugins {
    application
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":map-format"))

    implementation(libs.jme3.core)
    implementation(libs.jme3.desktop)
    runtimeOnly(libs.jme3.lwjgl3)

    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass = "pl.grzegorz2047.standalonethewalls.mapstudio.MapStudioMain"
}
