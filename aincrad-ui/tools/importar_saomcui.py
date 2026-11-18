"""Troca as artes e os sons do AincradUI pelos do mod SAO MC UI (A-G Projects).

Uso (a partir da pasta aincrad-ui, com Pillow instalado):
    python tools/importar_saomcui.py C:/caminho/para/saomcui

A pasta é a `assets/saomcui` do mod: `textures/gui/...`, `textures/effects/...` e `sounds/`.

O que troca:
- ícones do menu (em anel), do submenu, botões e skills, em 128 px, com as versões _on;
- a janela de alerta das confirmações (`gui/window_blank.png` do mod, já sem os botões);
- "You are dead" (recortado da tela cheia do mod);
- a silhueta com os pontos de equipamento (`gui/person.png`);
- os sete sons da interface (`sounds/ui/*.ogg`), que antes eram sons do Minecraft de reserva.

O que o mod não traz continua vindo de tools/importar_sao_assets.py: painel de perfil, barra de
vida, faixas, a fonte e os três ícones de submenu "invite", "player" e "dungeon".

Um arquivo só é regravado quando a imagem muda: rodar de novo não suja o git.
"""
import os
import shutil
import sys

from PIL import Image, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(HERE, "..", "src", "main", "resources", "resourcepack", "assets", "aincrad")
TEX = os.path.join(PACK, "textures", "ui")
SOUNDS = os.path.join(PACK, "sounds", "ui")

ICON_PX = 128

MAIN_ICONS = {  # nosso nome -> arquivo do mod (gui/icons)
    "profile": "ringed_man", "party": "ringed_men", "message": "ringed_message",
    "map": "ringed_location", "config": "ringed_config",
}
SUB_ICONS = {  # gui/under_icons
    "items": "items", "equipment": "equipment", "skills": "skills", "friends": "friend",
    "guild": "guild", "quests": "quest_and_message_box", "map": "field_map_and_position_check",
    "options": "option", "help": "help_and_unknown", "logout": "logout", "call": "calling",
}
BUTTONS = {  # gui/buttons
    "yes": "yes", "no": "no", "plus": "plus", "minus": "minus", "list": "list",
    "details": "details", "start": "start", "cancel": "cancel_and_dissolve",
}
SKILLS = ["battle_healing", "blade_throwing", "dual_blades", "one-handed_straight_sword",
          "parrying", "searching", "tracking"]  # gui/skills -> skill_1..7
SOUND_FILES = {  # nosso nome (ui.yml -> sons -> arquivo) -> arquivo do mod
    "open": "sao_menu", "move": "backsound", "confirm": "confirmsound", "back": "backsound",
    "close": "exitmenu", "critical": "critical_health", "death": "sao_death",
}

changed = []


def load(path):
    return Image.open(path).convert("RGBA")


def save(img, *parts):
    """Grava só se a imagem for diferente da que está lá."""
    path = os.path.join(TEX, *parts)
    if os.path.isfile(path):
        old = load(path)
        if old.size == img.size and ImageChops.difference(old, img).getbbox() is None:
            return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    changed.append("/".join(parts))


def icon(src, alpha=1.0):
    img = load(src).resize((ICON_PX, ICON_PX), Image.LANCZOS)
    if alpha < 1.0:
        r, g, b, a = img.split()
        img = Image.merge("RGBA", (r, g, b, a.point(lambda v: int(v * alpha))))
    return img


def import_icons(gui):
    for name, src in MAIN_ICONS.items():
        # Fora de foco os ícones do anime ficam translúcidos.
        save(icon(os.path.join(gui, "icons", src + ".png"), 0.78), "icons", name + ".png")
        save(icon(os.path.join(gui, "icons", src + "_on.png")), "icons", name + "_on.png")
    for folder, table in (("under_icons", SUB_ICONS), ("buttons", BUTTONS)):
        for name, src in table.items():
            save(icon(os.path.join(gui, folder, src + ".png")), folder, name + ".png")
            save(icon(os.path.join(gui, folder, src + "_on.png")), folder, name + "_on.png")
    for i, src in enumerate(SKILLS, 1):
        save(icon(os.path.join(gui, "skills", src + ".png")), "skills", "skill_%d.png" % i)


def import_alert(gui):
    """A janela de alerta (1920x1371): título em cima, corpo cinza, espaço dos botões embaixo.

    O plugin espera 512x366 (ui/window_alert, com as faixas em 92 e 226 px).
    """
    save(load(os.path.join(gui, "window_blank.png")).resize((512, 366), Image.LANCZOS), "window_alert.png")


def import_dead(textures):
    img = load(os.path.join(textures, "effects", "you_are_dead.png"))
    img = img.crop(img.getbbox())
    w = 1024
    save(img.resize((w, round(img.height * w / img.width)), Image.LANCZOS), "effects", "you_are_dead.png")


def import_person(gui):
    save(load(os.path.join(gui, "person.png")).resize((256, 256), Image.LANCZOS), "person.png")


def import_sounds(root):
    os.makedirs(SOUNDS, exist_ok=True)
    for name, src in SOUND_FILES.items():
        a = os.path.join(root, "sounds", src + ".ogg")
        b = os.path.join(SOUNDS, name + ".ogg")
        if not os.path.isfile(b) or open(a, "rb").read() != open(b, "rb").read():
            shutil.copyfile(a, b)
            changed.append("sounds/ui/" + name + ".ogg")


def main():
    if len(sys.argv) != 2 or not os.path.isdir(os.path.join(sys.argv[1], "textures", "gui")):
        sys.exit("Uso: python tools/importar_saomcui.py <pasta saomcui (a que tem textures/gui e sounds)>")
    root = sys.argv[1]
    gui = os.path.join(root, "textures", "gui")
    import_icons(gui)
    import_alert(gui)
    import_dead(os.path.join(root, "textures"))
    import_person(gui)
    import_sounds(root)
    print("%d arquivo(s) trocado(s):" % len(changed))
    for c in changed:
        print("  " + c)


if __name__ == "__main__":
    main()
