rootProject.name = "aincrad-admin"

// API do AincradCore direto da pasta vizinha (build composto).
includeBuild("../aincrad-core")
// Opcionais em tempo de execução: os eventos que vão para o Discord e para o ranking.
includeBuild("../aincrad-floors")
includeBuild("../aincrad-bosses")
includeBuild("../aincrad-reputation")
// O que os plugins acima usam para compilar.
includeBuild("../aincrad-rpg")
includeBuild("../aincrad-economy")
includeBuild("../aincrad-items")
includeBuild("../aincrad-life")
