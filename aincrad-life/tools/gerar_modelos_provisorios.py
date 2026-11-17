#!/usr/bin/env python3
"""Gera os modelos PROVISÓRIOS das bancadas: cada um aponta para o modelo de um bloco do próprio
Minecraft, para tudo funcionar antes de os modelos finais (do Blockbench) chegarem.

Para trocar por um modelo seu, substitua o arquivo em
resourcepack/assets/aincrad/models/station/<id>.json (e ponha a textura em textures/station/).
O arquivo em items/station/<id>.json não precisa mudar.

Uso:  python tools/gerar_modelos_provisorios.py
"""
import json
import os

RAIZ = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "resourcepack", "assets", "aincrad")

# id do modelo -> modelo de bloco do Minecraft usado como provisório
BANCADAS = {
    "forge_anvil": "minecraft:block/anvil",
    "forge_furnace": "minecraft:block/blast_furnace",
    "quench_barrel": "minecraft:block/water_cauldron_full",
    "kitchen": "minecraft:block/smoker",
    "alchemy_table": "minecraft:block/brewing_stand",
    "fish_stall": "minecraft:block/barrel",
    "order_board": "minecraft:block/bookshelf",
}


def escrever(caminho, dados):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dados, f, indent=2)
        f.write("\n")


def main():
    for nome, bloco in BANCADAS.items():
        escrever(os.path.join(RAIZ, "models", "station", nome + ".json"), {"parent": bloco})
        escrever(os.path.join(RAIZ, "items", "station", nome + ".json"),
                 {"model": {"type": "minecraft:model", "model": "aincrad:station/" + nome}})
        print(nome)


if __name__ == "__main__":
    main()
