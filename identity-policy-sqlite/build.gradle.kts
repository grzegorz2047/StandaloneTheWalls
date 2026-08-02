plugins {
    `java-library`
}

description = "Transactional SQLite persistence for local identity bindings and audit"

dependencies {
    api(project(":identity-policy"))
    runtimeOnly(libs.sqlite.jdbc)
}
