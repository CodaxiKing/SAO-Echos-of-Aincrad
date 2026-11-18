rootProject.name = "aincrad-duels"

// API do AincradCore direto da pasta vizinha (build composto).
includeBuild("../aincrad-core")
// Opcionais em tempo de execução: aposta em Col, zona segura e cor do cursor.
includeBuild("../aincrad-economy")
includeBuild("../aincrad-floors")
includeBuild("../aincrad-reputation")
// O que os plugins acima usam para compilar.
includeBuild("../aincrad-rpg")
includeBuild("../aincrad-ui")
includeBuild("../aincrad-bosses")
includeBuild("../aincrad-items")
includeBuild("../aincrad-life")
