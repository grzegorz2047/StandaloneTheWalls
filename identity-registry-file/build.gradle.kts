plugins {
    `java-library`
}

description = "Local file provider and atomic cache for verified identity-registry snapshots"

dependencies {
    api(project(":identity-registry"))
}
