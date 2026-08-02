plugins {
    `java-library`
}

description = "Fail-closed canonical-handle authorization policies above player authentication"

dependencies {
    api(project(":identity-registry"))
}
