plugins {
    `java-library`
}

description = "Bounded HTTPS provider for signed identity-registry snapshot artifacts"

dependencies {
    api(project(":identity-registry"))
}
