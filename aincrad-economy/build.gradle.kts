plugins {
    java
}

group = "com.aincrad"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // VaultAPI (opcional: só usado se o Vault estiver no servidor).
    maven("https://jitpack.io") {
        content { includeGroup("com.github.MilkBowl") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Obrigatório (depend): config, mensagens, banco, comandos e menus vêm do core.
    compileOnly("com.aincrad:aincrad-core:0.1.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        isTransitive = false
    }

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Bytecode Java 21 (o Paper 1.21.11 exige Java 21+); qualquer JDK 21 ou mais novo compila.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("AincradEconomy-${project.version}.jar")
}
