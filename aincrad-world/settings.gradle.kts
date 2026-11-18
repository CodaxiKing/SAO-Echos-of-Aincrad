rootProject.name = "aincrad-world"

// API do AincradCore direto da pasta vizinha (build composto).
includeBuild("../aincrad-core")
// Opcionais em tempo de execução: os eventos que viram conquistas e as zonas dos andares.
includeBuild("../aincrad-floors")
includeBuild("../aincrad-bosses")
includeBuild("../aincrad-items")
includeBuild("../aincrad-life")
// O que os plugins acima usam para compilar.
includeBuild("../aincrad-rpg")
includeBuild("../aincrad-economy")
includeBuild("../aincrad-reputation")
