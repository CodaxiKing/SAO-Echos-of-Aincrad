plugins {
    java
    `maven-publish`
}

group = "com.aincrad"
version = "0.1.0"

val hikariVersion = "6.3.0"
val mysqlVersion = "9.3.0"
val sqliteVersion = "3.49.1.0"

allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Baixadas pelo Paper na primeira vez que o servidor liga (libraries no plugin.yml).
    compileOnly("com.zaxxer:HikariCP:$hikariVersion")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("com.zaxxer:HikariCP:$hikariVersion")
    testImplementation("org.xerial:sqlite-jdbc:$sqliteVersion")
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
        expand(
            "version" to project.version,
            "hikari" to hikariVersion,
            "mysql" to mysqlVersion,
            "sqlite" to sqliteVersion,
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("AincradCore-${project.version}.jar")
}

// ./gradlew publishToMavenLocal → com.aincrad:aincrad-core:0.1.0 para os outros plugins.
java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("core") {
            artifactId = "aincrad-core"
            from(components["java"])
        }
    }
}
