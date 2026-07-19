// Build STANDALONE: no participa del settings del repo — el IntelliJ Platform
// Gradle Plugin descarga una distribución del IDE y engancharía tareas pesadas al build raíz.
// Construir con: ./gradlew -p intellij-plugin buildPlugin
rootProject.name = "kmapx-intellij-plugin"

// El plugin consume dev.kmapx:core (las reglas de @MapField y los textos de
// diagnóstico) vía composite build — la sustitución por coordenadas es automática.
includeBuild("..")
