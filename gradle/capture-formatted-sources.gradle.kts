val captureFormattedSources = tasks.register<org.gradle.api.tasks.Copy>("captureFormattedSources") {
    dependsOn(tasks.named("spotlessApply"))
    from(
        "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/config/identity/RegistryRefreshScheduleConfiguration.java",
        "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/identity/LocalIdentityRuntime.java",
        "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/identity/RegistryRefreshScheduler.java",
        "server/src/test/java/pl/grzegorz2047/standalonethewalls/server/ServerLauncherRegistrySchedulerModeTest.java",
        "server/src/test/java/pl/grzegorz2047/standalonethewalls/server/config/identity/LocalIdentityProcessScheduleConfigurationTest.java",
        "server/src/test/java/pl/grzegorz2047/standalonethewalls/server/identity/LocalIdentityRuntimeRegistryRefreshTest.java",
        "server/src/test/java/pl/grzegorz2047/standalonethewalls/server/identity/RegistryRefreshSchedulerTest.java",
    )
    into(layout.projectDirectory.dir("server/build/reports/tests/formatted"))
}

tasks.named("spotlessJavaCheck") {
    dependsOn(captureFormattedSources)
}
