plugins {
    `java-library`
}

dependencies {
    api(project(":protocol"))

    implementation(libs.bouncycastle.provider)
    implementation(libs.bouncycastle.tls)

    testImplementation(libs.bouncycastle.pkix)
}
