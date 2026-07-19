import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "dev.kmapx"
version = "0.1.0-SNAPSHOT"

kotlin { jvmToolchain(17) }

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.5")
        // PSI de Kotlin (KtClassOrObject, imports) — bundled en el IDE:
        bundledPlugin("org.jetbrains.kotlin")
        // Tests con el IDE headless (BasePlatformTestCase): markers y referencias VERIFICADOS.
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")

    // Las reglas de coherencia de @MapField y los mensajes KMX vienen del MISMO
    // core que usa el compilador (composite build): el plugin no duplica ni una regla.
    implementation("io.github.kuroxbyte:core:0.1.0-SNAPSHOT")
    // v0.6 (propuesta A) — el preview usa el PlanEmitter REAL: lo que se ve es lo que se genera.
    implementation("io.github.kuroxbyte:backend-codegen:0.1.0-SNAPSHOT")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "242" }
    }
    // Preparación de publicación: verificación contra los IDEs del rango declarado
    // (`./gradlew -p intellij-plugin verifyPlugin` — descarga IDEs; pensado para CI).
    pluginVerification {
        ides { recommended() }
    }
    // La firma y el publish a Marketplace se activan por variables de entorno cuando existan
    // las credenciales (hito 0.1.0): CERTIFICATE_CHAIN / PRIVATE_KEY / PRIVATE_KEY_PASSWORD
    // y PUBLISH_TOKEN — sin ellas, los tasks correspondientes simplemente no se usan.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
