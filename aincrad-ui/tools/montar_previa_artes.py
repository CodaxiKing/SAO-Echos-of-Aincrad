#!/usr/bin/env python3
"""Monta três prévias da interface usando as artes do resource pack (precisa do Pillow).

São montagens, não capturas do jogo: mostram como as telas ficam com as imagens de
src/main/resources/resourcepack. Servem para decidir o visual antes de mexer no plugin.

Uso:  python tools/montar_previa_artes.py
Saída: docs/previa/ui-menu.png, ui-combate.png e ui-janelas.png (na raiz do repositório)
"""
import os

from PIL import Image, ImageDraw, ImageFont

AQUI = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(AQUI, "..", "src", "main", "resources", "resourcepack", "assets", "aincrad")
ARTE = os.path.join(PACK, "textures", "ui")
SAIDA = os.path.join(AQUI, "..", "..", "docs", "previa")
W, H = 1600, 900

VERDE = (123, 210, 58)
AMARELO = (240, 200, 60)
VERMELHO = (229, 59, 75)
ESCURO = (60, 60, 60)
CINZA = (120, 126, 134)
BRANCO = (255, 255, 255)


def fonte(tamanho):
    return ImageFont.truetype(os.path.join(PACK, "font", "sao_ui.ttf"), tamanho)


def arte(nome, largura=None):
    im = Image.open(os.path.join(ARTE, nome + ".png")).convert("RGBA")
    if largura:
        im = im.resize((largura, max(1, round(im.height * largura / im.width))), Image.LANCZOS)
    return im


def pintar(im, cor):
    """Pinta uma arte branca numa cor, mantendo a transparência."""
    r, g, b, a = im.split()
    out = Image.new("RGBA", im.size, cor + (255,))
    out.putalpha(a)
    return out


def fundo():
    """Um cenário simples: céu no alto, chão embaixo, cantos escurecidos."""
    im = Image.new("RGBA", (W, H))
    px = im.load()
    for y in range(H):
        t = y / H
        if t < 0.62:
            c = (int(58 + 70 * t), int(96 + 80 * t), int(150 + 60 * t))
        else:
            u = (t - 0.62) / 0.38
            c = (int(70 - 30 * u), int(104 - 40 * u), int(74 - 30 * u))
        for x in range(W):
            px[x, y] = c + (255,)
    sombra = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(sombra)
    for i in range(90):
        d.rectangle([i, i, W - i, H - i], outline=(0, 0, 0, max(0, 70 - i)))
    im.alpha_composite(sombra)
    return im


def hud(tela, x, y, nome, vida, maxima, escala=0.62):
    """A moldura do anime com a barra em fatias, o nome e os números."""
    moldura = arte("hud/frame", round(744 * escala))
    tela.alpha_composite(moldura, (x, y))
    razao = vida / maxima
    cor = VERDE if razao > 0.5 else AMARELO if razao > 0.25 else VERMELHO
    cheias = round(20 * razao)
    for i in range(cheias):
        fatia = pintar(arte("hud/fill_%02d" % i, round(24 * escala)), cor)
        tela.alpha_composite(fatia, (x + round((236 + 24 * i) * escala), y))
    d = ImageDraw.Draw(tela)
    d.text((x + round(44 * escala), y + round(26 * escala)), nome, font=fonte(round(40 * escala)), fill=ESCURO)
    d.text((x + round(500 * escala), y + round(58 * escala)), "%d/%d  LV:%d" % (vida, maxima, 12), font=fonte(round(26 * escala)), fill=ESCURO)


def mini(tela, x, y, nome, razao, escala=0.62):
    """A barra menor de um colega de party."""
    cor = VERDE if razao > 0.5 else AMARELO if razao > 0.25 else VERMELHO
    tela.alpha_composite(barra_menor(razao, cor, round(248 * escala)), (x, y))
    ImageDraw.Draw(tela).text((x + round(258 * escala), y - 2), nome, font=fonte(18), fill=BRANCO)


def barra_menor(razao, cor, largura):
    """A barra menor montada no tamanho da arte e reduzida depois, para as fatias encostarem."""
    im = arte("hud/mini")
    for i in range(round(20 * max(0, min(1, razao)))):
        im.alpha_composite(pintar(arte("hud/mini_%02d" % i), cor), (4 + 12 * i, 0))
    return im.resize((largura, round(32 * largura / 248)), Image.LANCZOS)


def faixa(tela, caixa, cor):
    """Retângulo translúcido (o draw direto apagaria o que está embaixo)."""
    camada = Image.new("RGBA", tela.size, (0, 0, 0, 0))
    ImageDraw.Draw(camada).rectangle(caixa, fill=cor)
    tela.alpha_composite(camada)


def linha(tela, x, y, icone, texto, detalhe=None, foco=False, largura=250):
    """Uma linha de submenu ou de lista: fundo, ícone e texto."""
    tela.alpha_composite(arte("extra/row_bg_on" if foco else "extra/row_bg", largura), (x, y))
    alt = round(96 * largura / 368)
    tela.alpha_composite(arte(icone + ("_on" if foco and os.path.exists(os.path.join(ARTE, icone + "_on.png")) else ""), alt - 14), (x + 8, y + 7))
    d = ImageDraw.Draw(tela)
    d.text((x + alt + 6, y + alt // 2 - 13), texto, font=fonte(22), fill=BRANCO if foco else ESCURO)
    if detalhe:
        d.text((x + alt + 6, y + alt // 2 + 9), detalhe, font=fonte(14), fill=BRANCO if foco else CINZA)
    return alt


def janela(tela, x, y, titulo, linhas, largura=400):
    """A janela branca com título e texto."""
    tela.alpha_composite(arte("window_blank", largura), (x, y))
    d = ImageDraw.Draw(tela)
    f = fonte(30)
    d.text((x + largura / 2 - d.textlength(titulo, font=f) / 2, y + round(largura * 0.045)), titulo, font=f, fill=ESCURO)
    yy = y + round(largura * 0.22)
    for texto, tamanho, cor in linhas:
        f = fonte(tamanho)
        d.text((x + largura / 2 - d.textlength(texto, font=f) / 2, yy), texto, font=f, fill=cor)
        yy += tamanho + 12
    return round(600 * largura / 512)


def botao(tela, cx, cy, nome, tamanho, legenda=None, foco=False):
    tela.alpha_composite(arte("buttons/" + nome + ("_on" if foco else ""), tamanho), (cx - tamanho // 2, cy - tamanho // 2))
    if legenda:
        d = ImageDraw.Draw(tela)
        f = fonte(16)
        d.text((cx - d.textlength(legenda, font=f) / 2, cy + tamanho // 2 + 4), legenda, font=f, fill=ESCURO)


def rodape(tela, texto):
    faixa(tela, [0, H - 34, W, H], (0, 0, 0, 150))
    d = ImageDraw.Draw(tela)
    d.text((16, H - 28), texto, font=fonte(18), fill=(220, 226, 232))


def salvar(tela, nome):
    os.makedirs(SAIDA, exist_ok=True)
    tela.convert("RGB").save(os.path.join(SAIDA, nome))
    print(os.path.join("docs", "previa", nome))


# ---------------------------------------------------------------- 1. o menu
tela = fundo()
hud(tela, 24, 22, "Kirito", 14, 20)
mini(tela, 52, 92, "Asuna", 0.9)
mini(tela, 52, 120, "Klein", 0.3)
painel = arte("profile_panel", 400)
tela.alpha_composite(painel, (150, 215))
d = ImageDraw.Draw(tela)
d.text((350 - d.textlength("Kirito", font=fonte(28)) / 2, 228), "Kirito", font=fonte(28), fill=ESCURO)
tela.alpha_composite(arte("extra/arrow_right", 44), (566, 428))
icones = ["profile", "party", "message", "map", "config"]
for i, nome in enumerate(icones):
    tela.alpha_composite(arte("icons/" + nome + ("_on" if i == 0 else ""), 100), (640, 190 + i * 112))
tela.alpha_composite(arte("extra/arrow_right", 44), (756, 218))
entradas = [("under_icons/items", "Itens"), ("under_icons/equipment", "Equipamento"), ("under_icons/skills", "Skills"),
            ("under_icons/player", "Status"), ("under_icons/player", "Títulos"), ("under_icons/quests", "Conquistas")]
for i, (icone, texto) in enumerate(entradas):
    linha(tela, 820, 200 + i * 76, icone, texto, foco=(i == 2), largura=260)
tela.alpha_composite(arte("extra/cursor", 34), (1050, 378))
rodape(tela, "1. Menu principal — montagem com as artes do pack (não é captura do jogo)")
salvar(tela, "ui-menu.png")

# ---------------------------------------------------------------- 2. combate
tela = fundo()
hud(tela, 24, 22, "Kirito", 9, 20)
mini(tela, 52, 92, "Asuna", 0.9)
mini(tela, 52, 120, "Klein", 0.2)
d = ImageDraw.Draw(tela)
# alvo: a placa com o nome e quatro barras, na cor do cursor
nome = "Illfang the Kobold Lord"
tela.alpha_composite(arte("extra/row_bg").resize((360, 50), Image.LANCZOS), (620, 96))
d = ImageDraw.Draw(tela)
d.rectangle([636, 111, 656, 131], fill=(176, 19, 37))
d.text((668, 107), nome, font=fonte(24), fill=ESCURO)
resto = 0.62 * 4
for b in range(4):
    parte = max(0, min(1, resto - (3 - b)))
    tela.alpha_composite(barra_menor(parte, (200, 30, 50), 360), (620, 156 + b * 30))
# bússola e relógio
faixa(tela, [590, 28, 1010, 62], (14, 18, 22, 120))
d = ImageDraw.Draw(tela)
for texto, x, cor in (("O", 640, BRANCO), ("N", 800, (245, 166, 35)), ("L", 960, BRANCO)):
    d.text((x - 8, 30), texto, font=fonte(28), fill=cor)
d.rectangle([799, 26, 801, 34], fill=BRANCO)
d.rectangle([742, 58, 754, 62], fill=(91, 227, 125))
d.text((1486, 26), "21:47", font=fonte(30), fill=BRANCO)
d.text((1418, 62), "primavera, dia 12", font=fonte(18), fill=(216, 222, 228))
# avisos
linha(tela, 1296, 110, "under_icons/items", "Item recebido", "Anneal Blade", largura=280)
linha(tela, 1296, 190, "under_icons/skills", "Level", "12", largura=280)
# recargas: os ícones de skill, com a parte que falta escurecida
skills = [("skill_1", "Slant", 0.0), ("skill_2", "Vertical", 0.45), ("skill_4", "Horizontal", 1.0)]
for i, (icone, texto, falta) in enumerate(skills):
    x = 1296 + i * 96
    im = arte("skills/" + icone, 84)
    if falta > 0:
        veu = Image.new("RGBA", im.size, (0, 0, 0, 0))
        ImageDraw.Draw(veu).pieslice([0, 0, 83, 83], -90, -90 + 360 * falta, fill=(0, 0, 0, 150))
        veu.putalpha(Image.composite(veu.split()[3], Image.new("L", im.size, 0), im.split()[3]))
        im.alpha_composite(veu)
    tela.alpha_composite(im, (x, 740))
    d.text((x + 42 - d.textlength(texto, font=fonte(16)) / 2, 828), texto, font=fonte(16), fill=BRANCO)
rodape(tela, "2. Combate — a moldura do anime, o alvo, os avisos e as recargas com ícone (montagem com as artes do pack)")
salvar(tela, "ui-combate.png")

# ---------------------------------------------------------------- 3. as janelas
tela = fundo()
hud(tela, 24, 22, "Kirito", 20, 20)
# a confirmação usa a janela de alerta do anime (ui/window_alert, 512x366)
aw = 480
ah = round(366 * aw / 512)
ax, ay = 230, 250
tela.alpha_composite(arte("window_alert", aw), (ax, ay))
d = ImageDraw.Draw(tela)
f = fonte(34)
d.text((ax + aw / 2 - d.textlength("Confirmar", font=f) / 2, ay + round(ah * 0.07)), "Confirmar", font=f, fill=ESCURO)
for i, texto in enumerate(["Asuna desafiou você para um duelo.", "Modo: primeiro golpe"]):
    f = fonte(22 if i == 0 else 18)
    d.text((ax + aw / 2 - d.textlength(texto, font=f) / 2, ay + round(ah * 0.36) + i * 34), texto, font=f, fill=ESCURO if i == 0 else CINZA)
botao(tela, ax + round(aw * 0.26), ay + round(ah * 0.815), "yes", 84, foco=True)
botao(tela, ax + round(aw * 0.74), ay + round(ah * 0.815), "no", 84)
tela.alpha_composite(arte("extra/cursor", 34), (ax + round(aw * 0.26) + 8, ay + round(ah * 0.815) + 6))
alt = janela(tela, 890, 170, "Quantas vezes?", [("", 30, ESCURO), ("12", 76, (40, 150, 200)), ("de 1 a 256", 20, CINZA)], largura=440)
passos = [("minus", "-100"), ("minus", "-10"), ("minus", "-1"), ("plus", "+1"), ("plus", "+10"), ("plus", "+100")]
xs = [40, 94, 148, 292, 346, 400]
for (nome, legenda), x in zip(passos, xs):
    botao(tela, 890 + x, 170 + alt - 96, nome, 50, legenda)
botao(tela, 890 + 220, 170 + alt - 96, "yes", 72, "Confirmar")
# as faixas do anime, em tamanho reduzido
for i, nome in enumerate(["warning", "immortal", "duel"]):
    for metade in (0, 1):
        tela.alpha_composite(arte("banner/%s_%d" % (nome, metade), 110), (300 + i * 360 + metade * 110, 720))
rodape(tela, "3. Janelas — a confirmação na janela de alerta do anime, a quantidade e as faixas (montagem com as artes do pack)")
salvar(tela, "ui-janelas.png")
