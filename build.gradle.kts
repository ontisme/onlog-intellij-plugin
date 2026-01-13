plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.github.ontisme"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // WebSocket client
    implementation("org.java-websocket:Java-WebSocket:1.5.6")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // IntelliJ Platform
    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        instrumentationTools()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.github.ontisme.onlog"
        name = "OnLog"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "241"
            untilBuild = "253.*"
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}
