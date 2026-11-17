#!/usr/bin/env python3
"""Gera as artes da "tela plana" (HUD em barras e telas com cursor, como a loja):

  - folhas de letras (fonte Inter, licença SIL OFL) em textures/flat/inter_<tamanho>.png
  - a largura exata de cada letra em src/main/resources/flat/inter_<tamanho>.widths
  - o cursor (seta) em textures/flat/cursor.png

Uso:  python tools/gerar_tela_plana.py <pasta com Inter-Regular.otf e Inter-SemiBold.otf>

Cada letra fica numa célula; um pixel quase invisível (alfa 1) marca até onde a célula conta,
para o Minecraft avançar exatamente a largura gravada no arquivo .widths.
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
TEXTURAS = os.path.join(RAIZ, "resourcepack", "assets", "aincrad", "textures", "flat")
DADOS = os.path.join(RAIZ, "flat")

# ASCII imprimível + Latin-1 (acentos do português) + alguns sinais.
LETRAS = [chr(c) for c in range(0x20, 0x7F)] + [chr(c) for c in range(0xA1, 0x100)] + list("–—•…")
COLUNAS = 16
ESCALA = 2  # pixels de textura por pixel de interface

# nome, arquivo da fonte, altura da linha (px de interface), tamanho da fonte (px de textura)
TAMANHOS = [
    ("p", "Inter-Regular.otf", 9, 14),
    ("b", "Inter-SemiBold.otf", 9, 14),
    ("m", "Inter-SemiBold.otf", 12, 19),
]


def folha(nome, arquivo, altura, tamanho, pasta_fontes):
    fonte = ImageFont.truetype(os.path.join(pasta_fontes, arquivo), tamanho)
    sobe, desce = fonte.getmetrics()
    cel_h = altura * ESCALA
    # Linha de base: centra a caixa (sobe + desce) na célula.
    base = (cel_h - (sobe + desce)) // 2 + sobe
    # Primeiro desenha cada letra para saber até onde vai a tinta.
    celulas, largos = {}, {}
    cel_w = 0
    for ch in LETRAS:
        px = int(-(-fonte.getlength(ch) // 1))
        cel = Image.new("L", (px + 4 * ESCALA, cel_h), 0)
        ImageDraw.Draw(cel).text((0, base), ch, font=fonte, fill=255, anchor="ls")
        caixa = cel.getbbox()
        tinta = caixa[2] if caixa else 0
        # O jogo soma 1 px de interface depois de cada letra: desconta do avanço da fonte.
        largo = max(tinta, px - ESCALA, ESCALA)
        celulas[ch], largos[ch] = cel, largo
        cel_w = max(cel_w, largo)
    cel_w += cel_w % 2
    # Avanço como o Minecraft calcula: int(0.5 + largura × escala) + 1.
    avancos = {ch: int(0.5 + largos[ch] / ESCALA) + 1 for ch in LETRAS}
    linhas = -(-len(LETRAS) // COLUNAS)
    img = Image.new("RGBA", (cel_w * COLUNAS, cel_h * linhas), (255, 255, 255, 0))
    for i, ch in enumerate(LETRAS):
        cx, cy = (i % COLUNAS) * cel_w, (i // COLUNAS) * cel_h
        cel = celulas[ch].crop((0, 0, cel_w, cel_h))
        branco = Image.new("RGBA", (cel_w, cel_h), (255, 255, 255, 0))
        branco.putalpha(cel)
        img.paste(branco, (cx, cy))
        mx, my = cx + largos[ch] - 1, cy + cel_h - 1
        if img.getpixel((mx, my))[3] == 0:
            img.putpixel((mx, my), (255, 255, 255, 1))
    os.makedirs(TEXTURAS, exist_ok=True)
    os.makedirs(DADOS, exist_ok=True)
    img.save(os.path.join(TEXTURAS, f"inter_{nome}.png"))
    with open(os.path.join(DADOS, f"inter_{nome}.widths"), "w", encoding="utf-8", newline="\n") as f:
        f.write(f"altura={altura}\ncolunas={COLUNAS}\n")
        for ch in LETRAS:
            f.write(f"{ord(ch):04X}={avancos[ch]}\n")
    print(f"inter_{nome}: {img.size[0]}x{img.size[1]}, {len(LETRAS)} letras")


def cursor():
    s = 4  # 4x: desenhado em 8x8 px de interface
    img = Image.new("RGBA", (8 * s, 8 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    seta = [(0, 0), (0, 26), (7, 20), (12, 31), (16, 29), (11, 18), (20, 18)]
    d.polygon(seta, fill=(255, 255, 255, 255), outline=(30, 30, 30, 255))
    d.line(seta + [seta[0]], fill=(30, 30, 30, 255), width=2)
    img.save(os.path.join(TEXTURAS, "cursor.png"))
    print("cursor.png")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for nome, arquivo, altura, tamanho in TAMANHOS:
        folha(nome, arquivo, altura, tamanho, sys.argv[1])
    cursor()


if __name__ == "__main__":
    main()
