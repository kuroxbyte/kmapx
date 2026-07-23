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
    ":spi",
    ":ext-jvm",
    ":ext-serialization",
    ":ext-kotlinx-datetime",
    ":adapter-reflect",
    ":backend-codegen",
    ":frontend-ksp",
    ":architecture-tests",
    ":integration-tests",
    ":incremental-tests",
    ":demo",
    ":benchmarks",
    ":benchmarks-gen:ksp",
    ":benchmarks-gen:kapt",
    ":gradle-plugin",
)
