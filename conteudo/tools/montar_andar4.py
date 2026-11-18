#!/usr/bin/env python3
"""Monta o pacote do andar 4 em conteudo/andar4/plugins.

Os pacotes somam: o do andar 4 traz tudo dos andares 1 a 3 mais o que é do andar 4. Este script
copia os arquivos do pacote do andar 3 e acrescenta os blocos abaixo; rode o montar_andar2.py e o
montar_andar3.py antes se mudou algo nos andares anteriores. Os chefes e as quests têm um arquivo
por andar.

Uso:  python conteudo/tools/montar_andar4.py
"""
import os

AQUI = os.path.dirname(os.path.abspath(__file__))
A3 = os.path.join(AQUI, "..", "andar3", "plugins")
A4 = os.path.join(AQUI, "..", "andar4", "plugins")


def ler(caminho):
    with open(os.path.join(A3, caminho), encoding="utf-8") as f:
        return f.read()


def gravar(caminho, texto):
    destino = os.path.join(A4, caminho)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    with open(destino, "w", encoding="utf-8", newline="\n") as f:
        f.write(texto)
    print(caminho)


def titulo(texto):
    """O cabeçalho dos arquivos copiados passa a falar dos quatro andares."""
    assert "— ANDARES 1 a 3" in texto
    return texto.replace("— ANDARES 1 a 3", "— ANDARES 1 a 4", 1).replace("conteudo/andar3/README.md", "conteudo/andar4/README.md")


# ---------------------------------------------------------------- AincradRPG/spawns.yml
SPAWNS = """
  # ============================================================ ANDAR 4

  # ---------------------------------------------------------- Canais de Rovia (level 30 a 32)
  # O andar inteiro é cortado por canais. Em volta de Rovia, o que sai da água.
  canais_de_rovia:
    maximo: 16
    por_vez: 2
    raio: 40
    distancia_minima: 10
    sumir_longe: 72
    spawn_natural: false
    mobs:
      - mob: DROWNED
        id: afogado_dos_canais
        nome: "<white>Water Wight"
        peso: 5
        level: "30-31"
        drops:
          - { item: ROTTEN_FLESH, chance: 0.7, quantidade: "1-2" }
          - { item: COPPER_INGOT, chance: 0.4 }
      - mob: ENDERMITE
        id: caranguejo_dos_canais
        nome: "<white>Scuttle Crab"
        peso: 4
        level: "30-32"
        drops:
          - { item: PRISMARINE_SHARD, chance: 0.6, quantidade: "1-2" }
          - { item: BONE_MEAL, chance: 0.3 }
      - mob: PHANTOM
        id: mosquito_do_brejo
        nome: "<yellow>Mosquito do Brejo"
        peso: 3
        level: "31-32"
        periodo: noite
        drops:
          - { item: PHANTOM_MEMBRANE, chance: 0.6 }
          - { item: STRING, chance: 0.4 }

  # ---------------------------------------------------------- Floresta dos Ursos (level 32 a 34)
  # A mata na margem dos canais, de onde sai a madeira das gôndolas.
  floresta_dos_ursos:
    maximo: 12
    por_vez: 2
    raio: 40
    distancia_minima: 12
    sumir_longe: 72
    spawn_natural: false
    mobs:
      - mob: POLAR_BEAR
        id: urso_da_floresta
        nome: "<white>Urso da Floresta"
        agressivo: true
        peso: 4
        level: "32-34"
        drops:
          - { item: LEATHER, chance: 0.8, quantidade: "1-2" }
          - { item: COD, chance: 0.5, quantidade: "1-2" }
      - mob: WOLF
        id: lobo_do_pantano
        nome: "<white>Lobo do Pântano"
        agressivo: true
        peso: 3
        level: "32-33"
        drops:
          - { item: BONE, chance: 0.7, quantidade: "1-2" }
          - { item: LEATHER, chance: 0.5 }
      - mob: SKELETON
        id: cacador_caido
        nome: "<yellow>Caçador Caído"
        peso: 3
        level: "33-34"
        periodo: noite
        drops:
          - { item: ARROW, chance: 0.8, quantidade: "2-5" }
          - { item: BONE, chance: 0.6 }

  # ---------------------------------------------------------- Lago da Caldeira (level 34 a 36)
  # O lago no centro do andar, entre a vila de Usco e o Castelo de Yofel. O Biceps Archelon,
  # chefe de campo, vive aqui.
  lago_da_caldeira:
    maximo: 14
    por_vez: 2
    raio: 36
    distancia_minima: 12
    sumir_longe: 72
    spawn_natural: false
    mobs:
      - mob: DROWNED
        id: lanceiro_afogado
        nome: "<white>Lanceiro Afogado"
        equipamento: { mao: TRIDENT }
        peso: 4
        level: "34-36"
        drops:
          - { item: PRISMARINE_SHARD, chance: 0.6, quantidade: "1-3" }
          - { item: COPPER_INGOT, chance: 0.5, quantidade: "1-2" }
          - { item: NAUTILUS_SHELL, chance: 0.05 }
      - mob: BOGGED
        id: arqueiro_do_lodo
        nome: "<white>Arqueiro do Lodo"
        peso: 3
        level: "34-35"
        drops:
          - { item: ARROW, chance: 0.7, quantidade: "2-4" }
          - { item: BONE, chance: 0.6 }
      - mob: WITCH
        id: bruxa_do_brejo
        nome: "<yellow>Bruxa do Brejo"
        peso: 2
        level: "35-36"
        periodo: noite
        drops:
          - { item: GLASS_BOTTLE, chance: 0.6 }
          - { item: REDSTONE, chance: 0.4, quantidade: "1-3" }
          - { item: GLOWSTONE_DUST, chance: 0.4, quantidade: "1-2" }

  # ---------------------------------------------------------- Labirinto do andar 4, andares de baixo (level 36 a 38)
  # A torre meio submersa. Os Elfos Caídos chegaram antes dos jogadores.
  labirinto_andar4:
    maximo: 18
    por_vez: 2
    raio: 24
    distancia_minima: 8
    sumir_longe: 56
    spawn_natural: false
    mobs:
      - mob: WITHER_SKELETON
        id: guerreiro_caido
        nome: "<white>Fallen Elven Warrior"
        equipamento: { mao: IRON_SWORD }
        peso: 5
        level: "36-38"
        drops:
          - { item: IRON_INGOT, chance: 0.5, quantidade: "1-2" }
          - { item: COAL, chance: 0.5, quantidade: "1-3" }
          - { item: GOLD_NUGGET, chance: 0.4, quantidade: "2-5" }
      - mob: DROWNED
        id: guarda_afogado
        nome: "<yellow>Guarda Afogado"
        equipamento: { mao: IRON_SWORD, mao_secundaria: SHIELD, cabeca: TURTLE_HELMET }
        peso: 3
        level: "37-38"
        drops:
          - { item: PRISMARINE_SHARD, quantidade: "1-3" }
          - { item: NAUTILUS_SHELL, chance: 0.08 }

  # ---------------------------------------------------------- Labirinto do andar 4, andares de cima (level 38 a 39)
  labirinto_andar4_topo:
    maximo: 14
    por_vez: 2
    raio: 24
    distancia_minima: 8
    sumir_longe: 56
    spawn_natural: false
    mobs:
      - mob: WITHER_SKELETON
        id: cavaleiro_caido
        nome: "<yellow>Fallen Elven Knight"
        equipamento: { mao: IRON_SWORD, cabeca: IRON_HELMET, peito: IRON_CHESTPLATE }
        peso: 4
        level: "38-39"
        drops:
          - { item: IRON_INGOT, chance: 0.7, quantidade: "1-3" }
          - { item: GOLD_INGOT, chance: 0.2 }
          - { item: "rpg:montante_do_caido", chance: 0.03 }
      - mob: STRAY
        id: atirador_caido
        nome: "<yellow>Fallen Elven Shooter"
        peso: 3
        level: "38-39"
        drops:
          - { item: ARROW, chance: 0.8, quantidade: "2-5" }
          - { item: COAL, chance: 0.5, quantidade: "1-2" }
"""
gravar("AincradRPG/spawns.yml", titulo(ler("AincradRPG/spawns.yml")).rstrip("\n") + "\n" + SPAWNS)

# ---------------------------------------------------------------- AincradRPG/mobs.yml
mobs = titulo(ler("AincradRPG/mobs.yml"))
ancora = "  labirinto_andar3_topo: { min: 28, max: 29 }\n"
assert ancora in mobs
mobs = mobs.replace(ancora, ancora + """  canais_de_rovia: { min: 30, max: 32 }
  floresta_dos_ursos: { min: 32, max: 34 }
  lago_da_caldeira: { min: 34, max: 36 }
  labirinto_andar4: { min: 36, max: 38 }
  labirinto_andar4_topo: { min: 38, max: 39 }
""")
gravar("AincradRPG/mobs.yml", mobs)

# ---------------------------------------------------------------- AincradRPG/items.yml
ITEMS = """
  # ============================================================ ANDAR 4 (level 30 a 40)

  sabre_do_gondoleiro:
    material: IRON_SWORD
    nome: "Sabre do Gondoleiro"
    level_minimo: 30
    slot: MAINHAND
    bonus:
      dano: [11, 13]
      critico: [3, 6]
    drop: { chance: 0.008, level_mob: [30, 36] }
  adaga_de_coral:
    material: IRON_SWORD
    nome: "Adaga de Coral"
    level_minimo: 31
    slot: MAINHAND
    bonus:
      dano: [8, 10]
      velocidade_ataque: [14, 20]
      critico: [6, 10]
    drop: { chance: 0.006, level_mob: [31, 37] }
  florete_de_madreperola:
    material: IRON_SWORD
    nome: "Florete de Madrepérola"
    level_minimo: 32
    slot: MAINHAND
    bonus:
      dano: [9, 11]
      velocidade_ataque: [12, 18]
      esquiva: [3, 6]
    drop: { chance: 0.004, level_mob: [32, 39] }
  lanca_do_pescador:
    material: TRIDENT
    nome: "Lança do Pescador"
    level_minimo: 33
    slot: MAINHAND
    bonus:
      dano: [12, 14]
      aparar: [5, 8]
    drop: { chance: 0.005, level_mob: [33, 39] }
  # A recompensa da quest "A garra do urso".
  machado_de_garra_de_urso:
    material: IRON_AXE
    nome: "Machado de Garra de Urso"
    level_minimo: 33
    slot: MAINHAND
    bonus:
      dano: [13, 16]
      dano_critico: [10, 16]
  espada_de_aco_azul:
    material: IRON_SWORD
    nome: "Espada de Aço Azul"
    level_minimo: 34
    slot: MAINHAND
    bonus:
      dano: [13, 15]
      critico: [4, 6]
      aparar: [2, 4]
    drop: { chance: 0.004, level_mob: [34, 39] }
  # Só cai dos Fallen Elven Knights do topo do labirinto (spawns.yml).
  montante_do_caido:
    material: IRON_SWORD
    nome: "Montante do Elfo Caído"
    level_minimo: 36
    slot: MAINHAND
    bonus:
      dano: [16, 18]
      roubo_vida: [2, 4]
  escudo_de_casco:
    material: SHIELD
    nome: "Escudo de Casco"
    level_minimo: 33
    slot: OFFHAND
    bonus:
      armadura: [4, 5]
      aparar: [10, 14]
    drop: { chance: 0.005, level_mob: [33, 39] }
  elmo_de_escamas:
    material: TURTLE_HELMET
    nome: "Elmo de Escamas"
    level_minimo: 31
    slot: HEAD
    bonus:
      vida: [5, 7]
      armadura: [4, 5]
    drop: { chance: 0.006, level_mob: [31, 37] }
  peitoral_de_escamas:
    material: IRON_CHESTPLATE
    nome: "Peitoral de Escamas"
    level_minimo: 33
    slot: CHEST
    bonus:
      vida: [11, 14]
      armadura: [7, 9]
    drop: { chance: 0.004, level_mob: [33, 39] }
  grevas_de_escamas:
    material: IRON_LEGGINGS
    nome: "Grevas de Escamas"
    level_minimo: 32
    slot: LEGS
    bonus:
      vida: [7, 9]
      armadura: [5, 6]
    drop: { chance: 0.005, level_mob: [32, 38] }
  botas_de_escamas:
    material: IRON_BOOTS
    nome: "Botas de Escamas"
    level_minimo: 31
    slot: FEET
    bonus:
      armadura: [3, 4]
      velocidade: [2, 5]
    drop: { chance: 0.006, level_mob: [31, 37] }
  # A recompensa da quest "A capa do barqueiro".
  capa_do_barqueiro:
    material: LEATHER_CHESTPLATE
    nome: "Capa do Barqueiro"
    level_minimo: 32
    slot: CHEST
    bonus:
      vida: [8, 10]
      armadura: [3, 4]
      esquiva: [6, 9]

  # O bônus de Last Attack do Wythege (bosses/andar4.yml). Não cai de mob nenhum.
  lanca_do_hipocampo:
    material: TRIDENT
    nome: "Lança do Hipocampo"
    level_minimo: 38
    slot: MAINHAND
    bonus:
      dano: [15, 17]
      aparar: [6, 9]
      critico: [4, 6]
      velocidade_ataque: [4, 8]
"""
gravar("AincradRPG/items.yml", titulo(ler("AincradRPG/items.yml")).rstrip("\n") + "\n" + ITEMS)

# ---------------------------------------------------------------- AincradRPG/rare-mobs.yml
RARO = """
  # A Tartaruga de Nácar do Lago da Caldeira: foge, e solta conchas a quem a alcançar.
  tartaruga_de_nacar:
    mob: TURTLE
    nome: "<aqua>Pearl Turtle"
    regiao: lago_da_caldeira
    chance: 0.003
    level: 35
    foge: true
    drop: NAUTILUS_SHELL
    drop_quantidade: 3
    some_em_segundos: 120
"""
gravar("AincradRPG/rare-mobs.yml", titulo(ler("AincradRPG/rare-mobs.yml")).rstrip("\n") + "\n" + RARO)

# ---------------------------------------------------------------- AincradEconomy/shops.yml
LOJAS = """
  # ============================================================ ANDAR 4

  # ---------------------------------------------------------- Rovia
  mercador_rovia:
    titulo: "<dark_gray>Mercador — Rovia"
    itens:
      - { item: BREAD, nome: "Pão", quantidade: 4, compra: 28 }
      - { item: COOKED_COD, nome: "Peixe Assado", quantidade: 2, compra: 40 }
      - { item: TORCH, nome: "Tocha", quantidade: 8, compra: 16 }
      - { item: ARROW, nome: "Flecha", quantidade: 16, compra: 48 }
      - { item: COD, nome: "Peixe", venda: 4 }
      - { item: ROTTEN_FLESH, nome: "Carne Podre", venda: 1 }
      - { item: PHANTOM_MEMBRANE, nome: "Asa de Mosquito", venda: 12 }
      - { item: PRISMARINE_SHARD, nome: "Escama", venda: 8 }
      - { item: NAUTILUS_SHELL, nome: "Concha de Nácar", venda: 120 }
  # Sem barco não se vai longe neste andar.
  estaleiro_rovia:
    titulo: "<dark_gray>Estaleiro — Rovia"
    itens:
      - { item: OAK_BOAT, nome: "Gôndola", compra: 400 }
      - { item: OAK_CHEST_BOAT, nome: "Gôndola de Carga", compra: 900 }
      - { item: LEATHER, nome: "Pele", venda: 6 }
  ferreiro_rovia:
    titulo: "<dark_gray>Ferreiro — Rovia"
    itens:
      - { item: "rpg:sabre_do_gondoleiro:comum", compra: 14000 }
      - { item: "rpg:adaga_de_coral:comum", compra: 13200 }
      - { item: "rpg:florete_de_madreperola:comum", compra: 15600 }
      - { item: "rpg:lanca_do_pescador:comum", compra: 16800 }
      - { item: IRON_INGOT, nome: "Barra de Ferro", compra: 60, venda: 15 }
      - { item: COPPER_INGOT, nome: "Barra de Cobre", venda: 5 }
      - { item: COAL, nome: "Carvão", venda: 2 }
  armeiro_rovia:
    titulo: "<dark_gray>Armeiro — Rovia"
    itens:
      - { item: "rpg:elmo_de_escamas:comum", compra: 11600 }
      - { item: "rpg:botas_de_escamas:comum", compra: 11000 }
      - { item: "rpg:grevas_de_escamas:comum", compra: 16400 }

  # ---------------------------------------------------------- Vila de Usco
  mercador_usco:
    titulo: "<dark_gray>Mercador — Usco"
    itens:
      - { item: BREAD, nome: "Pão", quantidade: 4, compra: 28 }
      - { item: COOKED_COD, nome: "Peixe Assado", quantidade: 2, compra: 40 }
      - { item: TORCH, nome: "Tocha", quantidade: 8, compra: 16 }
      - { item: ARROW, nome: "Flecha", quantidade: 16, compra: 48 }
      - { item: BONE, nome: "Osso", venda: 2 }
      - { item: GLOWSTONE_DUST, nome: "Pó Brilhante", venda: 6 }
      - { item: REDSTONE, nome: "Pó Vermelho", venda: 4 }

  # ---------------------------------------------------------- Castelo de Yofel (elfos escuros)
  intendente_yofel:
    titulo: "<dark_gray>Intendente — Castelo de Yofel"
    itens:
      - { item: "rpg:espada_de_aco_azul:comum", compra: 19500 }
      - { item: "rpg:escudo_de_casco:comum", compra: 14500 }
      - { item: "rpg:peitoral_de_escamas:comum", compra: 22000 }
      - { item: GOLDEN_APPLE, nome: "Fruto Dourado", compra: 900 }
      - { item: GOLD_NUGGET, nome: "Pepita de Ouro", venda: 4 }
"""
gravar("AincradEconomy/shops.yml", titulo(ler("AincradEconomy/shops.yml")).rstrip("\n") + "\n" + LOJAS)

# ---------------------------------------------------------------- AincradFloors/floors.yml
floors = ler("AincradFloors/floors.yml")
velho = "#  Andares — ANDARES 1 a 3 prontos, o 4 como reserva"
assert velho in floors
floors = floors.replace(velho, "#  Andares — ANDARES 1 a 4 prontos, o 5 como reserva")
velho = """  4:
    nome: "Andar 4 — Rovia"
    servidor: ""
    tipo: regiao
    regiao: andar4
    chegada: ""
    cidade: rovia
"""
assert floors.endswith(velho)
floors += """    vilas: [vila_usco, castelo_yofel]
  5:
    nome: "Andar 5 — Karluin"
    servidor: ""
    tipo: regiao
    regiao: andar5
    chegada: ""
    cidade: karluin
"""
gravar("AincradFloors/floors.yml", floors)

# ---------------------------------------------------------------- AincradBosses
for n in (1, 2, 3):
    gravar("AincradBosses/bosses/andar%d.yml" % n, ler("AincradBosses/bosses/andar%d.yml" % n))
gravar("AincradBosses/bosses/andar4.yml", """# ============================================================
#  Chefes do ANDAR 4
#  Pacote de conteúdo: copie para plugins/AincradBosses/bosses/andar4.yml.
#
#  Wythege the Hippocampus, o chefe do andar, e o Biceps Archelon, a tartaruga de duas cabeças
#  do Lago da Caldeira. O Wythege é rápido: investe, salta e chama afogados.
#  Pensado para um grupo de 8 a 12 jogadores de level 38 a 40.
# ============================================================
wythege:
  andar: 4
  nome: "Wythege the Hippocampus"
  level: 40
  mob: RAVAGER
  vida: 3400
  dano: 19
  armadura: 13
  velocidade: 0.3
  resistencia_empurrao: 0.9
  escala: 1.4
  intro_segundos: 6
  tempo_limite_segundos: 1200
  anti_cristal: true
  reaparece_horas: 8
  fases:
    - vida: 100
      nome: "Maré baixa"
      habilidades:
        - { tipo: investida, recarga: 10, dano: 20, raio: 3.5 }
        - { tipo: area, recarga: 12, dano: 18, raio: 5, aviso_ticks: 30 }
    - vida: 75
      nome: "Os afogados"
      mensagem: "<red>A água sobe na sala e os afogados saem dela!"
      habilidades:
        - { tipo: lacaios, recarga: 40, mob: DROWNED, quantidade: 4, level: 37, nome: "Water Wight" }
        - { tipo: investida, recarga: 9, dano: 21, raio: 3.5 }
        - { tipo: area, recarga: 11, dano: 19, raio: 5.5, aviso_ticks: 28 }
    - vida: 45
      nome: "Jato d'água"
      mensagem: "<dark_red><chefe> empina: o jato dele derruba e prende quem estiver na frente!"
      dano_x: 1.15
      habilidades:
        - { tipo: sopro, recarga: 9, dano: 16, raio: 10, aviso_ticks: 28, segundos: 4 }
        - { tipo: salto, recarga: 11, dano: 21, raio: 5 }
        - { tipo: lacaios, recarga: 45, mob: DROWNED, quantidade: 4, level: 38, nome: "Water Wight" }
    - vida: 15
      nome: "Maré cheia"
      mensagem: "<dark_red><chefe> relincha e a maré vira!"
      dano_x: 1.3
      velocidade_x: 1.3
      habilidades:
        - { tipo: investida, recarga: 6, dano: 23, raio: 4 }
        - { tipo: salto, recarga: 9, dano: 22, raio: 5.5 }
        - { tipo: sopro, recarga: 12, dano: 17, raio: 10, aviso_ticks: 24, segundos: 4 }
        - { tipo: furia, recarga: 25, segundos: 8, multiplicador: 1.5, nome: "<dark_red>As escamas de <chefe> brilham em azul!" }
  recompensas:
    dano_minimo_pct: 4
    xp: 5000
    col: 3000
    # Só para quem deu o golpe final: a lança do Hipocampo.
    last_attack: "rpg:lanca_do_hipocampo:lendario"
    itens:
      - { item: IRON_INGOT, quantidade: 40 }
      - { item: NAUTILUS_SHELL, quantidade: 6 }
      - { item: GOLDEN_APPLE, quantidade: 10 }
      - { item: "rpg:montante_do_caido:raro", quantidade: 1 }
      - { item: "rpg:peitoral_de_escamas:raro", quantidade: 1 }
      - { item: "rpg:espada_de_aco_azul:epico", quantidade: 1 }

# Chefe de campo: vive no Lago da Caldeira e volta 8 horas depois de derrotado (roaming.yml).
biceps_archelon:
  andar: 4
  nome: "Biceps Archelon"
  level: 35
  mob: IRON_GOLEM
  vida: 1300
  dano: 15
  armadura: 13
  velocidade: 0.2
  resistencia_empurrao: 1.0
  escala: 1.3
  intro_segundos: 2
  anti_cristal: false
  fases:
    - vida: 100
      nome: "Duas cabeças"
      habilidades:
        - { tipo: area, recarga: 10, dano: 15, raio: 4.5, aviso_ticks: 28 }
        - { tipo: projetil, recarga: 7, dano: 12, mob: flecha }
    - vida: 45
      nome: "Dentro do casco"
      mensagem: "<red><chefe> recolhe as cabeças e gira!"
      velocidade_x: 1.6
      habilidades:
        - { tipo: investida, recarga: 8, dano: 18, raio: 3.5 }
        - { tipo: area, recarga: 9, dano: 16, raio: 5, aviso_ticks: 22 }
  recompensas:
    dano_minimo_pct: 5
    xp: 1700
    col: 950
    itens:
      - { item: PRISMARINE_SHARD, quantidade: 24 }
      - { item: NAUTILUS_SHELL, quantidade: 2 }
      - { item: "rpg:escudo_de_casco:raro", quantidade: 1 }
""")
roaming = titulo(ler("AincradBosses/roaming.yml"))
ancora = "  nephila_regina:\n    regiao: caverna_das_aranhas\n    intervalo_horas: 8\n"
assert ancora in roaming
roaming = roaming.replace(ancora, ancora + "  # A tartaruga de duas cabeças do Lago da Caldeira (bosses/andar4.yml). Volta 8 horas depois de derrotada.\n  biceps_archelon:\n    regiao: lago_da_caldeira\n    intervalo_horas: 8\n")
gravar("AincradBosses/roaming.yml", roaming)

# ---------------------------------------------------------------- AincradQuests
for n in (1, 2, 3):
    gravar("AincradQuests/quests/andar%d.yml" % n, ler("AincradQuests/quests/andar%d.yml" % n))
gravar("AincradQuests/quests/andar4.yml", """# ============================================================
#  Quests do ANDAR 4
#  Pacote de conteúdo: copie para plugins/AincradQuests/quests/andar4.yml
#  (junto com os dos andares 1 a 3; a história continua de onde o andar 3 parou).
#
#  Rovia → Canais → Floresta dos Ursos → Usco → Lago da Caldeira → Castelo de Yofel → Labirinto → Wythege
#
#  NPCs que precisam existir (/aincrad npc criar <id> <nome>):
#    guarda_rovia, construtor_rovia    (Rovia)
#    anciao_usco                       (vila de Usco)
#    visconde_yofel                    (Castelo de Yofel)
#  Regiões usadas em "chegar": rovia, vila_usco, castelo_yofel, labirinto_andar4
# ============================================================

# ---------------------------------------------------------------- história

chegada_a_rovia:
  nome: "A cidade dos canais"
  descricao: "O quarto andar abriu. Em Rovia não há ruas: só canais, pontes e gôndolas."
  tipo: historia
  npc: guarda_rovia
  requisitos:
    quest: o_treant_maligno
    andar: 4
  etapas:
    - { tipo: chegar, regiao: rovia, texto: "Chegue a Rovia" }
    - { tipo: falar, npc: guarda_rovia, texto: "Apresente-se ao guarda do cais" }
  recompensas:
    xp: 9200
    col: 1500

canais_infestados:
  nome: "Canais infestados"
  descricao: "Os Water Wights sobem dos canais e viram as gôndolas de quem sai da cidade."
  tipo: historia
  npc: guarda_rovia
  requisitos:
    quest: chegada_a_rovia
    level: 30
  etapas:
    - { tipo: matar, alvo: afogado_dos_canais, quantidade: 12, texto: "Derrote 12 Water Wights nos Canais de Rovia" }
  recompensas:
    xp: 9600
    col: 1800

a_gondola:
  nome: "A gôndola"
  descricao: "O velho construtor faz um barco para quem trouxer peles de urso para calafetar o casco."
  tipo: historia
  npc: construtor_rovia
  requisitos:
    quest: canais_infestados
    level: 32
  etapas:
    - { tipo: falar, npc: construtor_rovia, texto: "Ouça o construtor de barcos" }
    - { tipo: matar, alvo: urso_da_floresta, quantidade: 10, texto: "Derrote 10 Ursos da Floresta" }
    - { tipo: entregar, item: LEATHER, quantidade: 8, texto: "Leve 8 peles ao construtor" }
  recompensas:
    xp: 10200
    col: 2200
    itens: ["OAK_BOAT 1"]

caminho_para_usco:
  nome: "Rio abaixo"
  descricao: "Com um barco, dá para descer o canal até a vila de Usco, na beira do lago."
  tipo: historia
  npc: construtor_rovia
  requisitos:
    quest: a_gondola
    level: 34
  etapas:
    - { tipo: chegar, regiao: vila_usco, texto: "Desça o canal até Usco" }
    - { tipo: falar, npc: anciao_usco, texto: "Fale com o ancião da vila" }
  recompensas:
    xp: 10800
    col: 2600

o_castelo_de_yofel:
  nome: "O castelo no lago"
  descricao: "Do outro lado do lago fica Yofel, o castelo dos elfos escuros. Os lanceiros afogados cercam a travessia."
  tipo: historia
  npc: anciao_usco
  requisitos:
    quest: caminho_para_usco
    level: 35
  etapas:
    - { tipo: matar, alvo: lanceiro_afogado, quantidade: 10, texto: "Derrote 10 Lanceiros Afogados no Lago da Caldeira" }
    - { tipo: chegar, regiao: castelo_yofel, texto: "Atravesse o lago até o Castelo de Yofel" }
    - { tipo: falar, npc: visconde_yofel, texto: "Peça audiência ao visconde" }
  recompensas:
    xp: 11600
    col: 3000

a_torre_submersa:
  nome: "A torre submersa"
  descricao: "Os Elfos Caídos entraram na torre antes de todos. O visconde quer saber o que procuram."
  tipo: historia
  party: true
  npc: visconde_yofel
  requisitos:
    quest: o_castelo_de_yofel
    level: 36
  etapas:
    - { tipo: chegar, regiao: labirinto_andar4, texto: "Entre no labirinto" }
    - { tipo: matar, alvo: guerreiro_caido, quantidade: 15, texto: "Derrote 15 Fallen Elven Warriors" }
    - { tipo: falar, npc: visconde_yofel, texto: "Volte ao visconde" }
  recompensas:
    xp: 12800
    col: 3600
    itens: ["rpg:escudo_de_casco"]

os_cavaleiros_caidos:
  nome: "Os cavaleiros caídos"
  descricao: "No topo da torre, os cavaleiros dos Elfos Caídos guardam a porta do Hipocampo."
  tipo: historia
  party: true
  npc: visconde_yofel
  requisitos:
    quest: a_torre_submersa
    level: 38
  etapas:
    - { tipo: matar, alvo: cavaleiro_caido, quantidade: 12, texto: "Derrote 12 Fallen Elven Knights" }
    - { tipo: falar, npc: visconde_yofel, texto: "Volte para o conselho em Yofel" }
  recompensas:
    xp: 14000
    col: 4400

o_hipocampo:
  nome: "O Hipocampo"
  descricao: "Dizem que a sala do Wythege enche de água enquanto a luta dura. Ninguém quer ficar para conferir."
  tipo: historia
  party: true
  npc: visconde_yofel
  requisitos:
    quest: os_cavaleiros_caidos
  etapas:
    - { tipo: chefe, alvo: wythege, texto: "Derrote Wythege the Hippocampus" }
  recompensas:
    xp: 18000
    col: 8000
    itens: ["GOLDEN_APPLE 8"]

# ---------------------------------------------------------------- fora da história

a_capa_do_barqueiro:
  nome: "A capa do barqueiro"
  descricao: "O construtor costura capas com as escamas que os caranguejos e os afogados soltam."
  tipo: historia
  npc: construtor_rovia
  requisitos:
    quest: canais_infestados
  etapas:
    - { tipo: coletar, item: PRISMARINE_SHARD, quantidade: 16, texto: "Consiga 16 escamas" }
    - { tipo: entregar, item: PRISMARINE_SHARD, quantidade: 16, texto: "Leve as 16 escamas ao construtor" }
  recompensas:
    xp: 3000
    col: 1200
    itens: ["rpg:capa_do_barqueiro"]

a_garra_do_urso:
  nome: "A garra do urso"
  descricao: "O ancião de Usco já foi lenhador. Quem limpar a floresta dos ursos leva o machado dele."
  tipo: historia
  npc: anciao_usco
  requisitos:
    quest: caminho_para_usco
  etapas:
    - { tipo: matar, alvo: urso_da_floresta, quantidade: 25, texto: "Derrote 25 Ursos da Floresta" }
    - { tipo: falar, npc: anciao_usco, texto: "Volte ao ancião" }
  recompensas:
    xp: 5000
    col: 2000
    itens: ["rpg:machado_de_garra_de_urso"]

a_tartaruga_de_duas_cabecas:
  nome: "A tartaruga de duas cabeças"
  descricao: "O Biceps Archelon afunda os barcos dos pescadores de Usco."
  tipo: historia
  party: true
  npc: anciao_usco
  requisitos:
    quest: caminho_para_usco
    level: 34
  etapas:
    - { tipo: chefe, alvo: biceps_archelon, texto: "Derrote o Biceps Archelon no Lago da Caldeira" }
  recompensas:
    xp: 4800
    col: 2400

# ---------------------------------------------------------------- diárias

ronda_dos_canais:
  nome: "Ronda dos canais"
  descricao: "Mantenha os canais em volta de Rovia navegáveis. Vale para a party inteira."
  tipo: diaria
  party: true
  npc: guarda_rovia
  requisitos:
    quest: chegada_a_rovia
  etapas:
    - { tipo: matar, quantidade: 30, texto: "Derrote 30 monstros" }
  recompensas:
    xp: 1200
    col: 750

peixe_para_usco:
  nome: "Peixe para Usco"
  descricao: "Com a tartaruga no lago, ninguém pesca. A vila aceita o peixe que os ursos largam."
  tipo: diaria
  npc: anciao_usco
  requisitos:
    quest: caminho_para_usco
  etapas:
    - { tipo: entregar, item: COD, quantidade: 12, texto: "Leve 12 peixes ao ancião" }
  recompensas:
    xp: 1000
    col: 800

# ---------------------------------------------------------------- semanal

limpeza_do_labirinto_4:
  nome: "Limpeza da torre submersa"
  descricao: "Quanto menos Elfos Caídos na torre, mais gente chega viva à sala do Hipocampo."
  tipo: semanal
  party: true
  npc: visconde_yofel
  requisitos:
    quest: a_torre_submersa
  etapas:
    - { tipo: matar, alvo: guerreiro_caido, quantidade: 50, texto: "Derrote 50 Fallen Elven Warriors" }
    - { tipo: matar, alvo: cavaleiro_caido, quantidade: 15, texto: "Derrote 15 Fallen Elven Knights" }
  recompensas:
    xp: 12000
    col: 7000
    itens: ["IRON_INGOT 20"]
""")
