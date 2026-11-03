# SPDX-License-Identifier: AGPL-3.0-or-later
"""Generates the README install badges that are not official artwork.

Same format as the official "Get it on" badges (646x250, black, grey border), so they
sit in one row with the Obtainium badge (owner's choice 2026-11-03, "A, all black",
English only). The F-Droid one says "coming soon" in grey and carries no F-Droid
artwork: replace it with the official badge once the app is on F-Droid.

Usage: python3 art/generate_badges.py (needs cairosvg and the Roboto font: Debian
packages cairosvg and fonts-roboto).
Outputs: art/badges/fdroid-coming-soon.png, art/badges/download-apk.png
"""
from pathlib import Path

import cairosvg

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "art/badges"
W, H = 646, 250
BG, BORDER, WHITE, GREY = "#000000", "#A6A6A6", "#FFFFFF", "#BDBDBD"
FONT = "Roboto"

# Icons on a 100x100 grid, drawn left inside the badge
DOWNLOAD = ('<path d="M42,14 H58 V52 H74 L50,78 L26,52 H42 Z" fill="{c}"/>'
            '<rect x="22" y="84" width="56" height="9" rx="4.5" fill="{c}"/>')
HOURGLASS = ('<path fill-rule="evenodd" fill="{c}" d="M26,12 H74 V20 H68 C68,36 58,44 54,50 '
             'C58,56 68,64 68,80 H74 V88 H26 V80 H32 C32,64 42,56 46,50 C42,44 32,36 32,20 '
             'H26 Z M40,20 C40,32 48,40 50,44 C52,40 60,32 60,20 Z M50,58 C46,62 40,68 40,80 '
             'H60 C60,68 54,62 50,58 Z"/>')


def badge(top: str, big: str, icon: str, colour: str, big_size: int) -> str:
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}">'
            f'<rect x="44" y="44" width="558" height="162" rx="14" fill="{BG}" '
            f'stroke="{BORDER}" stroke-width="4"/>'
            f'<g transform="translate(66,70) scale(1.1)">{icon.format(c=colour)}</g>'
            f'<text x="210" y="100" font-family="{FONT}" font-weight="500" font-size="34" '
            f'letter-spacing="1" fill="{colour}">{top}</text>'
            f'<text x="206" y="178" font-family="{FONT}" font-size="{big_size}" '
            f'fill="{colour}">{big}</text></svg>')


OUT.mkdir(parents=True, exist_ok=True)
for name, svg in {
    "fdroid-coming-soon": badge("COMING SOON TO", "F-Droid", HOURGLASS, GREY, 88),
    "download-apk": badge("DOWNLOAD THE", "APK", DOWNLOAD, WHITE, 96),
}.items():
    cairosvg.svg2png(bytestring=svg.encode(), write_to=str(OUT / f"{name}.png"))
print(f"Badges written to {OUT.relative_to(ROOT)}/.")
