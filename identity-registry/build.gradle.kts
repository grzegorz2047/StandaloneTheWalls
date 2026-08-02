plugins {
    `java-library`
}

dependencies {
    api(project(":protocol"))

    implementation(libs.jackson.core)
    implementation(libs.json.canonicalization)
}
