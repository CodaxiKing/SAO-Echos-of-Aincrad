rootProject.name = "aincrad-life"

// API do AincradCore direto da pasta vizinha (build composto).
includeBuild("../aincrad-core")
// Opcionais em tempo de execução: Col (estalagens), XP (bônus de descanso) e andares (ponto de retorno).
includeBuild("../aincrad-economy")
includeBuild("../aincrad-rpg")
includeBuild("../aincrad-floors")
