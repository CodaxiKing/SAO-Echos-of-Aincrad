plugins {
    java
}

group = "com.aincrad"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.aincrad:aincrad-core:0.1.0")

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
    // CREDITOS.md vai dentro do .jar para o comando /creditos
    from("CREDITOS.md")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("AincradUI-${project.version}.jar")
}
