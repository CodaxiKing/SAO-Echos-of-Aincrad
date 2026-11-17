rootProject.name = "aincrad-bosses"

// API do AincradCore direto da pasta vizinha (build composto).
includeBuild("../aincrad-core")
// A API dos andares (depend) e as opcionais do RPG e da economia (recompensas).
includeBuild("../aincrad-floors")
includeBuild("../aincrad-rpg")
includeBuild("../aincrad-economy")
