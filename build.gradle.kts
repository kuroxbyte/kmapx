plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
}

subprojects {
    // Coordenadas Maven bajo io.github.kuroxbyte; los paquetes siguen siendo dev.kmapx.*.
    group = "io.github.kuroxbyte"
    version = "0.2.0-SNAPSHOT"
}

val publishedModules = mapOf(
    "annotations" to "kmapx annotations (KMP): @MapTo, @Mapper, @MapField and friends",
    "runtime" to "kmapx runtime: the Converts interface (zero framework dependencies)",
    "core" to "kmapx core: the mapping engine (zero compiler dependencies)",
    "spi" to "kmapx SPI (experimental): processor extension points, discovered via ServiceLoader",
    "ext-jvm" to "kmapx JVM converter pack: java.time, java.util.UUID, java.math, java.net — add to ksp(...) and implementation(...)",
    "ext-serialization" to "kmapx kotlinx.serialization pack: JsonElement <-> String — add to ksp(...) and implementation(...)",
    "backend-codegen" to "kmapx backend: materializes mapping plans into Kotlin sources",
    "frontend-ksp" to "kmapx KSP2 processor: add with ksp(...) to generate mappers at compile time",
)
configure(subprojects.filter { it.name in publishedModules.keys }) {
    apply(plugin = "com.vanniktech.maven.publish")
    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        coordinates("io.github.kuroxbyte", "kmapx-${project.name}", version.toString())
        publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
        // La firma SOLO cuando existen las llaves (release): mavenLocal no la exige.
        if (providers.gradleProperty("signingInMemoryKey").isPresent ||
            providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent
        ) {
            signAllPublications()
        }
        pom {
            name.set("kmapx-${project.name}")
            description.set(publishedModules.getValue(project.name))
            url.set("https://github.com/kuroxbyte/kmapx")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("kuroxbyte")
                    name.set("kuroxbyte")
                }
            }
            scm {
                url.set("https://github.com/kuroxbyte/kmapx")
                connection.set("scm:git:git://github.com/kuroxbyte/kmapx.git")
                developerConnection.set("scm:git:ssh://git@github.com/kuroxbyte/kmapx.git")
            }
        }
    }
}

// API docs (Dokka V2) solo para la superficie pública del usuario: annotations y runtime.
configure(listOf(project(":annotations"), project(":runtime"))) {
    apply(plugin = "org.jetbrains.dokka")
}
