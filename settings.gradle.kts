pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "kmapx"

include(
    ":annotations",
    ":runtime",
    ":core",
    ":adapter-reflect",
    ":backend-codegen",
    ":frontend-ksp",
    ":architecture-tests",
    ":integration-tests",
    ":incremental-tests",
    ":demo",
    ":gradle-plugin",
)
