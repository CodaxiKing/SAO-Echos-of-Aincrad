#!/usr/bin/env python3
"""Gera as artes PROVISÓRIAS dos cristais (16x16, um losango colorido por tipo) e os modelos do
resource pack. Desenhe as finais com o mesmo nome de arquivo em textures/crystal/.

Uso:  python tools/gerar_cristais.py
"""
import json
import os
from PIL import Image

RAIZ = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "resourcepack", "assets", "aincrad")

# id do modelo -> (cor clara, cor média, cor escura)
CRISTAIS = {
    "cura": ((255, 170, 190), (235, 80, 120), (140, 30, 60)),
    "antidoto": ((190, 255, 190), (80, 200, 100), (30, 110, 50)),
    "teleporte": ((180, 220, 255), (70, 150, 240), (25, 70, 150)),
    "ressurreicao": ((255, 245, 190), (245, 200, 70), (160, 110, 20)),
    "gravacao": ((225, 200, 255), (160, 110, 235), (80, 45, 150)),
    "gravacao_gravado": ((255, 255, 255), (190, 150, 250), (100, 60, 170)),
}

# Losango: para cada linha, de qual coluna até qual (inclusive).
FORMA = {1: (7, 8), 2: (6, 9), 3: (5, 10), 4: (4, 11), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (4, 11),
         9: (4, 11), 10: (5, 10), 11: (5, 10), 12: (6, 9), 13: (6, 9), 14: (7, 8)}


def desenhar(nome, cores):
    clara, media, escura = cores
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, (x0, x1) in FORMA.items():
        for x in range(x0, x1 + 1):
            borda = x in (x0, x1) or y in (1, 14)
            if borda:
                cor = escura
            elif x <= (x0 + x1) // 2 and y <= 7:
                cor = clara  # faceta iluminada, em cima à esquerda
            else:
                cor = media
            img.putpixel((x, y), cor + (255,))
    img.putpixel((6, 4), (255, 255, 255, 255))  # brilho
    os.makedirs(os.path.join(RAIZ, "textures", "crystal"), exist_ok=True)
    img.save(os.path.join(RAIZ, "textures", "crystal", nome + ".png"))


def escrever(caminho, dados):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dados, f, indent=2)
        f.write("\n")


def main():
    for nome, cores in CRISTAIS.items():
        desenhar(nome, cores)
        escrever(os.path.join(RAIZ, "models", "crystal", nome + ".json"),
                 {"parent": "minecraft:item/generated", "textures": {"layer0": "aincrad:crystal/" + nome}})
        escrever(os.path.join(RAIZ, "items", "crystal", nome + ".json"),
                 {"model": {"type": "minecraft:model", "model": "aincrad:crystal/" + nome}})
        print(nome)


if __name__ == "__main__":
    main()
