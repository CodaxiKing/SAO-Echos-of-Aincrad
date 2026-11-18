#!/usr/bin/env python3
"""Monta o pacote do andar 2 em conteudo/andar2/plugins.

Os pacotes somam: o do andar 2 traz tudo do andar 1 mais o que é do andar 2, porque items.yml,
spawns.yml, shops.yml e os outros são um arquivo só por plugin. Este script copia os arquivos do
andar 1 e acrescenta os blocos abaixo. Os chefes e as quests têm um arquivo por andar.

Uso:  python conteudo/tools/montar_andar2.py
"""
import os

AQUI = os.path.dirname(os.path.abspath(__file__))
A1 = os.path.join(AQUI, "..", "andar1", "plugins")
A2 = os.path.join(AQUI, "..", "andar2", "plugins")


def ler(caminho):
    with open(os.path.join(A1, caminho), encoding="utf-8") as f:
        return f.read()


def gravar(caminho, texto):
    destino = os.path.join(A2, caminho)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    with open(destino, "w", encoding="utf-8", newline="\n") as f:
        f.write(texto)
    print(caminho)


def titulo(texto):
    """O cabeçalho dos arquivos copiados passa a falar dos dois andares."""
    return texto.replace("— ANDAR 1", "— ANDARES 1 e 2", 1).replace("conteudo/andar1/README.md", "conteudo/andar2/README.md")


# ---------------------------------------------------------------- AincradRPG/spawns.yml
SPAWNS = """
  # ============================================================ ANDAR 2

  # ---------------------------------------------------------- Planalto de Urbus (level 10 a 12)
  # As mesas de pedra em volta de Urbus. Bois por toda parte; à noite, as vespas do vento.
  planalto_urbus:
    maximo: 16
    por_vez: 2
    raio: 40
    distancia_minima: 10
    sumir_longe: 72
    spawn_natural: false
    mobs:
      - mob: COW
        id: boi_tremulo
        nome: "<white>Trembling Ox"
        peso: 5
        level: "10-11"
        drops:
          - { item: BEEF, chance: 0.9, quantidade: "1-2" }
          - { item: LEATHER, chance: 0.5 }
      - mob: SILVERFISH
        id: verme_dentado
        nome: "<white>Jagged Worm"
        peso: 3
        level: "11-12"
        drops:
          - { item: STRING, chance: 0.5 }
          - { item: FLINT, chance: 0.3 }
      - mob: PHANTOM
        id: vespa_do_vento
        nome: "<yellow>Windwasp"
        peso: 3
        level: "11-12"
        periodo: noite
        drops:
          - { item: PHANTOM_MEMBRANE, chance: 0.6 }
          - { item: FEATHER, chance: 0.4, quantidade: "1-2" }

  # ---------------------------------------------------------- Pradaria de Marome (level 12 a 14)
  # Entre Urbus e a vila de Marome. O Bullbous Bow, chefe de campo, pasta aqui.
  pradaria_marome:
    maximo: 14
    por_vez: 2
    raio: 40
    distancia_minima: 12
    sumir_longe: 72
    spawn_natural: false
    mobs:
      - mob: COW
        id: vaca_tremula
        nome: "<white>Trembling Cow"
        peso: 4
        level: "12-13"
        drops:
          - { item: BEEF, quantidade: "1-3" }
          - { item: LEATHER, chance: 0.6 }
      - mob: WOLF
        id: lobo_da_pradaria
        nome: "<white>Lobo da Pradaria"
        agressivo: true
        peso: 3
        level: "12-14"
        drops:
          - { item: BONE, chance: 0.7, quantidade: "1-2" }
          - { item: LEATHER, chance: 0.5 }
      - mob: SKELETON
        id: arqueiro_errante
        nome: "<white>Arqueiro Errante"
        peso: 3
        level: "13-14"
        periodo: noite
        drops:
          - { item: ARROW, chance: 0.8, quantidade: "2-5" }
          - { item: BONE, chance: 0.6 }

  # ---------------------------------------------------------- Desfiladeiro de Taran (level 14 a 16)
  # O caminho de pedra até Taran, a vila ao pé do labirinto. Os primeiros Taurus aparecem aqui.
  desfiladeiro_taran:
    maximo: 14
    por_vez: 2
    raio: 36
    distancia_minima: 12
    sumir_longe: 72
    spawn_natural: false
    mobs:
      - mob: PIGLIN_BRUTE
        id: taurus_batedor
        nome: "<white>Taurus Scout"
        equipamento: { mao: STONE_AXE }
        peso: 4
        level: "14-16"
        drops:
          - { item: LEATHER, chance: 0.6, quantidade: "1-2" }
          - { item: IRON_NUGGET, chance: 0.6, quantidade: "2-4" }
      - mob: HUSK
        id: andarilho_do_desfiladeiro
        nome: "<white>Andarilho do Desfiladeiro"
        peso: 3
        level: "14-15"
        drops:
          - { item: SAND, chance: 0.4 }
          - { item: IRON_NUGGET, chance: 0.5, quantidade: "1-3" }
      - mob: SPIDER
        id: aranha_das_pedras
        nome: "<white>Aranha das Pedras"
        peso: 3
        level: "15-16"
        periodo: noite
        drops:
          - { item: STRING, chance: 0.8, quantidade: "1-3" }
          - { item: SPIDER_EYE, chance: 0.3 }

  # ---------------------------------------------------------- Labirinto do andar 2, andares de baixo (level 16 a 18)
  labirinto_andar2:
    maximo: 18
    por_vez: 2
    raio: 24
    distancia_minima: 8
    sumir_longe: 56
    spawn_natural: false
    mobs:
      - mob: PIGLIN_BRUTE
        id: taurus_golpeador
        nome: "<white>Lesser Taurus Striker"
        equipamento: { mao: IRON_AXE }
        peso: 5
        level: "16-18"
        drops:
          - { item: IRON_INGOT, chance: 0.4, quantidade: "1-2" }
          - { item: LEATHER, chance: 0.6, quantidade: "1-2" }
          - { item: GOLD_NUGGET, chance: 0.4, quantidade: "1-3" }
      - mob: PIGLIN_BRUTE
        id: taurus_guarda_de_ferro
        nome: "<yellow>Taurus Ironguard"
        equipamento: { mao: IRON_SWORD, mao_secundaria: SHIELD, cabeca: IRON_HELMET }
        peso: 2
        level: "17-18"
        drops:
          - { item: IRON_INGOT, chance: 0.6, quantidade: "1-3" }
          - { item: "rpg:machado_taurino", chance: 0.02 }

  # ---------------------------------------------------------- Labirinto do andar 2, andares de cima (level 18 a 19)
  labirinto_andar2_topo:
    maximo: 14
    por_vez: 2
    raio: 24
    distancia_minima: 8
    sumir_longe: 56
    spawn_natural: false
    mobs:
      - mob: PIGLIN_BRUTE
        id: taurus_guarda_de_ferro
        nome: "<yellow>Taurus Ironguard"
        equipamento: { mao: IRON_SWORD, mao_secundaria: SHIELD, cabeca: IRON_HELMET }
        peso: 4
        level: "18-19"
        drops:
          - { item: IRON_INGOT, chance: 0.6, quantidade: "1-3" }
          - { item: "rpg:machado_taurino", chance: 0.03 }
      - mob: HOGLIN
        id: taurus_pisoteador
        nome: "<yellow>Taurus Trampler"
        peso: 3
        level: "18-19"
        drops:
          - { item: LEATHER, quantidade: "1-3" }
          - { item: GOLD_INGOT, chance: 0.15 }
"""
gravar("AincradRPG/spawns.yml", titulo(ler("AincradRPG/spawns.yml")).rstrip("\n") + "\n" + SPAWNS)

# ---------------------------------------------------------------- AincradRPG/mobs.yml
mobs = titulo(ler("AincradRPG/mobs.yml"))
ancora = "  labirinto_andar1_topo: { min: 8, max: 9 }\n"
assert ancora in mobs
mobs = mobs.replace(ancora, ancora + """  planalto_urbus: { min: 10, max: 12 }
  pradaria_marome: { min: 12, max: 14 }
  desfiladeiro_taran: { min: 14, max: 16 }
  labirinto_andar2: { min: 16, max: 18 }
  labirinto_andar2_topo: { min: 18, max: 19 }
""")
gravar("AincradRPG/mobs.yml", mobs)

# ---------------------------------------------------------------- AincradRPG/items.yml
ITEMS = """
  # ============================================================ ANDAR 2 (level 10 a 20)

  espada_de_aco:
    material: IRON_SWORD
    nome: "Steel Sword"
    level_minimo: 10
    slot: MAINHAND
    bonus:
      dano: [5, 7]
      critico: [2, 4]
    drop: { chance: 0.008, level_mob: [10, 16] }
  # A recompensa da quest "A rocha do eremita".
  lamina_do_eremita:
    material: IRON_SWORD
    nome: "Lâmina do Eremita"
    level_minimo: 13
    slot: MAINHAND
    bonus:
      dano: [6, 8]
      velocidade_ataque: [4, 8]
      aparar: [3, 6]
  adaga_taurina:
    material: IRON_SWORD
    nome: "Adaga de Chifre"
    level_minimo: 11
    slot: MAINHAND
    bonus:
      dano: [3, 5]
      velocidade_ataque: [10, 16]
      critico: [4, 8]
    drop: { chance: 0.006, level_mob: [11, 17] }
  florete_de_prata:
    material: IRON_SWORD
    nome: "Florete de Prata"
    level_minimo: 12
    slot: MAINHAND
    bonus:
      dano: [4, 6]
      velocidade_ataque: [8, 14]
      esquiva: [2, 4]
    drop: { chance: 0.004, level_mob: [12, 19] }
  # Só cai dos Taurus Ironguards do labirinto (spawns.yml).
  machado_taurino:
    material: IRON_AXE
    nome: "Machado Taurino"
    level_minimo: 16
    slot: MAINHAND
    bonus:
      dano: [8, 11]
      dano_critico: [8, 14]
  lanca_de_guerra:
    material: TRIDENT
    nome: "Lança de Guerra"
    level_minimo: 13
    slot: MAINHAND
    bonus:
      dano: [6, 8]
      aparar: [3, 6]
    drop: { chance: 0.005, level_mob: [13, 19] }
  montante_de_aco:
    material: IRON_SWORD
    nome: "Montante de Aço"
    level_minimo: 14
    slot: MAINHAND
    bonus:
      dano: [8, 10]
    drop: { chance: 0.004, level_mob: [14, 19] }
  escudo_de_aco:
    material: SHIELD
    nome: "Escudo de Aço"
    level_minimo: 12
    slot: OFFHAND
    bonus:
      armadura: [2, 3]
      aparar: [6, 10]
    drop: { chance: 0.005, level_mob: [12, 19] }
  elmo_de_ferro:
    material: IRON_HELMET
    nome: "Elmo de Ferro"
    level_minimo: 11
    slot: HEAD
    bonus:
      vida: [2, 3]
      armadura: [2, 3]
    drop: { chance: 0.006, level_mob: [11, 17] }
  peitoral_de_ferro:
    material: IRON_CHESTPLATE
    nome: "Peitoral de Ferro"
    level_minimo: 13
    slot: CHEST
    bonus:
      vida: [5, 8]
      armadura: [4, 6]
    drop: { chance: 0.004, level_mob: [13, 19] }
  grevas_de_ferro:
    material: IRON_LEGGINGS
    nome: "Grevas de Ferro"
    level_minimo: 12
    slot: LEGS
    bonus:
      vida: [3, 5]
      armadura: [3, 4]
    drop: { chance: 0.005, level_mob: [12, 18] }
  botas_de_ferro:
    material: IRON_BOOTS
    nome: "Botas de Ferro"
    level_minimo: 11
    slot: FEET
    bonus:
      armadura: [1, 2]
      velocidade: [1, 3]
    drop: { chance: 0.006, level_mob: [11, 17] }
  couraca_de_couro_de_boi:
    material: LEATHER_CHESTPLATE
    nome: "Couraça de Couro de Boi"
    level_minimo: 10
    slot: CHEST
    bonus:
      vida: [4, 6]
      armadura: [1, 2]
      esquiva: [3, 5]
    drop: { chance: 0.006, level_mob: [10, 16] }

  # O bônus de Last Attack do Asterius (bosses/andar2.yml). Não cai de mob nenhum.
  coroa_do_rei_taurus:
    material: GOLDEN_HELMET
    nome: "Crown of the Taurus King"
    level_minimo: 18
    slot: HEAD
    bonus:
      vida: [6, 8]
      armadura: [3, 4]
      dano: [1, 2]
      resistencia: [1, 2]
"""
gravar("AincradRPG/items.yml", titulo(ler("AincradRPG/items.yml")).rstrip("\n") + "\n" + ITEMS)

# ---------------------------------------------------------------- AincradRPG/rare-mobs.yml
RARO = """
  # O Boi Dourado do planalto de Urbus: foge, e solta ouro a quem o alcançar.
  boi_dourado:
    mob: COW
    nome: "<gold>Golden Ox"
    regiao: planalto_urbus
    chance: 0.003
    level: 11
    foge: true
    drop: GOLD_INGOT
    drop_quantidade: 3
    some_em_segundos: 120
"""
gravar("AincradRPG/rare-mobs.yml", titulo(ler("AincradRPG/rare-mobs.yml")).rstrip("\n") + "\n" + RARO)

# ---------------------------------------------------------------- AincradEconomy/shops.yml
LOJAS = """
  # ============================================================ ANDAR 2

  # ---------------------------------------------------------- Urbus
  mercador_urbus:
    titulo: "<dark_gray>Mercador — Urbus"
    itens:
      - { item: BREAD, nome: "Pão", quantidade: 4, compra: 20 }
      - { item: COOKED_BEEF, nome: "Bife de Boi do Planalto", quantidade: 2, compra: 40 }
      - { item: TORCH, nome: "Tocha", quantidade: 8, compra: 16 }
      - { item: ARROW, nome: "Flecha", quantidade: 16, compra: 40 }
      - { item: BEEF, nome: "Carne de Boi", venda: 5 }
      - { item: LEATHER, nome: "Couro", venda: 6 }
      - { item: PHANTOM_MEMBRANE, nome: "Asa de Vespa", venda: 12 }
      - { item: FEATHER, nome: "Pena", venda: 2 }
  ferreiro_urbus:
    titulo: "<dark_gray>Ferreiro — Urbus"
    itens:
      - { item: "rpg:espada_de_aco:comum", compra: 3200 }
      - { item: "rpg:adaga_taurina:comum", compra: 2900 }
      - { item: "rpg:florete_de_prata:comum", compra: 3800 }
      - { item: "rpg:lanca_de_guerra:comum", compra: 3900 }
      - { item: "rpg:escudo_de_aco:comum", compra: 2600 }
      - { item: IRON_INGOT, nome: "Barra de Ferro", compra: 60, venda: 15 }
      - { item: GOLD_NUGGET, nome: "Pepita de Ouro", venda: 4 }
      - { item: GOLD_INGOT, nome: "Barra de Ouro", venda: 40 }
  armeiro_urbus:
    titulo: "<dark_gray>Armeiro — Urbus"
    itens:
      - { item: "rpg:couraca_de_couro_de_boi:comum", compra: 2400 }
      - { item: "rpg:elmo_de_ferro:comum", compra: 2600 }
      - { item: "rpg:botas_de_ferro:comum", compra: 2400 }

  # ---------------------------------------------------------- Taran
  ferreiro_taran:
    titulo: "<dark_gray>Ferreiro — Taran"
    itens:
      - { item: "rpg:montante_de_aco:comum", compra: 5200 }
      - { item: "rpg:peitoral_de_ferro:comum", compra: 5600 }
      - { item: "rpg:grevas_de_ferro:comum", compra: 4200 }
      - { item: IRON_INGOT, nome: "Barra de Ferro", compra: 60, venda: 15 }
      - { item: IRON_NUGGET, nome: "Pepita de Ferro", venda: 2 }
  mercador_taran:
    titulo: "<dark_gray>Mercador — Taran"
    itens:
      - { item: BREAD, nome: "Pão", quantidade: 4, compra: 20 }
      - { item: COOKED_BEEF, nome: "Bife de Boi do Planalto", quantidade: 2, compra: 40 }
      - { item: TORCH, nome: "Tocha", quantidade: 8, compra: 16 }
      - { item: STRING, nome: "Linha", venda: 3 }
      - { item: SPIDER_EYE, nome: "Olho de Aranha", venda: 8 }
"""
gravar("AincradEconomy/shops.yml", titulo(ler("AincradEconomy/shops.yml")).rstrip("\n") + "\n" + LOJAS)

# ---------------------------------------------------------------- AincradFloors/floors.yml
floors = ler("AincradFloors/floors.yml").replace("#  Andares — ANDAR 1 pronto, 2 e 3 como reserva", "#  Andares — ANDARES 1 e 2 prontos, o 3 como reserva")
velho = """  2:
    nome: "Andar 2 — Urbus"
    servidor: ""
    tipo: regiao
    regiao: andar2
    chegada: ""
    cidade: urbus
"""
assert velho in floors
floors = floors.replace(velho, velho + "    vilas: [marome, taran]\n")
gravar("AincradFloors/floors.yml", floors)

# ---------------------------------------------------------------- AincradBosses
gravar("AincradBosses/bosses/andar1.yml", ler("AincradBosses/bosses/andar1.yml"))
gravar("AincradBosses/bosses/andar2.yml", """# ============================================================
#  Chefes do ANDAR 2
#  Pacote de conteúdo: copie para plugins/AincradBosses/bosses/andar2.yml.
#
#  Asterius the Taurus King, o chefe do andar, e o Bullbous Bow, o chefe de campo da pradaria.
#  Como no anime, a luta começa contra o General e o Coronel; o Rei entra depois.
#  Pensado para um grupo de 8 a 12 jogadores de level 18 a 20.
# ============================================================
asterius:
  andar: 2
  nome: "Asterius the Taurus King"
  level: 20
  mob: RAVAGER
  vida: 1600
  dano: 13
  armadura: 9
  velocidade: 0.26
  resistencia_empurrao: 0.9
  escala: 1.3
  intro_segundos: 6
  tempo_limite_segundos: 1200
  anti_cristal: true
  reaparece_horas: 8
  fases:
    - vida: 100
      nome: "O General e o Coronel"
      habilidades:
        - { tipo: lacaios, recarga: 60, mob: IRON_GOLEM, quantidade: 1, level: 18, nome: "Baran the General Taurus" }
        - { tipo: lacaios, recarga: 45, mob: ZOMBIE, quantidade: 2, level: 17, nome: "Nato the Colonel Taurus" }
        - { tipo: area, recarga: 12, dano: 12, raio: 4.5, aviso_ticks: 30 }
    - vida: 70
      nome: "O Rei desce do trono"
      mensagem: "<red><chefe> desce do trono e o chão treme!"
      habilidades:
        - { tipo: area, recarga: 10, dano: 14, raio: 5, aviso_ticks: 28 }
        - { tipo: investida, recarga: 12, dano: 15, raio: 3.5 }
        - { tipo: lacaios, recarga: 50, mob: ZOMBIE, quantidade: 2, level: 18, nome: "Nato the Colonel Taurus" }
    - vida: 40
      nome: "Sopro do Rei"
      mensagem: "<dark_red><chefe> enche o peito: o sopro dele paralisa!"
      dano_x: 1.15
      habilidades:
        - { tipo: sopro, recarga: 9, dano: 12, raio: 8, aviso_ticks: 30, segundos: 4 }
        - { tipo: investida, recarga: 10, dano: 16, raio: 3.5 }
        - { tipo: area, recarga: 9, dano: 15, raio: 5, aviso_ticks: 25 }
    - vida: 15
      nome: "Fúria do touro"
      mensagem: "<dark_red><chefe> baixa os chifres!"
      dano_x: 1.3
      velocidade_x: 1.3
      habilidades:
        - { tipo: investida, recarga: 7, dano: 18, raio: 4 }
        - { tipo: salto, recarga: 11, dano: 17, raio: 5 }
        - { tipo: furia, recarga: 25, segundos: 8, multiplicador: 1.5, nome: "<dark_red>Os olhos de <chefe> ardem em vermelho!" }
  recompensas:
    dano_minimo_pct: 4
    xp: 2400
    col: 1500
    # Só para quem deu o golpe final: a coroa do Rei.
    last_attack: "rpg:coroa_do_rei_taurus:lendario"
    itens:
      - { item: IRON_INGOT, quantidade: 24 }
      - { item: GOLD_INGOT, quantidade: 8 }
      - { item: GOLDEN_APPLE, quantidade: 6 }
      - { item: "rpg:machado_taurino:raro", quantidade: 1 }
      - { item: "rpg:peitoral_de_ferro:raro", quantidade: 1 }
      - { item: "rpg:espada_de_aco:epico", quantidade: 1 }

# Chefe de campo: pasta na Pradaria de Marome e volta 8 horas depois de derrotado (roaming.yml).
bullbous_bow:
  andar: 2
  nome: "Bullbous Bow"
  level: 14
  mob: RAVAGER
  vida: 520
  dano: 10
  armadura: 5
  velocidade: 0.3
  resistencia_empurrao: 0.7
  escala: 1.1
  intro_segundos: 2
  anti_cristal: false
  fases:
    - vida: 100
      nome: "Pastando"
      habilidades:
        - { tipo: investida, recarga: 10, dano: 11, raio: 3 }
    - vida: 45
      nome: "Estouro"
      mensagem: "<red><chefe> escava o chão e dispara!"
      velocidade_x: 1.25
      habilidades:
        - { tipo: investida, recarga: 7, dano: 13, raio: 3 }
        - { tipo: area, recarga: 12, dano: 11, raio: 4, aviso_ticks: 25 }
  recompensas:
    dano_minimo_pct: 5
    xp: 700
    col: 400
    itens:
      - { item: LEATHER, quantidade: 10 }
      - { item: BEEF, quantidade: 12 }
      - { item: "rpg:couraca_de_couro_de_boi:raro", quantidade: 1 }
""")
roaming = titulo(ler("AincradBosses/roaming.yml"))
ancora = "  lobo_alfa:\n    regiao: floresta_horunka\n    intervalo_horas: 6\n"
assert ancora in roaming
roaming = roaming.replace(ancora, ancora + "  # O touro da Pradaria de Marome (bosses/andar2.yml). Volta 8 horas depois de derrotado.\n  bullbous_bow:\n    regiao: pradaria_marome\n    intervalo_horas: 8\n")
gravar("AincradBosses/roaming.yml", roaming)

# ---------------------------------------------------------------- AincradQuests
gravar("AincradQuests/quests/andar1.yml", ler("AincradQuests/quests/andar1.yml"))
gravar("AincradQuests/quests/andar2.yml", """# ============================================================
#  Quests do ANDAR 2
#  Pacote de conteúdo: copie para plugins/AincradQuests/quests/andar2.yml
#  (junto com o quests/andar1.yml; a história continua de onde o andar 1 parou).
#
#  Urbus → Planalto → Marome → Desfiladeiro → Taran → Labirinto → Asterius
#
#  NPCs que precisam existir (/aincrad npc criar <id> <nome>):
#    guarda_urbus, curtidor_urbus      (Urbus)
#    cozinheira_marome, eremita        (Marome)
#    capitao_taran                     (Taran)
#  Regiões usadas em "chegar": urbus, marome, taran, labirinto_andar2
# ============================================================

# ---------------------------------------------------------------- história

chegada_a_urbus:
  nome: "A cidade na mesa de pedra"
  descricao: "O segundo andar abriu. Urbus fica no alto de uma mesa de pedra, logo depois do portal."
  tipo: historia
  npc: guarda_urbus
  requisitos:
    quest: o_senhor_dos_kobolds
    andar: 2
  etapas:
    - { tipo: chegar, regiao: urbus, texto: "Chegue a Urbus" }
    - { tipo: falar, npc: guarda_urbus, texto: "Apresente-se ao guarda do portão" }
  recompensas:
    xp: 1000
    col: 300

bois_do_planalto:
  nome: "Os bois do planalto"
  descricao: "Os Trembling Oxen derrubam as cercas e pisam a plantação."
  tipo: historia
  npc: guarda_urbus
  requisitos:
    quest: chegada_a_urbus
    level: 10
  etapas:
    - { tipo: matar, alvo: boi_tremulo, quantidade: 10, texto: "Derrote 10 Trembling Oxen no Planalto de Urbus" }
  recompensas:
    xp: 1200
    col: 450

couro_para_urbus:
  nome: "Couro para Urbus"
  descricao: "O curtidor de Urbus faz uma couraça para quem trouxer couro de boi suficiente."
  tipo: historia
  npc: curtidor_urbus
  requisitos:
    quest: bois_do_planalto
  etapas:
    - { tipo: coletar, item: LEATHER, quantidade: 12, texto: "Consiga 12 couros" }
    - { tipo: entregar, item: LEATHER, quantidade: 12, texto: "Leve os 12 couros ao curtidor" }
  recompensas:
    xp: 800
    col: 300
    itens: ["rpg:couraca_de_couro_de_boi"]

caminho_para_marome:
  nome: "O caminho para Marome"
  descricao: "A vila de Marome fica do outro lado da pradaria. Dizem que um eremita mora lá perto."
  tipo: historia
  npc: guarda_urbus
  requisitos:
    quest: bois_do_planalto
    level: 12
  etapas:
    - { tipo: chegar, regiao: marome, texto: "Atravesse a pradaria até Marome" }
    - { tipo: falar, npc: cozinheira_marome, texto: "Fale com a cozinheira da vila" }
  recompensas:
    xp: 1400
    col: 500

# A quest do eremita: no anime ele manda quebrar uma rocha com as mãos. Aqui, vermes de pedra.
a_rocha_do_eremita:
  nome: "A rocha do eremita"
  descricao: "O eremita só ensina quem provar que não desiste: limpe a pedreira dos Jagged Worms."
  tipo: historia
  npc: eremita
  requisitos:
    quest: caminho_para_marome
  etapas:
    - { tipo: falar, npc: eremita, texto: "Ouça o eremita" }
    - { tipo: matar, alvo: verme_dentado, quantidade: 25, texto: "Derrote 25 Jagged Worms" }
    - { tipo: falar, npc: eremita, texto: "Volte ao eremita" }
  recompensas:
    xp: 1800
    col: 600
    itens: ["rpg:lamina_do_eremita"]

estrada_para_taran:
  nome: "O desfiladeiro"
  descricao: "A torre do labirinto fica além do desfiladeiro, perto de Taran. Os Taurus guardam a passagem."
  tipo: historia
  npc: cozinheira_marome
  requisitos:
    quest: caminho_para_marome
    level: 14
  etapas:
    - { tipo: matar, alvo: taurus_batedor, quantidade: 8, texto: "Derrote 8 Taurus Scouts no desfiladeiro" }
    - { tipo: chegar, regiao: taran, texto: "Chegue a Taran" }
    - { tipo: falar, npc: capitao_taran, texto: "Procure o capitão da linha de frente" }
  recompensas:
    xp: 1700
    col: 700

golpeadores_do_labirinto:
  nome: "Os golpeadores"
  descricao: "O capitão quer os andares de baixo da torre limpos antes da reunião."
  tipo: historia
  party: true
  npc: capitao_taran
  requisitos:
    quest: estrada_para_taran
    level: 16
  etapas:
    - { tipo: chegar, regiao: labirinto_andar2, texto: "Entre no labirinto" }
    - { tipo: matar, alvo: taurus_golpeador, quantidade: 15, texto: "Derrote 15 Lesser Taurus Strikers" }
    - { tipo: falar, npc: capitao_taran, texto: "Volte ao capitão" }
  recompensas:
    xp: 2200
    col: 1000
    itens: ["rpg:escudo_de_aco"]

a_guarda_do_rei:
  nome: "A guarda do Rei"
  descricao: "No topo da torre, os Ironguards fecham o caminho até a sala do trono."
  tipo: historia
  party: true
  npc: capitao_taran
  requisitos:
    quest: golpeadores_do_labirinto
    level: 18
  etapas:
    - { tipo: matar, alvo: taurus_guarda_de_ferro, quantidade: 12, texto: "Derrote 12 Taurus Ironguards" }
    - { tipo: falar, npc: capitao_taran, texto: "Volte para a reunião em Taran" }
  recompensas:
    xp: 2800
    col: 1300

o_rei_taurus:
  nome: "O Rei Taurus"
  descricao: "O General e o Coronel esperam na sala. O Rei, dizem, só desce do trono depois."
  tipo: historia
  party: true
  npc: capitao_taran
  requisitos:
    quest: a_guarda_do_rei
  etapas:
    - { tipo: chefe, alvo: asterius, texto: "Derrote Asterius the Taurus King" }
  recompensas:
    xp: 3500
    col: 2500
    itens: ["GOLDEN_APPLE 4"]

# ---------------------------------------------------------------- fora da história

o_touro_da_pradaria:
  nome: "O touro da pradaria"
  descricao: "O Bullbous Bow afugenta o gado e quem tenta atravessar a pradaria."
  tipo: historia
  party: true
  npc: cozinheira_marome
  requisitos:
    quest: caminho_para_marome
    level: 13
  etapas:
    - { tipo: chefe, alvo: bullbous_bow, texto: "Derrote o Bullbous Bow na Pradaria de Marome" }
  recompensas:
    xp: 1500
    col: 800

# ---------------------------------------------------------------- diárias

ronda_do_planalto:
  nome: "Ronda do planalto"
  descricao: "Mantenha o planalto limpo. Vale para a party inteira."
  tipo: diaria
  party: true
  npc: guarda_urbus
  requisitos:
    quest: chegada_a_urbus
  etapas:
    - { tipo: matar, quantidade: 20, texto: "Derrote 20 monstros" }
  recompensas:
    xp: 400
    col: 250

carne_para_marome:
  nome: "Carne para Marome"
  descricao: "A cozinheira precisa de carne de boi para o cozido da vila."
  tipo: diaria
  npc: cozinheira_marome
  requisitos:
    quest: caminho_para_marome
  etapas:
    - { tipo: entregar, item: BEEF, quantidade: 10, texto: "Leve 10 carnes de boi à cozinheira" }
  recompensas:
    xp: 350
    col: 280

# ---------------------------------------------------------------- semanal

limpeza_do_labirinto_2:
  nome: "Limpeza da torre dos Taurus"
  descricao: "Quanto menos Taurus na torre, mais gente chega viva à sala do trono."
  tipo: semanal
  party: true
  npc: capitao_taran
  requisitos:
    quest: golpeadores_do_labirinto
  etapas:
    - { tipo: matar, alvo: taurus_golpeador, quantidade: 50, texto: "Derrote 50 Lesser Taurus Strikers" }
    - { tipo: matar, alvo: taurus_guarda_de_ferro, quantidade: 15, texto: "Derrote 15 Taurus Ironguards" }
  recompensas:
    xp: 4000
    col: 2500
    itens: ["IRON_INGOT 12"]
""")
