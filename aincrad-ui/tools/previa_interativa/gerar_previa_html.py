"""Gera a prévia interativa da interface (um HTML só, com as artes do pack embutidas).

Uso:  python aincrad-ui/tools/previa_interativa/gerar_previa_html.py . saida.html
(da raiz do repositório; precisa do Pillow). Abra o saida.html no navegador.
"""
import base64
import io
import os
import sys

from PIL import Image

REPO = sys.argv[1]
OUT = sys.argv[2]
PACK = os.path.join(REPO, "aincrad-ui", "src", "main", "resources", "resourcepack", "assets", "aincrad")
ARTE = os.path.join(PACK, "textures", "ui")


def uri_img(im):
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def arte(nome, largura=None):
    im = Image.open(os.path.join(ARTE, nome + ".png")).convert("RGBA")
    if largura and im.width > largura:
        im = im.resize((largura, round(im.height * largura / im.width)), Image.LANCZOS)
    return im


def cheia(prefixo, passo, largura, altura, x0):
    """As 20 fatias de uma barra juntas, para servir de máscara."""
    im = Image.new("RGBA", (largura, altura), (0, 0, 0, 0))
    for i in range(20):
        im.alpha_composite(arte("%s%02d" % (prefixo, i)), (x0 + passo * i, 0))
    return im


def faixa(nome):
    im = Image.new("RGBA", (512, 216), (0, 0, 0, 0))
    im.alpha_composite(arte("banner/%s_0" % nome), (0, 0))
    im.alpha_composite(arte("banner/%s_1" % nome), (256, 0))
    return im.crop(im.getbbox())


A = {}
for nome in ["hud/frame", "hud/mini", "profile_panel", "window_blank", "window_alert", "extra/row_bg", "extra/row_bg_on",
             "extra/arrow_right", "extra/cursor"]:
    A[nome.split("/")[-1]] = uri_img(arte(nome, 760))
A["fill"] = uri_img(cheia("hud/fill_", 24, 744, 96, 236))
A["minifill"] = uri_img(cheia("hud/mini_", 12, 248, 32, 4))
for g in ["profile", "party", "message", "map", "config"]:
    A["g_" + g] = uri_img(arte("icons/" + g, 128))
    A["g_" + g + "_on"] = uri_img(arte("icons/" + g + "_on", 128))
for u in ["items", "equipment", "skills", "player", "quests", "friends", "guild", "call", "dungeon", "help", "map", "options",
          "logout", "invite"]:
    A["u_" + u] = uri_img(arte("under_icons/" + u, 96))
    A["u_" + u + "_on"] = uri_img(arte("under_icons/" + u + "_on", 96))
for b in ["yes", "no", "plus", "minus"]:
    A["b_" + b] = uri_img(arte("buttons/" + b, 128))
    A["b_" + b + "_on"] = uri_img(arte("buttons/" + b + "_on", 128))
for s in ["skill_1", "skill_2", "skill_4"]:
    A[s] = uri_img(arte("skills/" + s, 128))
for f in ["warning", "immortal", "duel", "system", "congratulations"]:
    A["f_" + f] = uri_img(faixa(f))
FONTE = "data:font/ttf;base64," + base64.b64encode(open(os.path.join(PACK, "font", "sao_ui.ttf"), "rb").read()).decode()

html = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "previa_molde.html"), encoding="utf-8").read()
html = html.replace("/*FONTE*/", FONTE)
css = "\n".join(".a-%s{background-image:url(%s)}" % (k, v) for k, v in A.items())
html = html.replace("/*ARTES*/", css)
html = html.replace("/*MASCARAS*/", ".fill{-webkit-mask-image:url(%s);mask-image:url(%s)}\n.minifill{-webkit-mask-image:url(%s);mask-image:url(%s)}"
                    % (A["fill"], A["fill"], A["minifill"], A["minifill"]))
open(OUT, "w", encoding="utf-8", newline="\n").write(html)
print(OUT, round(len(html) / 1024), "KB")
