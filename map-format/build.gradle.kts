plugins {
    `java-library`
}

dependencies {
    api(project(":shared"))

    implementation(libs.jackson.core)
}
