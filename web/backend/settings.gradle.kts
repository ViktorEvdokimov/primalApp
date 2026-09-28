plugins {
    // Скачивает JDK 25 для сборки, если его нет на машине (Gradle запускается и на JDK 17)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "primal-backend"
