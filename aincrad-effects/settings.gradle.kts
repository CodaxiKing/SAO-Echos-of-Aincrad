rootProject.name = "aincrad-effects"

// Integração opcional com o Aincrad: compila contra as APIs dos andares e dos chefes (softdepend).
includeBuild("../aincrad-core")
includeBuild("../aincrad-floors")
includeBuild("../aincrad-bosses")
