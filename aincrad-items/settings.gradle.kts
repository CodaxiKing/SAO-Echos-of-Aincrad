rootProject.name = "aincrad-items"

// API do AincradCore direto da pasta vizinha (build composto).
includeBuild("../aincrad-core")
// Opcional em tempo de execução: o cristal de teleporte usa os andares do AincradFloors.
includeBuild("../aincrad-floors")
