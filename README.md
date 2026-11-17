# SAO — Echoes of Aincrad

Plugins Paper (1.21.11, Java 21) que recriam o Aincrad de Sword Art Online num servidor Minecraft.
Cada plugin fica na sua própria pasta, com seu próprio projeto Gradle.

| Pasta | Plugin | Estado |
|---|---|---|
| `aincrad-ui/` | **AincradUI** — menu de ícones estilo SAO, HUD de vida, vida crítica, tela de morte | em desenvolvimento (etapas 14–17) |
| `aincrad-floors/` | AincradFloors — andares, bloqueio, Teleport Gates | planejado |
| `aincrad-bosses/` | AincradBosses — chefes, salas, Last Attack | planejado |
| `aincrad-effects/` | AincradEffects — motor de efeitos e resource pack único | planejado |

## Compilar

Em cada pasta:

```bash
./gradlew build
```

O `.jar` sai em `build/libs/`.
