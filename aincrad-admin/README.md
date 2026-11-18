# AincradAdmin

Ferramentas do dono do servidor. Não muda o jogo:

- **registra as ações de administração** dos outros plugins Aincrad — quem fez, quando, o quê e em
  qual servidor (etapa 28);
- faz o **backup diário** das configs, dos dados e do banco SQLite, e **restaura** uma data (etapa 30);
- mostra o **estado da rede** em `/aincrad status` (etapa 30);
- publica no **Discord** por webhook e serve o **ranking num site** pequeno (etapa 29).

Precisa do **AincradCore**. Opcionais: AincradFloors, AincradBosses e AincradReputation, de onde vêm
os eventos do Discord e do ranking. O registro não depende de ninguém: os outros plugins disparam o
`AincradAdminActionEvent` do core, e este só ouve.

> Compila e passa nos testes — o de backup faz a ida e a volta em disco com um SQLite de verdade —,
> mas **ainda não foi testado num servidor de verdade**.

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
| `admin` | backup feito à mão e restauração pedida |

Cada linha guarda a data, o autor (jogador ou console), o plugin, a ação (`andar.liberar`), o
detalhe (`andar 5`) e o nome do servidor. Fica no banco do core, então vale para a rede inteira.

## Registro: `/aincrad log`

| Comando | O que faz | Permissão |
|---|---|---|
| `/aincrad log` | As ações mais recentes | `aincrad.admin` |
| `/aincrad log <jogador>` | O que o jogador fez **e** o que fizeram com ele (o nome aparece no detalhe) | `aincrad.admin` |
| `/aincrad log * <tipo>` | Só um tipo: o plugin (`floors`) ou a ação (`andar`, `andar.liberar`) | `aincrad.admin` |
| `/aincrad log <jogador\|*> <tipo\|*> <página>` | Os dois filtros e a página | `aincrad.admin` |

Um número no fim é sempre a página: `/aincrad log 2`, `/aincrad log Kirito 2`.

As linhas mais velhas que `registro.guardar_dias` são apagadas ao ligar e a cada seis horas.

## Backup: `/aincrad backup`

| Comando | O que faz |
|---|---|
| `/aincrad backup agora` | Faz um backup já |
| `/aincrad backup listar` | Os backups guardados, com tamanho e motivo |
| `/aincrad backup restaurar <data>` | Restaura um backup (pede confirmação) |

**O que entra**: os `.yml`, `.yaml` e `.json` da pasta de cada plugin Aincrad (menos o resource
pack gerado e caches) e uma cópia consistente do banco **SQLite**, tirada com o banco em uso. Fica
em `plugins/AincradAdmin/backups/<dia>_<hora>/`, por exemplo `2026-11-18_0400`.

**Quando**: uma vez por dia, na primeira vez que o servidor está ligado depois de `backup.hora`, se
ainda não há backup daquele dia. Os com mais de `backup.guardar_dias` (14) são apagados.

**Restaurar**:

1. Só com o servidor **sem jogadores** (na prática, pelo console).
2. `/aincrad backup restaurar 2026-11-18` — só o dia vale pelo backup mais novo daquele dia; o nome
   inteiro (`2026-11-18_0400`) escolhe um. O plugin mostra o que vai voltar.
3. Repita com `confirmar` no fim, em até 1 minuto.
4. O plugin faz um backup do estado atual (restaurar o backup errado também tem volta), marca a
   restauração e **desliga o servidor**.
5. Ao ligar de novo, os arquivos voltam ao lugar **antes de qualquer plugin ligar** — por isso
   nenhum plugin grava por cima o que tinha na memória.

Limites:

- **MySQL não entra.** Com o banco em MySQL, o backup guarda só os arquivos; faça o backup do banco
  pelo próprio MySQL (`mysqldump`). O comando avisa.
- A restauração põe os arquivos do backup por cima dos atuais; um arquivo criado **depois** do
  backup não é apagado.
- O `config.yml` do core entra no backup, com a senha do MySQL que estiver nele. A pasta de backups
  merece o mesmo cuidado que a pasta de plugins.
- Não é um backup do mapa: os mundos ficam de fora.

## Estado: `/aincrad status`

Mostra o nome do servidor e o tipo de banco; cada plugin Aincrad com a versão (ligado, desligado ou
não instalado); as integrações opcionais (Vault, PlaceholderAPI, floodgate, LuckPerms, MythicMobs,
ModelEngine) e quais plugins as usam; o último backup; e o tamanho do `.zip` do resource pack.

## Discord (`discord.yml`)

Sem bot: um webhook por tipo de mensagem (ou um `padrao` para todos), desligado até `ativo: true`.

| Tipo | Quando | Variáveis do texto |
|---|---|---|
| `andar` | Um andar é liberado | `<andar>` `<nome>` `<participantes>` |
| `chefe` | Um chefe é derrotado | `<chefe>` `<andar>` `<last_attack>` `<top>` (os 3 maiores danos) `<participantes>` `<primeira>` |
| `raid` | Uma raid é marcada | `<chefe>` `<andar>` `<lider>` `<data>` |
| `conquista` | Uma conquista que o servidor anuncia | `<jogador>` `<conquista>` `<titulo>` |

- Só são aceitos endereços de webhook do próprio Discord, por HTTPS.
- O endereço é um segredo: fica só no `discord.yml` e nunca aparece em mensagem nem no console (um
  erro de envio mostra só o tipo e o código HTTP). Ele entra no backup, junto com o arquivo.
- Nomes de jogador e de chefe têm o Markdown neutralizado e as menções ficam desligadas: um nome
  com `@everyone` não avisa ninguém.

## Ranking em site (`config.yml → site`)

Um servidor web pequeno embutido, **desligado por padrão**. Com `site.ativo: true`:

- `http://<servidor>:<porta>/` — a página: linha de frente (andar e quem mais participou de
  primeiras vitórias), Last Attacks, os três maiores danos de cada chefe e o tempo de cada andar.
- `http://<servidor>:<porta>/api/ranking` — os mesmos dados em JSON.

Só leitura (GET), sem login e sem dados pessoais além dos nomes de jogador. Os dados são gravados
quando acontecem (tabelas `admin_kills`, `admin_kill_damage`, `admin_floor_times`) — o ranking
começa a contar a partir de quando o AincradAdmin entra no servidor. A porta precisa estar liberada
na hospedagem; ela fica só na config e não vai para o console.

## Config (`config.yml`)

```yaml
registro:
  guardar_dias: 90     # 0 = para sempre
  por_pagina: 10
  console: true        # também escreve cada ação no console
  formato_data: "dd/MM/yyyy HH:mm"

backup:
  diario: true
  hora: "04:00"
  guardar_dias: 14     # 0 = para sempre

site:
  ativo: false
  endereco: "0.0.0.0"
  porta: 8085
  titulo: "Aincrad — Ranking"
  atualizar_segundos: 60
```

## Banco

`admin_log` (id, at_time, actor_uuid, actor, plugin, action, detail, server), criada pela migração
do core.

## Ainda falta

- **Entrega de item raro** no registro: o plano pede, mas nenhum plugin anuncia isso ainda.
- O Discord e o site nunca foram exercitados contra um webhook e um navegador de verdade.

## Compilar

```bash
./gradlew build
```

O jar sai em `build/libs/AincradAdmin-0.1.0.jar`.
