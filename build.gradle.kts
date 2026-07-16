plugins {
    `java-library`
}

group = "dev.sacdj.scdi"
version = "0.1.0"

java {
    toolchain {
        // this is the COMPILE target, resolved via Gradle's own toolchain
        // auto-detection - it does not need to match whatever JDK is
        // running the Gradle daemon itself (that's controlled separately,
        // e.g. via JAVA_HOME/org.gradle.java.home, and Gradle 8.11 doesn't
        // reliably run its own daemon on a JDK this new yet).
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // compileOnly, not implementation - the actual Paper API classes are
    // provided by the server jar at runtime; bundling them into our plugin
    // jar would just bloat it and risk classloader conflicts. targeting the
    // Bukkit/Spigot-compatible surface of the Paper API (not Paper-only
    // bootstrap APIs) - see plugin.yml using the classic format instead of
    // paper-plugin.yml - so this same jar runs on Spigot/CraftBukkit/Paper/
    // Purpur/Pufferfish and other Paper-family forks, not just Paper itself.
    compileOnly("io.papermc.paper:paper-api:26.2.build.60-beta")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
