#!/usr/bin/env python3
"""Monta o pacote do andar 3 em conteudo/andar3/plugins.

Os pacotes somam: o do andar 3 traz tudo dos andares 1 e 2 mais o que é do andar 3. Este script
copia os arquivos do pacote do andar 2 e acrescenta os blocos abaixo; rode o montar_andar2.py
antes se mudou algo nos andares 1 ou 2. Os chefes e as quests têm um arquivo por andar.

Uso:  python conteudo/tools/montar_andar3.py
"""
import os

AQUI = os.path.dirname(os.path.abspath(__file__))
A2 = os.path.join(AQUI, "..", "andar2", "plugins")
A3 = os.path.join(AQUI, "..", "andar3", "plugins")


def ler(caminho):
    with open(os.path.join(A2, caminho), encoding="utf-8") as f:
        return f.read()


def gravar(caminho, texto):
    destino = os.path.join(A3, caminho)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    with open(destino, "w", encoding="utf-8", newline="\n") as f:
        f.write(texto)
    print(caminho)


def titulo(texto):
    """O cabeçalho dos arquivos copiados passa a falar dos três andares."""
    assert "— ANDARES 1 e 2" in texto
    return texto.replace("— ANDARES 1 e 2", "— ANDARES 1 a 3", 1).replace("conteudo/andar2/README.md", "conteudo/andar3/README.md")


# ---------------------------------------------------------------- AincradRPG/spawns.yml
SPAWNS = """
  # ============================================================ ANDAR 3

  # ---------------------------------------------------------- Floresta da Névoa (level 20 a 22)
  # A floresta de árvores gigantes em volta de Zumfut. Os brotos de Treant só andam quando
  # ninguém olha para eles (são creakings do Minecraft).
  floresta_da_nevoa:
    maximo: 16
    por_vez: 2
    raio: 40
    distancia_minima: 10
    sumir_longe: 72
    spawn_natural: false
    mobs:
      - mob: CREAKING
        id: treant_broto
        nome: "<white>Treant Sapling"
        peso: 5
        level: "20-21"
        drops:
          - { item: OAK_LOG, chance: 0.8, quantidade: "1-2" }
          - { item: STICK, chance: 0.6, quantidade: "1-3" }
          - { item: APPLE, chance: 0.15 }
      - mob: WOLF
        id: lobo_da_nevoa
        nome: "<white>Lobo da Névoa"
        agressivo: true
        peso: 3
        level: "20-22"
        drops:
          - { item: BONE, chance: 0.7, quantidade: "1-2" }
          - { item: LEATHER, chance: 0.5 }
      - mob: CAVE_SPIDER
        id: aranha_do_matagal
        nome: "<white>Thicket Spider"
        peso: 3
        level: "21-22"
        periodo: noite
        drops:
          - { item: STRING, chance: 0.8, quantidade: "1-2" }
          - { item: SPIDER_EYE, chance: 0.3 }

  # ---------------------------------------------------------- Bosque Élfico (level 22 a 24)
  # A terra disputada pelos elfos. Os da floresta atacam qualquer jogador; os elfos escuros
  # recebem quem chega em paz no acampamento.
  bosque_elfico:
    maximo: 14
    por_vez: 2
    raio: 40
    distancia_minima: 12
    sumir_longe: 72
    spawn_natural: false
    mobs:
      - mob: VINDICATOR
        id: batedor_elfico
        nome: "<white>Forest Elven Scout"
        equipamento: { mao: IRON_SWORD }
        peso: 4
        level: "22-23"
        drops:
          - { item: EMERALD, chance: 0.25 }
          - { item: IRON_NUGGET, chance: 0.6, quantidade: "2-4" }
      - mob: PILLAGER
        id: arqueiro_elfico
        nome: "<white>Forest Elven Archer"
        peso: 3
        level: "23-24"
        drops:
          - { item: ARROW, chance: 0.8, quantidade: "2-5" }
          - { item: EMERALD, chance: 0.3 }
      - mob: STRAY
        id: espectro_da_nevoa
        nome: "<yellow>Espectro da Névoa"
        peso: 3
        level: "23-24"
        periodo: noite
        drops:
          - { item: BONE, chance: 0.6, quantidade: "1-2" }
          - { item: ARROW, chance: 0.6, quantidade: "1-4" }

  # ---------------------------------------------------------- Caverna das Aranhas (level 24 a 26)
  # A caverna da rainha Nephila Regina, chefe de campo. Só aqui cai o manto de seda.
  caverna_das_aranhas:
    maximo: 16
    por_vez: 2
    raio: 28
    distancia_minima: 8
    sumir_longe: 56
    spawn_natural: false
    mobs:
      - mob: CAVE_SPIDER
        id: aranha_do_matagal
        nome: "<white>Thicket Spider"
        peso: 4
        level: "24-25"
        drops:
          - { item: STRING, chance: 0.8, quantidade: "1-2" }
          - { item: SPIDER_EYE, chance: 0.3 }
      - mob: SPIDER
        id: aranha_tecela
        nome: "<yellow>Aranha Tecelã"
        peso: 4
        level: "25-26"
        drops:
          - { item: STRING, quantidade: "1-3" }
          - { item: COBWEB, chance: 0.3 }
          - { item: "rpg:manto_de_seda_de_aranha", chance: 0.015 }

  # ---------------------------------------------------------- Labirinto do andar 3, andares de baixo (level 26 a 28)
  labirinto_andar3:
    maximo: 18
    por_vez: 2
    raio: 24
    distancia_minima: 8
    sumir_longe: 56
    spawn_natural: false
    mobs:
      - mob: CREAKING
        id: treant_anciao
        nome: "<white>Elder Treant"
        peso: 5
        level: "26-28"
        drops:
          - { item: OAK_LOG, quantidade: "1-3" }
          - { item: RESIN_CLUMP, chance: 0.4, quantidade: "1-2" }
          - { item: GOLD_NUGGET, chance: 0.4, quantidade: "2-4" }
      - mob: BOGGED
        id: semente_maligna
        nome: "<yellow>Evil Treant Seedling"
        peso: 3
        level: "27-28"
        drops:
          - { item: ARROW, chance: 0.7, quantidade: "2-4" }
          - { item: MOSS_BLOCK, chance: 0.4 }

  # ---------------------------------------------------------- Labirinto do andar 3, andares de cima (level 28 a 29)
  labirinto_andar3_topo:
    maximo: 14
    por_vez: 2
    raio: 24
    distancia_minima: 8
    sumir_longe: 56
    spawn_natural: false
    mobs:
      - mob: VINDICATOR
        id: cavaleiro_elfico
        nome: "<yellow>Forest Elven Knight"
        equipamento: { mao: IRON_SWORD, cabeca: CHAINMAIL_HELMET }
        peso: 4
        level: "28-29"
        drops:
          - { item: IRON_INGOT, chance: 0.6, quantidade: "1-3" }
          - { item: EMERALD, chance: 0.4, quantidade: "1-2" }
      - mob: BOGGED
        id: semente_maligna
        nome: "<yellow>Evil Treant Seedling"
        peso: 3
        level: "28-29"
        drops:
          - { item: ARROW, chance: 0.7, quantidade: "2-4" }
          - { item: MOSS_BLOCK, chance: 0.4 }
"""
gravar("AincradRPG/spawns.yml", titulo(ler("AincradRPG/spawns.yml")).rstrip("\n") + "\n" + SPAWNS)

# ---------------------------------------------------------------- AincradRPG/mobs.yml
mobs = titulo(ler("AincradRPG/mobs.yml"))
ancora = "  labirinto_andar2_topo: { min: 18, max: 19 }\n"
assert ancora in mobs
mobs = mobs.replace(ancora, ancora + """  floresta_da_nevoa: { min: 20, max: 22 }
  bosque_elfico: { min: 22, max: 24 }
  caverna_das_aranhas: { min: 24, max: 26 }
  labirinto_andar3: { min: 26, max: 28 }
  labirinto_andar3_topo: { min: 28, max: 29 }
""")
gravar("AincradRPG/mobs.yml", mobs)

# ---------------------------------------------------------------- AincradRPG/items.yml
ITEMS = """
  # ============================================================ ANDAR 3 (level 20 a 30)

  espada_elfica:
    material: IRON_SWORD
    nome: "Espada Élfica"
    level_minimo: 20
    slot: MAINHAND
    bonus:
      dano: [8, 10]
      critico: [3, 5]
    drop: { chance: 0.008, level_mob: [20, 26] }
  adaga_de_espinho:
    material: IRON_SWORD
    nome: "Adaga de Espinho"
    level_minimo: 21
    slot: MAINHAND
    bonus:
      dano: [5, 7]
      velocidade_ataque: [12, 18]
      critico: [5, 9]
    drop: { chance: 0.006, level_mob: [21, 27] }
  florete_da_nevoa:
    material: IRON_SWORD
    nome: "Florete da Névoa"
    level_minimo: 22
    slot: MAINHAND
    bonus:
      dano: [6, 8]
      velocidade_ataque: [10, 16]
      esquiva: [3, 5]
    drop: { chance: 0.004, level_mob: [22, 29] }
  # A recompensa da quest "Lenha para Zumfut".
  machado_do_lenhador:
    material: IRON_AXE
    nome: "Machado do Lenhador"
    level_minimo: 22
    slot: MAINHAND
    bonus:
      dano: [10, 13]
      dano_critico: [8, 14]
  lanca_de_carvalho:
    material: TRIDENT
    nome: "Lança de Carvalho Antigo"
    level_minimo: 23
    slot: MAINHAND
    bonus:
      dano: [9, 11]
      aparar: [4, 7]
    drop: { chance: 0.005, level_mob: [23, 29] }
  montante_elfico:
    material: IRON_SWORD
    nome: "Montante Élfico"
    level_minimo: 24
    slot: MAINHAND
    bonus:
      dano: [12, 14]
    drop: { chance: 0.004, level_mob: [24, 29] }
  # A recompensa da quest "O sabre da cavaleira".
  sabre_da_cavaleira:
    material: IRON_SWORD
    nome: "Sabre da Cavaleira"
    level_minimo: 24
    slot: MAINHAND
    bonus:
      dano: [10, 12]
      velocidade_ataque: [6, 10]
      aparar: [4, 7]
      critico: [3, 5]
  escudo_de_casca:
    material: SHIELD
    nome: "Escudo de Casca"
    level_minimo: 22
    slot: OFFHAND
    bonus:
      armadura: [3, 4]
      aparar: [8, 12]
    drop: { chance: 0.005, level_mob: [22, 29] }
  elmo_elfico:
    material: CHAINMAIL_HELMET
    nome: "Elmo Élfico"
    level_minimo: 21
    slot: HEAD
    bonus:
      vida: [3, 5]
      armadura: [3, 4]
    drop: { chance: 0.006, level_mob: [21, 27] }
  cota_elfica:
    material: CHAINMAIL_CHESTPLATE
    nome: "Cota Élfica"
    level_minimo: 23
    slot: CHEST
    bonus:
      vida: [8, 11]
      armadura: [5, 7]
    drop: { chance: 0.004, level_mob: [23, 29] }
  grevas_elficas:
    material: CHAINMAIL_LEGGINGS
    nome: "Grevas Élficas"
    level_minimo: 22
    slot: LEGS
    bonus:
      vida: [5, 7]
      armadura: [4, 5]
    drop: { chance: 0.005, level_mob: [22, 28] }
  botas_elficas:
    material: CHAINMAIL_BOOTS
    nome: "Botas Élficas"
    level_minimo: 21
    slot: FEET
    bonus:
      armadura: [2, 3]
      velocidade: [2, 4]
    drop: { chance: 0.006, level_mob: [21, 27] }
  # Só cai das Aranhas Tecelãs da caverna (spawns.yml) e da rainha delas.
  manto_de_seda_de_aranha:
    material: LEATHER_CHESTPLATE
    nome: "Manto de Seda de Aranha"
    level_minimo: 25
    slot: CHEST
    bonus:
      vida: [6, 9]
      armadura: [2, 3]
      esquiva: [5, 8]

  # O bônus de Last Attack do Nerius (bosses/andar3.yml). Não cai de mob nenhum.
  raizes_de_nerius:
    material: GOLDEN_BOOTS
    nome: "Roots of Nerius"
    level_minimo: 28
    slot: FEET
    bonus:
      vida: [6, 8]
      armadura: [3, 4]
      velocidade: [4, 6]
      resistencia: [2, 3]
"""
gravar("AincradRPG/items.yml", titulo(ler("AincradRPG/items.yml")).rstrip("\n") + "\n" + ITEMS)

# ---------------------------------------------------------------- AincradRPG/rare-mobs.yml
RARO = """
  # A Raposa de Jade da Floresta da Névoa: foge, e solta esmeraldas a quem a alcançar.
  raposa_de_jade:
    mob: FOX
    nome: "<green>Jade Fox"
    regiao: floresta_da_nevoa
    chance: 0.003
    level: 21
    foge: true
    drop: EMERALD
    drop_quantidade: 4
    some_em_segundos: 120
"""
gravar("AincradRPG/rare-mobs.yml", titulo(ler("AincradRPG/rare-mobs.yml")).rstrip("\n") + "\n" + RARO)

# ---------------------------------------------------------------- AincradEconomy/shops.yml
LOJAS = """
  # ============================================================ ANDAR 3

  # ---------------------------------------------------------- Zumfut
  mercador_zumfut:
    titulo: "<dark_gray>Mercador — Zumfut"
    itens:
      - { item: BREAD, nome: "Pão", quantidade: 4, compra: 24 }
      - { item: APPLE, nome: "Maçã da Floresta", quantidade: 4, compra: 30, venda: 2 }
      - { item: TORCH, nome: "Tocha", quantidade: 8, compra: 16 }
      - { item: ARROW, nome: "Flecha", quantidade: 16, compra: 44 }
      - { item: OAK_LOG, nome: "Tronco de Treant", venda: 3 }
      - { item: STICK, nome: "Graveto", venda: 1 }
      - { item: BONE, nome: "Osso", venda: 2 }
      - { item: EMERALD, nome: "Fragmento de Jade", venda: 30 }
  ferreiro_zumfut:
    titulo: "<dark_gray>Ferreiro — Zumfut"
    itens:
      - { item: "rpg:espada_elfica:comum", compra: 7200 }
      - { item: "rpg:adaga_de_espinho:comum", compra: 6800 }
      - { item: "rpg:florete_da_nevoa:comum", compra: 8200 }
      - { item: "rpg:escudo_de_casca:comum", compra: 6000 }
      - { item: IRON_INGOT, nome: "Barra de Ferro", compra: 60, venda: 15 }
      - { item: GOLD_NUGGET, nome: "Pepita de Ouro", venda: 4 }
  armeiro_zumfut:
    titulo: "<dark_gray>Armeiro — Zumfut"
    itens:
      - { item: "rpg:elmo_elfico:comum", compra: 5800 }
      - { item: "rpg:botas_elficas:comum", compra: 5400 }
      - { item: "rpg:grevas_elficas:comum", compra: 8600 }

  # ---------------------------------------------------------- Acampamento dos elfos escuros
  intendente_elfico:
    titulo: "<dark_gray>Intendente — Acampamento Élfico"
    itens:
      - { item: "rpg:lanca_de_carvalho:comum", compra: 9200 }
      - { item: "rpg:montante_elfico:comum", compra: 11000 }
      - { item: "rpg:cota_elfica:comum", compra: 11800 }
      - { item: GOLDEN_APPLE, nome: "Fruto Dourado", compra: 900 }
      - { item: STRING, nome: "Seda de Aranha", venda: 3 }
      - { item: SPIDER_EYE, nome: "Olho de Aranha", venda: 8 }
      - { item: COBWEB, nome: "Teia", venda: 6 }

  # ---------------------------------------------------------- Posto avançado, ao pé do labirinto
  mercador_posto:
    titulo: "<dark_gray>Mercador — Posto Avançado"
    itens:
      - { item: BREAD, nome: "Pão", quantidade: 4, compra: 24 }
      - { item: COOKED_BEEF, nome: "Bife", quantidade: 2, compra: 44 }
      - { item: TORCH, nome: "Tocha", quantidade: 8, compra: 16 }
      - { item: ARROW, nome: "Flecha", quantidade: 16, compra: 44 }
      - { item: RESIN_CLUMP, nome: "Resina de Treant", venda: 10 }
      - { item: MOSS_BLOCK, nome: "Musgo", venda: 4 }
"""
gravar("AincradEconomy/shops.yml", titulo(ler("AincradEconomy/shops.yml")).rstrip("\n") + "\n" + LOJAS)

# ---------------------------------------------------------------- AincradFloors/floors.yml
floors = ler("AincradFloors/floors.yml")
velho = "#  Andares — ANDARES 1 e 2 prontos, o 3 como reserva"
assert velho in floors
floors = floors.replace(velho, "#  Andares — ANDARES 1 a 3 prontos, o 4 como reserva")
velho = """  3:
    nome: "Andar 3 — Zumfut"
    servidor: ""
    tipo: regiao
    regiao: andar3
    chegada: ""
    cidade: zumfut
"""
assert floors.endswith(velho)
floors += """    vilas: [acampamento_elfico, posto_avancado]
  4:
    nome: "Andar 4 — Rovia"
    servidor: ""
    tipo: regiao
    regiao: andar4
    chegada: ""
    cidade: rovia
"""
gravar("AincradFloors/floors.yml", floors)

# ---------------------------------------------------------------- AincradBosses
gravar("AincradBosses/bosses/andar1.yml", ler("AincradBosses/bosses/andar1.yml"))
gravar("AincradBosses/bosses/andar2.yml", ler("AincradBosses/bosses/andar2.yml"))
gravar("AincradBosses/bosses/andar3.yml", """# ============================================================
#  Chefes do ANDAR 3
#  Pacote de conteúdo: copie para plugins/AincradBosses/bosses/andar3.yml.
#
#  Nerius the Evil Treant, o chefe do andar, e a Nephila Regina, a rainha da Caverna das Aranhas.
#  O Nerius quase não anda: atira espinhos, chama sementes e solta um pólen que paralisa.
#  Pensado para um grupo de 8 a 12 jogadores de level 28 a 30.
# ============================================================
nerius:
  andar: 3
  nome: "Nerius the Evil Treant"
  level: 30
  mob: IRON_GOLEM
  vida: 2400
  dano: 16
  armadura: 11
  velocidade: 0.2
  resistencia_empurrao: 1.0
  escala: 1.6
  intro_segundos: 6
  tempo_limite_segundos: 1200
  anti_cristal: true
  reaparece_horas: 8
  fases:
    - vida: 100
      nome: "A árvore acorda"
      habilidades:
        - { tipo: projetil, recarga: 6, dano: 11, mob: flecha }
        - { tipo: area, recarga: 12, dano: 15, raio: 5, aviso_ticks: 30 }
    - vida: 70
      nome: "Sementes malignas"
      mensagem: "<red>As raízes de <chefe> rasgam o chão e as sementes brotam!"
      habilidades:
        - { tipo: lacaios, recarga: 40, mob: BOGGED, quantidade: 3, level: 27, nome: "Evil Treant Seedling" }
        - { tipo: projetil, recarga: 5, dano: 12, mob: flecha }
        - { tipo: area, recarga: 11, dano: 16, raio: 5.5, aviso_ticks: 28 }
    - vida: 40
      nome: "Pólen paralisante"
      mensagem: "<dark_red><chefe> sacode a copa: o pólen dele paralisa!"
      dano_x: 1.15
      habilidades:
        - { tipo: sopro, recarga: 10, dano: 13, raio: 9, aviso_ticks: 30, segundos: 5 }
        - { tipo: lacaios, recarga: 45, mob: BOGGED, quantidade: 3, level: 28, nome: "Evil Treant Seedling" }
        - { tipo: area, recarga: 10, dano: 17, raio: 5.5, aviso_ticks: 25 }
    - vida: 15
      nome: "Raízes soltas"
      mensagem: "<dark_red><chefe> arranca as raízes do chão e avança!"
      dano_x: 1.3
      velocidade_x: 1.5
      habilidades:
        - { tipo: salto, recarga: 10, dano: 19, raio: 5 }
        - { tipo: sopro, recarga: 12, dano: 14, raio: 9, aviso_ticks: 25, segundos: 4 }
        - { tipo: furia, recarga: 25, segundos: 8, multiplicador: 1.5, nome: "<dark_red>A casca de <chefe> racha e brilha em verde!" }
  recompensas:
    dano_minimo_pct: 4
    xp: 3600
    col: 2200
    # Só para quem deu o golpe final: as raízes do Nerius.
    last_attack: "rpg:raizes_de_nerius:lendario"
    itens:
      - { item: IRON_INGOT, quantidade: 32 }
      - { item: EMERALD, quantidade: 12 }
      - { item: GOLDEN_APPLE, quantidade: 8 }
      - { item: "rpg:montante_elfico:raro", quantidade: 1 }
      - { item: "rpg:cota_elfica:raro", quantidade: 1 }
      - { item: "rpg:espada_elfica:epico", quantidade: 1 }

# Chefe de campo: vive na Caverna das Aranhas e volta 8 horas depois de derrotada (roaming.yml).
nephila_regina:
  andar: 3
  nome: "Nephila Regina"
  level: 25
  mob: SPIDER
  vida: 820
  dano: 12
  armadura: 6
  velocidade: 0.32
  resistencia_empurrao: 0.6
  escala: 1.8
  intro_segundos: 2
  anti_cristal: false
  fases:
    - vida: 100
      nome: "A rainha na teia"
      habilidades:
        - { tipo: lacaios, recarga: 30, mob: CAVE_SPIDER, quantidade: 3, level: 24, nome: "Thicket Spider" }
        - { tipo: salto, recarga: 11, dano: 13, raio: 4 }
    - vida: 45
      nome: "Ninhada"
      mensagem: "<red>Os ovos de <chefe> racham!"
      velocidade_x: 1.2
      habilidades:
        - { tipo: lacaios, recarga: 22, mob: CAVE_SPIDER, quantidade: 4, level: 25, nome: "Thicket Spider" }
        - { tipo: salto, recarga: 8, dano: 15, raio: 4 }
        - { tipo: investida, recarga: 10, dano: 14, raio: 3 }
  recompensas:
    dano_minimo_pct: 5
    xp: 1100
    col: 650
    itens:
      - { item: STRING, quantidade: 24 }
      - { item: SPIDER_EYE, quantidade: 8 }
      - { item: "rpg:manto_de_seda_de_aranha:raro", quantidade: 1 }
""")
roaming = titulo(ler("AincradBosses/roaming.yml"))
ancora = "  bullbous_bow:\n    regiao: pradaria_marome\n    intervalo_horas: 8\n"
assert ancora in roaming
roaming = roaming.replace(ancora, ancora + "  # A rainha da Caverna das Aranhas (bosses/andar3.yml). Volta 8 horas depois de derrotada.\n  nephila_regina:\n    regiao: caverna_das_aranhas\n    intervalo_horas: 8\n")
gravar("AincradBosses/roaming.yml", roaming)

# ---------------------------------------------------------------- AincradQuests
gravar("AincradQuests/quests/andar1.yml", ler("AincradQuests/quests/andar1.yml"))
gravar("AincradQuests/quests/andar2.yml", ler("AincradQuests/quests/andar2.yml"))
gravar("AincradQuests/quests/andar3.yml", """# ============================================================
#  Quests do ANDAR 3
#  Pacote de conteúdo: copie para plugins/AincradQuests/quests/andar3.yml
#  (junto com os dos andares 1 e 2; a história continua de onde o andar 2 parou).
#
#  Zumfut → Floresta da Névoa → Bosque Élfico → Acampamento → Posto Avançado → Labirinto → Nerius
#
#  NPCs que precisam existir (/aincrad npc criar <id> <nome>):
#    guarda_zumfut, lenhador_zumfut, escrivao_zumfut    (Zumfut)
#    kizmel, comandante_elfico         (acampamento dos elfos escuros)
#    capitao_posto                     (posto avançado)
#  Regiões usadas em "chegar": zumfut, acampamento_elfico, posto_avancado, labirinto_andar3
# ============================================================

# ---------------------------------------------------------------- história

chegada_a_zumfut:
  nome: "A cidade nas árvores"
  descricao: "O terceiro andar abriu. Zumfut foi escavada dentro de três árvores gigantes."
  tipo: historia
  npc: guarda_zumfut
  requisitos:
    quest: o_rei_taurus
    andar: 3
  etapas:
    - { tipo: chegar, regiao: zumfut, texto: "Chegue a Zumfut" }
    - { tipo: falar, npc: guarda_zumfut, texto: "Apresente-se ao guarda da cidade" }
  recompensas:
    xp: 3600
    col: 800

a_floresta_que_anda:
  nome: "A floresta que anda"
  descricao: "As árvores novas da floresta saem do lugar quando ninguém olha. São brotos de Treant."
  tipo: historia
  npc: guarda_zumfut
  requisitos:
    quest: chegada_a_zumfut
    level: 20
  etapas:
    - { tipo: matar, alvo: treant_broto, quantidade: 12, texto: "Derrote 12 Treant Saplings na Floresta da Névoa" }
  recompensas:
    xp: 3800
    col: 1000

a_cavaleira_na_nevoa:
  nome: "A cavaleira na névoa"
  descricao: "Dois elfos lutam no bosque. Os da floresta atacam quem passa; a cavaleira escura pede ajuda."
  tipo: historia
  npc: guarda_zumfut
  requisitos:
    quest: a_floresta_que_anda
    level: 21
  etapas:
    - { tipo: matar, alvo: batedor_elfico, quantidade: 6, texto: "Derrote 6 Forest Elven Scouts no Bosque Élfico" }
    - { tipo: chegar, regiao: acampamento_elfico, texto: "Siga a cavaleira até o acampamento" }
    - { tipo: falar, npc: kizmel, texto: "Fale com Kizmel" }
  recompensas:
    xp: 4200
    col: 1200

a_chave_de_jade:
  nome: "A chave de jade"
  descricao: "Os elfos da floresta levaram a chave de jade e a partiram. Os arqueiros carregam os fragmentos."
  tipo: historia
  npc: kizmel
  requisitos:
    quest: a_cavaleira_na_nevoa
    level: 23
  etapas:
    - { tipo: matar, alvo: arqueiro_elfico, quantidade: 10, texto: "Derrote 10 Forest Elven Archers" }
    - { tipo: coletar, item: EMERALD, quantidade: 5, texto: "Junte 5 fragmentos de jade" }
    - { tipo: falar, npc: comandante_elfico, texto: "Leve os fragmentos ao comandante" }
  recompensas:
    xp: 4800
    col: 1500

a_linha_de_frente:
  nome: "A linha de frente"
  descricao: "O comandante indica o caminho da torre. Os jogadores da linha de frente montaram um posto ao pé dela."
  tipo: historia
  npc: comandante_elfico
  requisitos:
    quest: a_chave_de_jade
    level: 25
  etapas:
    - { tipo: chegar, regiao: posto_avancado, texto: "Chegue ao posto avançado" }
    - { tipo: falar, npc: capitao_posto, texto: "Procure o capitão do posto" }
  recompensas:
    xp: 5200
    col: 1800

raizes_da_torre:
  nome: "Raízes da torre"
  descricao: "A torre deste andar é uma árvore oca. Os Treants antigos fecham os andares de baixo."
  tipo: historia
  party: true
  npc: capitao_posto
  requisitos:
    quest: a_linha_de_frente
    level: 26
  etapas:
    - { tipo: chegar, regiao: labirinto_andar3, texto: "Entre no labirinto" }
    - { tipo: matar, alvo: treant_anciao, quantidade: 15, texto: "Derrote 15 Elder Treants" }
    - { tipo: falar, npc: capitao_posto, texto: "Volte ao capitão" }
  recompensas:
    xp: 6000
    col: 2200
    itens: ["rpg:escudo_de_casca"]

os_cavaleiros_da_floresta:
  nome: "Os cavaleiros da floresta"
  descricao: "No topo da torre, os cavaleiros dos elfos da floresta guardam a sala do Treant."
  tipo: historia
  party: true
  npc: capitao_posto
  requisitos:
    quest: raizes_da_torre
    level: 28
  etapas:
    - { tipo: matar, alvo: cavaleiro_elfico, quantidade: 12, texto: "Derrote 12 Forest Elven Knights" }
    - { tipo: falar, npc: capitao_posto, texto: "Volte para a reunião no posto" }
  recompensas:
    xp: 7000
    col: 2800

o_treant_maligno:
  nome: "O Treant maligno"
  descricao: "Nerius não sai do lugar, mas os espinhos e o pólen dele alcançam a sala inteira."
  tipo: historia
  party: true
  npc: capitao_posto
  requisitos:
    quest: os_cavaleiros_da_floresta
  etapas:
    - { tipo: chefe, alvo: nerius, texto: "Derrote Nerius the Evil Treant" }
  recompensas:
    xp: 9000
    col: 5000
    itens: ["GOLDEN_APPLE 6"]

# ---------------------------------------------------------------- fora da história

lenha_para_zumfut:
  nome: "Lenha para Zumfut"
  descricao: "Ninguém corta as árvores da cidade. O lenhador troca troncos de Treant por um machado dele."
  tipo: historia
  npc: lenhador_zumfut
  requisitos:
    quest: a_floresta_que_anda
  etapas:
    - { tipo: coletar, item: OAK_LOG, quantidade: 16, texto: "Consiga 16 troncos" }
    - { tipo: entregar, item: OAK_LOG, quantidade: 16, texto: "Leve os 16 troncos ao lenhador" }
  recompensas:
    xp: 1800
    col: 700
    itens: ["rpg:machado_do_lenhador"]

# O AincradSocial pede esta quest para fundar uma guilda (guilds.yml → fundar.quest).
selo_da_guilda:
  nome: "Selo da Guilda"
  descricao: "O escrivão de Zumfut só registra uma guilda de quem provar que sabe guardar os seus."
  tipo: historia
  npc: escrivao_zumfut
  requisitos:
    quest: chegada_a_zumfut
    level: 20
  etapas:
    - { tipo: falar, npc: escrivao_zumfut, texto: "Ouça o escrivão" }
    - { tipo: matar, alvo: lobo_da_nevoa, quantidade: 15, texto: "Derrote 15 Lobos da Névoa" }
    - { tipo: entregar, item: EMERALD, quantidade: 3, texto: "Leve 3 fragmentos de jade para o lacre" }
  recompensas:
    xp: 2500
    col: 1000

o_sabre_da_cavaleira:
  nome: "O sabre da cavaleira"
  descricao: "Kizmel treina quem a ajudou: limpe a entrada da caverna das Aranhas Tecelãs."
  tipo: historia
  npc: kizmel
  requisitos:
    quest: a_chave_de_jade
    level: 24
  etapas:
    - { tipo: matar, alvo: aranha_tecela, quantidade: 20, texto: "Derrote 20 Aranhas Tecelãs" }
    - { tipo: falar, npc: kizmel, texto: "Volte a Kizmel" }
  recompensas:
    xp: 3200
    col: 1400
    itens: ["rpg:sabre_da_cavaleira"]

a_rainha_das_aranhas:
  nome: "A rainha das aranhas"
  descricao: "No fundo da caverna, a Nephila Regina guarda a ninhada. Os elfos não entram lá."
  tipo: historia
  party: true
  npc: kizmel
  requisitos:
    quest: a_chave_de_jade
    level: 24
  etapas:
    - { tipo: chefe, alvo: nephila_regina, texto: "Derrote a Nephila Regina na Caverna das Aranhas" }
  recompensas:
    xp: 3000
    col: 1600

# ---------------------------------------------------------------- diárias

ronda_da_floresta:
  nome: "Ronda da floresta"
  descricao: "Mantenha as trilhas em volta de Zumfut abertas. Vale para a party inteira."
  tipo: diaria
  party: true
  npc: guarda_zumfut
  requisitos:
    quest: chegada_a_zumfut
  etapas:
    - { tipo: matar, quantidade: 25, texto: "Derrote 25 monstros" }
  recompensas:
    xp: 700
    col: 450

seda_para_o_acampamento:
  nome: "Seda para o acampamento"
  descricao: "Os elfos escuros tecem as cordas dos arcos com seda de aranha."
  tipo: diaria
  npc: comandante_elfico
  requisitos:
    quest: a_chave_de_jade
  etapas:
    - { tipo: entregar, item: STRING, quantidade: 16, texto: "Leve 16 sedas de aranha ao comandante" }
  recompensas:
    xp: 600
    col: 480

# ---------------------------------------------------------------- semanal

limpeza_do_labirinto_3:
  nome: "Limpeza da torre oca"
  descricao: "Quanto menos Treants na torre, mais gente chega viva à sala do Nerius."
  tipo: semanal
  party: true
  npc: capitao_posto
  requisitos:
    quest: raizes_da_torre
  etapas:
    - { tipo: matar, alvo: treant_anciao, quantidade: 50, texto: "Derrote 50 Elder Treants" }
    - { tipo: matar, alvo: cavaleiro_elfico, quantidade: 15, texto: "Derrote 15 Forest Elven Knights" }
  recompensas:
    xp: 7000
    col: 4200
    itens: ["IRON_INGOT 16"]
""")
