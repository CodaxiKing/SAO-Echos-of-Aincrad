rootProject.name = "aincrad-economy"

// API do AincradCore direto da pasta vizinha (build composto).
includeBuild("../aincrad-core")
// Opcional em tempo de execução: a loja usa as telas planas do AincradUI quando ele está instalado.
includeBuild("../aincrad-ui")
