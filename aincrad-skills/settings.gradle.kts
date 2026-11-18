rootProject.name = "aincrad-skills"

// API do AincradCore direto da pasta vizinha (build composto).
includeBuild("../aincrad-core")
// Opcionais em tempo de execução: zona segura (Floors) e level do jogador (RPG).
includeBuild("../aincrad-floors")
includeBuild("../aincrad-rpg")
