plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.github.ontisme"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // WebSocket client
    implementation("org.java-websocket:Java-WebSocket:1.5.6")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
}

intellij {
    version.set("2024.1")
    type.set("IC") // IntelliJ IDEA Community Edition

    plugins.set(listOf(
        // No additional plugins needed
    ))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("253.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
