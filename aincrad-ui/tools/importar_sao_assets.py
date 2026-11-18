"""Importa as artes do repositório SAO-UI/sao-assets para o resource pack do AincradUI.

Uso (a partir da pasta aincrad-ui, com Pillow instalado):
    git clone --depth 1 https://github.com/SAO-UI/sao-assets.git ../../sao-assets
    python tools/importar_sao_assets.py ../../sao-assets

O que faz:
- ícones do menu (em anel), do submenu, botões e skills em 128 px, com as versões _on;
- painel de perfil (silhueta com os pontos de equipamento) recortado do SAO_UI v1.100;
- janela em branco, setas e cursor no mesmo estilo (desenhados aqui);
- barra de vida: moldura vazia + 20 fatias brancas do preenchimento (o plugin pinta de
  verde, amarelo ou vermelho), a partir do SAO_Life_Gauge;
- "You are dead" do SAO_HEX;
- fonte SAO UI (TTF) para os textos do menu.

As artes são de darkblackswords (DeviantArt), reunidas pelo projeto SAO-UI. Ver CREDITOS.md.
"""
import os
import shutil
import sys

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(HERE, "..", "src", "main", "resources", "resourcepack", "assets", "aincrad")
TEX = os.path.join(PACK, "textures", "ui")

ICON_PX = 128

MAIN_ICONS = {  # menu principal: versão "Ringed"
    "profile": "Ringed Man", "party": "Ringed Men", "message": "Ringed Message",
    "map": "Ringed Location", "config": "Ringed Config",
}
SUB_ICONS = {  # submenu
    "items": "3_Menu-2/Items", "equipment": "3_Menu-2/Equipment", "skills": "3_Menu-2/Skills",
    "friends": "3_Menu-2/Friend", "guild": "3_Menu-2/Guild", "quests": "3_Menu-2/Quest & Message Box",
    "map": "3_Menu-2/Field Map & Position Check", "options": "3_Menu-2/Option",
    "help": "3_Menu-2/Help & Unknown", "logout": "3_Menu-2/Logout", "call": "4_Buttons/Calling",
    "invite": "3_Menu-2/Invite", "player": "3_Menu-2/Player", "dungeon": "3_Menu-2/Dungeon Map",
}
BUTTONS = {
    "yes": "Yes", "no": "No", "plus": "Plus", "minus": "Minus", "list": "List",
    "details": "Details", "start": "Start", "cancel": "Cancel & Dissolve",
}
SKILLS = ["Battle Healing", "Blade Throwing", "Dual Blades", "One-Handed Straight Sword",
          "Parrying", "Searching", "Tracking"]

# Barra de vida (SAO_Life_Gauge/1st/[Blank].png, 1620x258): região do preenchimento verde.
GAUGE_FILL = (341, 61, 1500, 132)       # x0, y0, x1, y1 (inclusive)
GAUGE_CROP = (325, 47, 1517, 147)       # preenchimento + borda
HUD_SCALE = 4                           # pixels de textura por pixel de fonte
HUD_SEGMENTS = 20
HUD_SEGMENT_PX = 6                      # largura de cada fatia em pixels de fonte
HUD_HEIGHT = 24                         # altura da moldura em pixels de fonte
HUD_NAME_W = 56                         # área do nome à esquerda da barra
HUD_BAR_Y = 3                           # topo da barra dentro da moldura
HUD_RIGHT_PAD = 6

# Painel de perfil em SAO_UI_v1.100_[sample]-2_[blank].png (3906x2500).
PROFILE_PANEL = (290, 114, 1326, 1332)  # a seta do desenho original começa em x=1326


def save(img, *parts):
    path = os.path.join(TEX, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    return path


def load(path):
    return Image.open(path).convert("RGBA")


def icon(src, alpha=1.0):
    img = load(src).resize((ICON_PX, ICON_PX), Image.LANCZOS)
    if alpha < 1.0:
        r, g, b, a = img.split()
        a = a.point(lambda v: int(v * alpha))
        img = Image.merge("RGBA", (r, g, b, a))
    return img


def import_icons(sao):
    base = os.path.join(sao, "SAO_Icons_v3.300")
    for name, src in MAIN_ICONS.items():
        # Fora de foco os ícones do anime ficam translúcidos.
        save(icon(os.path.join(base, "2_Menu-1_Ringed", src + ".png"), 0.78), "icons", name + ".png")
        save(icon(os.path.join(base, "2_Menu-1_Ringed", src + "_on.png")), "icons", name + "_on.png")
    for name, src in SUB_ICONS.items():
        save(icon(os.path.join(base, src + ".png")), "under_icons", name + ".png")
        save(icon(os.path.join(base, src + "_on.png")), "under_icons", name + "_on.png")
    for name, src in BUTTONS.items():
        save(icon(os.path.join(base, "4_Buttons", src + ".png")), "buttons", name + ".png")
        save(icon(os.path.join(base, "4_Buttons", src + "_on.png")), "buttons", name + "_on.png")
    for i, src in enumerate(SKILLS, 1):
        save(icon(os.path.join(base, "5_Skills", src + ".png")), "skills", "skill_%d.png" % i)


def import_profile(sao):
    src = load(os.path.join(sao, "SAO_UI_v1.100", "SAO_UI_v1.100_[sample]-2_[blank].png"))
    panel = src.crop(PROFILE_PANEL)
    w = 512
    panel = panel.resize((w, round(panel.height * w / panel.width)), Image.LANCZOS)
    save(panel, "profile_panel.png")
    # Silhueta sozinha (modo baú e outros usos).
    man = load(os.path.join(sao, "SAO_Man.png"))
    man = man.resize((round(man.width * 256 / man.height), 256), Image.LANCZOS)
    save(man, "person.png")


def import_dead(sao):
    img = load(os.path.join(sao, "SAO_HEX", "You_are_dead.png"))
    img = img.crop(img.getbbox())
    w = 1024
    save(img.resize((w, round(img.height * w / img.width)), Image.LANCZOS), "effects", "you_are_dead.png")


def draw_window():
    """Janela em branco no estilo do painel de perfil: branco, título com linha embaixo."""
    w, h = 512, 600
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rectangle((6, 8, w - 4, h - 2), fill=(0, 0, 0, 70))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(4)))
    d = ImageDraw.Draw(img)
    d.rectangle((2, 2, w - 8, h - 10), fill=(250, 250, 250, 255))
    d.line((24, 64, w - 30, 64), fill=(90, 90, 90, 255), width=3)
    d.rectangle((2, h - 16, w - 8, h - 10), fill=(225, 225, 225, 255))
    save(img, "window_blank.png")


def draw_arrows():
    """Setas brancas que ligam o ícone escolhido ao submenu e ao painel."""
    s = 64
    right = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(right).polygon([(4, 8), (60, 32), (4, 56)], fill=(250, 250, 250, 255))
    save(right, "extra", "arrow_right.png")
    left = right.transpose(Image.FLIP_LEFT_RIGHT)
    ImageDraw.Draw(left).ellipse((30, 26, 42, 38), fill=(70, 70, 70, 255))
    save(left, "extra", "arrow_left.png")


def draw_rows():
    """Fundo das linhas do submenu: branco translúcido, laranja com o cursor, cinza bloqueado."""
    w, h = 368, 96  # 92x24 px de fonte
    styles = {
        "row_bg": ((246, 246, 246, 218), (205, 205, 205, 235)),
        "row_bg_on": ((241, 165, 22, 245), (196, 128, 8, 255)),
        "row_bg_locked": ((226, 226, 226, 150), (190, 190, 190, 170)),
    }
    for name, (fill, edge) in styles.items():
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.rectangle((0, 0, w - 1, h - 1), fill=fill)
        d.rectangle((0, 0, 5, h - 1), fill=edge)              # borda da esquerda, como no anime
        d.rectangle((0, h - 4, w - 1, h - 1), fill=edge)      # sombra embaixo
        save(img, "extra", name + ".png")


def draw_small_extras():
    cur = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(cur)
    d.ellipse((2, 2, 29, 29), outline=(255, 255, 255, 230), width=3)
    d.ellipse((12, 12, 19, 19), fill=(255, 176, 30, 255))
    save(cur, "extra", "cursor.png")

    lock = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(lock)
    d.arc((8, 2, 24, 20), 180, 360, fill=(220, 220, 220, 255), width=3)
    d.rectangle((6, 13, 26, 30), fill=(220, 220, 220, 255))
    d.rectangle((14, 18, 18, 25), fill=(70, 70, 70, 255))
    save(lock, "extra", "lock.png")


def import_hud(sao):
    gauge = load(os.path.join(sao, "SAO_Life_Gauge", "1st", "[Blank].png"))
    fx0, fy0, fx1, fy1 = GAUGE_FILL
    cx0, cy0, cx1, cy1 = GAUGE_CROP
    fill_w_tex = HUD_SEGMENTS * HUD_SEGMENT_PX * HUD_SCALE
    f = fill_w_tex / (fx1 - fx0 + 1)  # pixels de textura por pixel do original

    bar = gauge.crop((cx0, cy0, cx1 + 1, cy1 + 1))
    bar_w, bar_h = round(bar.width * f), round(bar.height * f)
    bar = bar.resize((bar_w, bar_h), Image.LANCZOS)

    # Separa o verde (vira as fatias) do resto (vira a moldura com a barra vazia).
    empty = bar.copy()
    fill = Image.new("RGBA", bar.size, (0, 0, 0, 0))
    bp, ep, fp = bar.load(), empty.load(), fill.load()
    for y in range(bar_h):
        for x in range(bar_w):
            r, g, b, a = bp[x, y]
            if a > 40 and g > r + 25 and g > b + 25:
                lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
                v = int(170 + 85 * min(1.0, lum / 0.75))
                fp[x, y] = (v, v, v, a)
                ep[x, y] = (40, 42, 46, 150)

    s = HUD_SCALE
    total_w = (HUD_NAME_W + round(bar_w / s) + HUD_RIGHT_PAD) * s
    total_h = HUD_HEIGHT * s
    frame = Image.new("RGBA", (total_w, total_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    plate = (236, 233, 226, 205)
    edge = (150, 140, 122, 255)
    d.rounded_rectangle((0, 0, total_w - 1, total_h - 1), radius=3 * s, fill=plate, outline=edge, width=s)
    d.rectangle((3 * s, 4 * s, 5 * s, total_h - 5 * s), fill=(150, 140, 122, 255))  # detalhe à esquerda
    d.line((HUD_NAME_W * s - 3 * s, 3 * s, HUD_NAME_W * s - 3 * s, total_h - 4 * s), fill=edge, width=s // 2)
    bar_x, bar_y = HUD_NAME_W * s, HUD_BAR_Y * s
    frame.alpha_composite(empty, (bar_x, bar_y))
    save(frame, "hud", "frame.png")

    # Fatias: recortadas do preenchimento já posicionado na moldura, em colunas inteiras de
    # pixel de fonte, com a mesma altura da moldura (alinham sozinhas na vertical).
    layer = Image.new("RGBA", (total_w, total_h), (0, 0, 0, 0))
    layer.alpha_composite(fill, (bar_x, bar_y))
    box = layer.getbbox()
    start = box[0] // s * s
    end = -(-box[2] // s) * s
    seg_tex = HUD_SEGMENT_PX * s
    for i in range(HUD_SEGMENTS):
        x0 = start + i * seg_tex
        x1 = end if i == HUD_SEGMENTS - 1 else x0 + seg_tex  # a última pega o resto
        sl = layer.crop((x0, 0, x1, total_h))
        # O jogo mede a largura do glifo até a última coluna com pixel visível: marca as bordas.
        px = sl.load()
        for x in (0, sl.width - 1):
            if px[x, total_h - 1][3] == 0:
                px[x, total_h - 1] = (255, 255, 255, 1)
        save(sl, "hud", "fill_%02d.png" % i)

    print("HUD: moldura %dx%d px de fonte; preenchimento começa em x=%d; %d fatias de %d px (última %d px)"
          % (total_w // s, HUD_HEIGHT, start // s, HUD_SEGMENTS, HUD_SEGMENT_PX, (end - start) // s - (HUD_SEGMENTS - 1) * HUD_SEGMENT_PX))


MINI_SEGMENT_PX = 3   # barras dos membros da party: 20 fatias de 3 px
MINI_HEIGHT = 8       # altura da linha (a barra fica no meio)


def import_hud_mini(sao):
    """Barra menor (sem moldura) para os membros da party, no mesmo esquema de fatias."""
    gauge = load(os.path.join(sao, "SAO_Life_Gauge", "1st", "[Blank].png"))
    fx0, fy0, fx1, fy1 = GAUGE_FILL
    cx0, cy0, cx1, cy1 = GAUGE_CROP
    s = HUD_SCALE
    f = HUD_SEGMENTS * MINI_SEGMENT_PX * s / (fx1 - fx0 + 1)
    bar = gauge.crop((cx0, cy0, cx1 + 1, cy1 + 1))
    bar = bar.resize((round(bar.width * f), round(bar.height * f)), Image.LANCZOS)
    empty = bar.copy()
    fill = Image.new("RGBA", bar.size, (0, 0, 0, 0))
    bp, ep, fp = bar.load(), empty.load(), fill.load()
    for y in range(bar.height):
        for x in range(bar.width):
            r, g, b, a = bp[x, y]
            if a > 40 and g > r + 25 and g > b + 25:
                lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
                v = int(170 + 85 * min(1.0, lum / 0.75))
                fp[x, y] = (v, v, v, a)
                ep[x, y] = (40, 42, 46, 170)
    w = -(-bar.width // s) * s
    h = MINI_HEIGHT * s
    y0 = (h - bar.height) // 2
    frame = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    frame.alpha_composite(empty, (0, y0))
    px = frame.load()
    if px[w - 1, h - 1][3] == 0:
        px[w - 1, h - 1] = (255, 255, 255, 1)
    save(frame, "hud", "mini.png")

    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    layer.alpha_composite(fill, (0, y0))
    box = layer.getbbox()
    start = box[0] // s * s
    end = -(-box[2] // s) * s
    seg = MINI_SEGMENT_PX * s
    for i in range(HUD_SEGMENTS):
        x0 = start + i * seg
        x1 = end if i == HUD_SEGMENTS - 1 else x0 + seg
        sl = layer.crop((x0, 0, x1, h))
        sp = sl.load()
        for x in (0, sl.width - 1):
            if sp[x, h - 1][3] == 0:
                sp[x, h - 1] = (255, 255, 255, 1)
        save(sl, "hud", "mini_%02d.png" % i)
    print("HUD mini: %dx%d px de fonte; preenchimento começa em x=%d" % (w // s, MINI_HEIGHT, start // s))


def import_font(root):
    src = os.path.join(root, "fonts", "SAO_Fonts_v4.000", "TTF", "SAOUITT-Regular.ttf")
    dst = os.path.join(PACK, "font", "sao_ui.ttf")
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)


def import_alert(sao):
    """A janela de alerta (SAO_UI-Window, 1920x1371): título em cima, corpo cinza, botões embaixo.

    Os dois botões do desenho são apagados: o plugin põe os dele por cima, com o destaque do cursor.
    """
    src = load(os.path.join(sao, "SAO_UI-Window_v0.110", "SAO_UI-Window_opened_blank.png"))
    ImageDraw.Draw(src).rectangle((300, 960, 1620, 1270), fill=src.getpixel((960, 900)))
    w = 512
    save(src.resize((w, round(src.height * w / src.width)), Image.LANCZOS), "window_alert.png")


# Faixas mostradas no meio da tela (nome do arquivo de saída, origem, recorte ou None).
BANNERS = [
    ("warning", ("SAO_HEX", "Warning.png"), None),
    ("system", ("SAO_HEX", "Warning_System-Annnouncement.png"), None),
    ("immortal", ("SAO_HEX", "Immortal-Object.png"), None),
    ("congratulations", ("SAO_Congratulations!!.png",), None),
    ("duel", ("SAO_Duel_v1.000", "2-Duel_starting", "Duel_starting_center.png"), "bbox"),
]


def import_banners(sao):
    """Cada faixa cabe em 512x216 e é cortada em duas metades: um glifo não passa de 256 px."""
    for name, parts, crop in BANNERS:
        src = load(os.path.join(sao, *parts))
        if crop == "bbox":
            src = src.crop(src.getbbox())
        f = min(512 / src.width, 216 / src.height)
        small = src.resize((max(1, round(src.width * f)), max(1, round(src.height * f))), Image.LANCZOS)
        canvas = Image.new("RGBA", (512, 216), (0, 0, 0, 0))
        canvas.alpha_composite(small, ((512 - small.width) // 2, (216 - small.height) // 2))
        save(canvas.crop((0, 0, 256, 216)), "banner", name + "_0.png")
        save(canvas.crop((256, 0, 512, 216)), "banner", name + "_1.png")


def split_frame():
    """A moldura da barra de vida (744x96) em três peças de 248 px, para a HUD em barras."""
    frame = load(os.path.join(TEX, "hud", "frame.png"))
    for i in range(3):
        save(frame.crop((248 * i, 0, 248 * (i + 1), 96)), "hud", "frame_%d.png" % i)


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    root = os.path.abspath(sys.argv[1])
    sao = os.path.join(root, "images", "SAO_All-in-one_v4.000")
    if not os.path.isdir(sao):
        print("Pasta não encontrada:", sao)
        return 1
    if os.path.isdir(TEX):
        shutil.rmtree(TEX)
    import_icons(sao)
    import_profile(sao)
    import_dead(sao)
    draw_window()
    draw_arrows()
    draw_rows()
    draw_small_extras()
    import_hud(sao)
    import_hud_mini(sao)
    split_frame()
    import_alert(sao)
    import_banners(sao)
    import_font(root)
    print("Artes importadas para", os.path.normpath(PACK))
    return 0


if __name__ == "__main__":
    sys.exit(main())
