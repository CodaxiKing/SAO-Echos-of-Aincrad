# SAO — Echoes of Aincrad

Plugins Paper (1.21.11, Java 21) que recriam o Aincrad de Sword Art Online num servidor Minecraft.
Cada plugin fica na sua própria pasta, com seu próprio projeto Gradle.

| Pasta | Plugin | Estado |
|---|---|---|
| `aincrad-core/` | **AincradCore** — config, mensagens, /aincrad, banco, regiões, menus, perfil, MessageBus | pronto (etapa 0) |
| `aincrad-ui/` | **AincradUI** — menu de ícones estilo SAO, HUD de vida, vida crítica, tela de morte | pronto (etapas 14–17), com as artes do SAO-UI/sao-assets |
| `aincrad-social/` | **AincradSocial** — amigos, party, guilda (como no anime) e mensagens | pronto (dados em YAML até o aincrad-core) |
| `aincrad-rpg/` | **AincradRPG** — level, XP, atributos, level dos mobs, itens com raridade | pronto (etapas A1–A2) |
| `aincrad-economy/` | **AincradEconomy** — Col, Vault, lojas de NPC, /pagar, troca segura, leilão e casas | pronto (etapas A3–A4) |
| `aincrad-floors/` | AincradFloors — andares, bloqueio, Teleport Gates | planejado |
| `aincrad-bosses/` | AincradBosses — chefes, salas, Last Attack | planejado |
| `aincrad-effects/` | **AincradEffects** — motor de efeitos do servidor e resource pack único | motor geral pronto (etapa 10); integração com Floors/Bosses pendente (11–13) |

## Compilar

Em cada pasta (o `aincrad-social` compila junto com o `aincrad-ui`, de quem usa a API):

```bash
./gradlew build
```

O `.jar` sai em `build/libs/`.
