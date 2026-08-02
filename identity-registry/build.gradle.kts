plugins {
    `java-library`
}

description = "Offline verification and monotonic activation of signed identity-registry snapshots"

dependencies {
    api(project(":protocol"))

    implementation(libs.jackson.core)
    implementation(libs.json.canonicalization)
}
