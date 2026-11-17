"""Gera as imagens provisórias do AincradEffects (só biblioteca padrão do Python).

Uso (a partir da pasta aincrad-effects):
    python tools/gerar_placeholders.py

Escreve em src/main/resources/resourcepack/assets/aincrad/textures/effects/. As artes finais
substituem estas na pasta do servidor (plugins/AincradEffects/resourcepack/), com o mesmo nome.
"""
import math
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "resourcepack", "assets", "aincrad", "textures", "effects")

FONT = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["01110", "00100", "00100", "00100", "00100", "00100", "01110"],
    "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "10101", "01010"],
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "!": ["00100", "00100", "00100", "00100", "00100", "00000", "00100"],
    "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    " ": ["00000"] * 7,
}


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = bytearray(w * h * 4)

    def set(self, x, y, rgba):
        if 0 <= x < self.w and 0 <= y < self.h:
            i = (y * self.w + x) * 4
            self.px[i:i + 4] = bytes(rgba)

    def rect(self, x0, y0, x1, y1, rgba):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.set(x, y, rgba)

    def frame(self, x0, y0, x1, y1, t, rgba):
        self.rect(x0, y0, x1, y0 + t, rgba)
        self.rect(x0, y1 - t, x1, y1, rgba)
        self.rect(x0, y0, x0 + t, y1, rgba)
        self.rect(x1 - t, y0, x1, y1, rgba)

    def disc(self, cx, cy, r, rgba):
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                    self.set(x, y, rgba)

    def text(self, s, cx, cy, scale, rgba, shadow=None):
        width = len(s) * 6 * scale - scale
        x0 = int(cx - width / 2)
        y0 = int(cy - 7 * scale / 2)
        for dx, dy, color in ([(scale // 2 or 1, scale // 2 or 1, shadow)] if shadow else []) + [(0, 0, rgba)]:
            for n, ch in enumerate(s):
                for ry, row in enumerate(FONT.get(ch, FONT[" "])):
                    for rx, bit in enumerate(row):
                        if bit == "1":
                            bx = x0 + n * 6 * scale + rx * scale + dx
                            by = y0 + ry * scale + dy
                            self.rect(bx, by, bx + scale, by + scale, color)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        raw = b"".join(b"\x00" + bytes(self.px[y * self.w * 4:(y + 1) * self.w * 4]) for y in range(self.h))

        def chunk(tag, data):
            c = struct.pack(">I", len(data)) + tag + data
            return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(raw, 9))
        png += chunk(b"IEND", b"")
        with open(path, "wb") as f:
            f.write(png)


SHADOW = (10, 20, 35, 200)


def banner(path, text, color, w=1024, h=256, scale=12):
    c = Canvas(w, h)
    c.text(text, w // 2, h // 2, scale, color, SHADOW)
    c.save(path)


def panel(path, w, h, border, fill, header=None):
    c = Canvas(w, h)
    c.rect(0, 0, w, h, border)
    c.rect(8, 8, w - 8, h - 8, fill)
    if header:
        c.rect(8, 8, w - 8, 40, header)
    c.save(path)


def main():
    # Motor geral
    c = Canvas(512, 256)
    c.rect(0, 0, 512, 256, (20, 32, 50, 220))
    c.frame(0, 0, 512, 256, 8, (91, 200, 255, 255))
    c.text("AINCRAD", 256, 128, 10, (255, 200, 80, 255), SHADOW)
    c.save(os.path.join(ROOT, "teste.png"))

    banner(os.path.join(ROOT, "bem_vindo.png"), "BEM-VINDO", (120, 210, 255, 255))
    panel(os.path.join(ROOT, "fundo_menu.png"), 512, 512, (150, 172, 196, 255), (236, 241, 247, 235), (200, 214, 230, 255))

    # Integração com o Aincrad
    aincrad = os.path.join(ROOT, "aincrad")
    p = Canvas(512, 256)
    p.rect(0, 0, 512, 256, (40, 26, 22, 235))
    p.frame(0, 0, 512, 256, 10, (190, 150, 80, 255))
    p.text("BOSS", 256, 128, 14, (230, 70, 60, 255), SHADOW)
    p.save(os.path.join(aincrad, "placa_chefe.png"))

    banner(os.path.join(aincrad, "congratulations.png"), "CONGRATULATIONS!", (255, 210, 90, 255), scale=10)
    banner(os.path.join(aincrad, "floor_cleared.png"), "FLOOR CLEARED", (120, 210, 255, 255), scale=11)
    panel(os.path.join(aincrad, "janela_resultado.png"), 512, 512, (150, 172, 196, 255), (236, 241, 247, 235), (255, 210, 90, 255))

    for n in (1, 2, 3):
        b = Canvas(128, 128)
        b.disc(64, 64, 60, (190, 150, 80, 255))
        b.disc(64, 64, 52, (30, 45, 70, 255))
        b.text(str(n), 64, 64, 8, (255, 230, 150, 255))
        b.save(os.path.join(aincrad, "brasao_andar_%d.png" % n))

    print("Imagens provisórias geradas em", os.path.normpath(ROOT))


if __name__ == "__main__":
    sys.exit(main())
