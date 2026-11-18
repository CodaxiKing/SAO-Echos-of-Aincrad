# AincradAdmin

Ferramentas do dono do servidor. Não muda o jogo: **registra as ações de administração** dos outros
plugins Aincrad — quem fez, quando, o quê e em qual servidor. Etapa 28 do plano.

Precisa do **AincradCore**. Não depende de nenhum outro plugin: os outros disparam o
`AincradAdminActionEvent` do core, e este plugin só ouve.

> Compila e passa nos testes de regras, mas **ainda não foi testado num servidor de verdade**.

## O que entra no registro

Toda ação que um plugin Aincrad anuncia com o `AincradAdminActionEvent`. Hoje:

| Plugin | Ações |
|---|---|
| `floors` | liberar e bloquear andar e os outros comandos de `/aincrad andar` |
| `rpg` | dar e tirar XP, definir level, gerar item, resetar pontos, criar mob |
| `economy` | dar, tirar e definir Col |
| `bosses` | comandos de chefe e cada campo mudado no editor |
| `items` | dar cristal |
| `life` | comandos de admin das profissões |
| `reputation` | definir prisão, limpar reputação, dar título e conquista |

Cada linha guarda a data, o autor (jogador ou console), o plugin, a ação (`andar.liberar`), o
detalhe (`andar 5`) e o nome do servidor. Fica no banco do core, então vale para a rede inteira.

## Comando

| Comando | O que faz | Permissão |
|---|---|---|
| `/aincrad log` | As ações mais recentes | `aincrad.admin` |
| `/aincrad log <jogador>` | O que o jogador fez **e** o que fizeram com ele (o nome aparece no detalhe) | `aincrad.admin` |
| `/aincrad log * <tipo>` | Só um tipo: o plugin (`floors`) ou a ação (`andar`, `andar.liberar`) | `aincrad.admin` |
| `/aincrad log <jogador\|*> <tipo\|*> <página>` | Os dois filtros e a página | `aincrad.admin` |

Um número no fim é sempre a página: `/aincrad log 2`, `/aincrad log Kirito 2`.

## Config (`config.yml`)

```yaml
registro:
  guardar_dias: 90     # 0 = para sempre
  por_pagina: 10
  console: true        # também escreve cada ação no console
  formato_data: "dd/MM/yyyy HH:mm"
```

As linhas mais velhas que `guardar_dias` são apagadas ao ligar e a cada seis horas.

## Banco

`admin_log` (id, at_time, actor_uuid, actor, plugin, action, detail, server), criada pela migração
do core.

## Ainda falta

- **Entrega de item raro** no registro: o plano pede, mas nenhum plugin anuncia isso ainda.
- **Etapa 29**: Discord por webhook e ranking em site.
- **Etapa 30**: backup, restauração e `/aincrad status`.

## Compilar

```bash
./gradlew build
```

O jar sai em `build/libs/AincradAdmin-0.1.0.jar`.
