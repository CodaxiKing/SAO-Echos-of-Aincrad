"""Gera as imagens provisórias do AincradUI (só biblioteca padrão do Python).

Uso (a partir da pasta aincrad-ui):
    python tools/gerar_placeholders.py

Escreve em src/main/resources/resourcepack/assets/aincrad/textures/ui/. As artes finais do
pacote aincrad-ui-assets.zip substituem estas na pasta do servidor (plugins/AincradUI/resourcepack/).
"""
import math
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "resourcepack", "assets", "aincrad", "textures", "ui")

# Fonte 5x7 para as siglas dos ícones.
FONT = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01111"],
    "I": ["01110", "00100", "00100", "00100", "00100", "00100", "01110"],
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
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "+": ["00000", "00100", "00100", "11111", "00100", "00100", "00000"],
    "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    "?": ["01110", "10001", "00001", "00010", "00100", "00000", "00100"],
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

    def disc(self, cx, cy, r_out, r_in, rgba):
        for y in range(int(cy - r_out) - 1, int(cy + r_out) + 2):
            for x in range(int(cx - r_out) - 1, int(cx + r_out) + 2):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r_in <= d <= r_out:
                    self.set(x, y, rgba)

    def text(self, s, cx, cy, scale, rgba):
        width = len(s) * 6 * scale - scale
        x0 = int(cx - width / 2)
        y0 = int(cy - 7 * scale / 2)
        for n, ch in enumerate(s):
            rows = FONT.get(ch, FONT["?"])
            for ry, row in enumerate(rows):
                for rx, bit in enumerate(row):
                    if bit == "1":
                        bx = x0 + n * 6 * scale + rx * scale
                        by = y0 + ry * scale
                        self.rect(bx, by, bx + scale, by + scale, rgba)

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


DARK = (24, 34, 48, 225)
RING = (220, 230, 242, 255)
RING_ON = (255, 170, 60, 255)
WHITE = (245, 248, 252, 255)


def icon(path, label, ring, fill=DARK, text=WHITE, size=128):
    c = Canvas(size, size)
    m = size / 2
    c.disc(m, m, m - 4, 0, fill)
    c.disc(m, m, m - 4, m - 12, ring)
    scale = 6 if len(label) <= 2 else 4
    c.text(label, m, m, scale, text)
    c.save(path)


def main():
    groups = {
        "icons": [("profile", "PR"), ("party", "PT"), ("message", "MS"), ("map", "MP"), ("config", "CF")],
        "under_icons": [("items", "IT"), ("equipment", "EQ"), ("skills", "SK"), ("friends", "FR"),
                        ("guild", "GD"), ("quests", "QS"), ("map", "MP"), ("options", "OP"),
                        ("help", "?"), ("logout", "LO"), ("call", "CL")],
        "buttons": [("yes", "S"), ("no", "N"), ("plus", "+"), ("minus", "-"), ("list", "LI"),
                    ("details", "DT"), ("start", "GO"), ("cancel", "X")],
    }
    for group, items in groups.items():
        for name, label in items:
            ring = RING
            if group == "buttons" and name == "yes":
                ring = (90, 220, 110, 255)
            if group == "buttons" and name in ("no", "cancel"):
                ring = (230, 80, 80, 255)
            icon(os.path.join(ROOT, group, name + ".png"), label, ring)
            icon(os.path.join(ROOT, group, name + "_on.png"), label, RING_ON, text=RING_ON)

    for i in range(1, 8):
        icon(os.path.join(ROOT, "skills", "skill_%d.png" % i), str(i), (120, 180, 255, 255))

    # Janela 512x512: painel claro com borda.
    w = Canvas(512, 512)
    w.rect(0, 0, 512, 512, (150, 172, 196, 255))
    w.rect(8, 8, 504, 504, (236, 241, 247, 235))
    w.rect(8, 8, 504, 40, (200, 214, 230, 255))
    w.save(os.path.join(ROOT, "window_blank.png"))

    # Silhueta 128x256.
    p = Canvas(128, 256)
    gray = (170, 182, 198, 255)
    p.disc(64, 40, 28, 0, gray)
    p.rect(32, 76, 96, 170, gray)
    p.rect(16, 80, 30, 160, gray)
    p.rect(98, 80, 112, 160, gray)
    p.rect(36, 170, 60, 250, gray)
    p.rect(68, 170, 92, 250, gray)
    p.save(os.path.join(ROOT, "person.png"))

    # "YOU ARE DEAD" 1024x256.
    d = Canvas(1024, 256)
    d.text("YOU ARE DEAD", 512, 128, 12, (220, 50, 50, 255))
    d.save(os.path.join(ROOT, "effects", "you_are_dead.png"))

    # Extras próprios do plugin.
    cur = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            dist = abs(x - 7.5) + abs(y - 7.5)
            if dist <= 7:
                cur.set(x, y, (255, 255, 255, 255) if dist <= 5 else (20, 30, 40, 255))
    cur.save(os.path.join(ROOT, "extra", "cursor.png"))

    seg = Canvas(4, 16)
    seg.rect(0, 0, 4, 16, (255, 255, 255, 255))
    seg.save(os.path.join(ROOT, "extra", "hud_segment.png"))

    lock = Canvas(32, 32)
    lg = (200, 200, 200, 255)
    lock.disc(16, 12, 8, 5, lg)
    lock.rect(6, 13, 26, 30, lg)
    lock.rect(14, 18, 18, 25, (60, 60, 60, 255))
    lock.save(os.path.join(ROOT, "extra", "lock.png"))

    print("Imagens provisórias geradas em", os.path.normpath(ROOT))


if __name__ == "__main__":
    sys.exit(main())
