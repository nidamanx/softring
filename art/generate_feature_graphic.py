# SPDX-License-Identifier: AGPL-3.0-or-later
"""Generates the store feature graphic (1024x500) in the five languages.

Design "A, day and night" (owner's choice, 2026-10-31): the icon on a lavender day
half, the name and the app tagline on an indigo night half. No hours on purpose:
SoftRing is not tied to one schedule.

Usage: python3 art/generate_feature_graphic.py (needs cairosvg and the Roboto font:
Debian packages cairosvg and fonts-roboto). Run art/generate_icon.py first if the
icon changed: the icon is read from art/icon.svg.
Outputs: fastlane/metadata/android/<locale>/images/featureGraphic.png
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DAY, NIGHT, NAME, GOLD = "#ECEAF7", "#17153A", "#ECEAF7", "#FFD58A"
FONT = "Roboto"
# Resource folder -> fastlane folder
LOCALES = {"values": "en-US", "values-it": "it-IT", "values-fr": "fr-FR",
           "values-de": "de-DE", "values-es": "es-ES"}


def tagline(res_dir: str) -> str:
    strings = ROOT / "app/src/main/res" / res_dir / "strings.xml"
    for item in ET.parse(strings).getroot().iter("string"):
        if item.get("name") == "tagline":
            # Android resource escapes (\' and \")
            return re.sub(r"\\(['\"])", r"\1", item.text or "")
    sys.exit(f"tagline not found in {strings}")


def icon_body() -> str:
    svg = (ROOT / "art/icon.svg").read_text()
    return svg[svg.index(">") + 1:svg.rindex("</svg>")]


def banner(text: str, icon: str) -> str:
    # XML-escape the tagline (& < >); quotes are fine inside a text node
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<rect width="1024" height="500" fill="{NIGHT}"/>
<rect width="392" height="500" fill="{DAY}"/>
<svg x="96" y="150" width="200" height="200" viewBox="16 16 76 76">{icon}</svg>
<text x="456" y="232" font-family="{FONT}" font-size="78" font-weight="500" fill="{NAME}">SoftRing</text>
<text x="458" y="290" font-family="{FONT}" font-size="23" fill="{GOLD}">{text}</text>
</svg>"""


def main() -> None:
    try:
        import cairosvg
    except ImportError as e:
        sys.exit(f"cairosvg is needed: {e}")
    icon = icon_body()
    for res_dir, locale in LOCALES.items():
        out = ROOT / "fastlane/metadata/android" / locale / "images/featureGraphic.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        cairosvg.svg2png(bytestring=banner(tagline(res_dir), icon).encode(), write_to=str(out))
        print(f"{out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
