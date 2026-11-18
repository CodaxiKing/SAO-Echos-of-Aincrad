rootProject.name = "aincrad-social"

// A API do AincradUI vem direto da pasta vizinha (build composto): compileOnly, sem copiar o .jar.
includeBuild("../aincrad-ui")
// O banco e o MessageBus do AincradCore (opcional em tempo de execução).
includeBuild("../aincrad-core")
