# SPDX-License-Identifier: AGPL-3.0-or-later
"""Generates the SoftRing icon in all formats from a single geometry.

Design "A" (owner's choice, 2026-10-31): sun, bell, moon. SoftRing is not only for the
night (also "Always", at work...), so the day is in the icon too.

Usage: python3 art/generate_icon.py (the store PNG needs cairosvg: Debian package
cairosvg, or pip install cairosvg)
Outputs: adaptive icon vectors and the Quick Settings tile icon (app/src/main/res), art/icon.svg,
fastlane/metadata/android/en-US/images/icon.png (512x512, only with cairosvg).
Debug builds ("SoftRing-D", app/src/debug/res): grey "draft" colours and a "D" badge on the
bell, also in the monochrome layer so themed icons tell the two apps apart.
"""
import math, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BG, BELL, GOLD = "#2B2766", "#E2DFFF", "#FFD58A"

# Bell, centred (108x108 adaptive-icon grid, safe zone = circle r33 around 54,54)
bx = 54.0
bell_body = (f"M{bx-14},68 L{bx-14},53 C{bx-14},43.6 {bx-7.7},37 {bx},37 "
             f"C{bx+7.7},37 {bx+14},43.6 {bx+14},53 L{bx+14},68 L{bx+18},72 L{bx-18},72 Z")
bell_knob = f"M{bx-3},37 a3,3 0 1 1 6,0 Z"
clapper = f"M{bx-5},74 a5,5 0 0 0 10,0 Z"

# Crescent moon (right) = circle C1 minus circle C2
c1, r1 = (72.5, 36.0), 7.0
c2, r2 = (75.2, 33.3), 5.7
d = math.dist(c1, c2)
a = (r1**2 - r2**2 + d**2) / (2 * d)
h = math.sqrt(r1**2 - a**2)
mx = c1[0] + a * (c2[0] - c1[0]) / d
my = c1[1] + a * (c2[1] - c1[1]) / d
p = (mx + h * (c2[1] - c1[1]) / d, my - h * (c2[0] - c1[0]) / d)
q = (mx - h * (c2[1] - c1[1]) / d, my + h * (c2[0] - c1[0]) / d)
f = lambda v: f"{v:.3f}"
# Outer arc the long way (away from C2), inner arc back along C2
moon = (f"M{f(p[0])},{f(p[1])} A{r1},{r1} 0 1 0 {f(q[0])},{f(q[1])} "
        f"A{r2},{r2} 0 0 1 {f(p[0])},{f(p[1])} Z")

# Sun (left): one filled 8-pointed star, inside the safe zone and clear of the bell
sx, sy = 36.2, 36.6
SUN_OUT, SUN_IN, SUN_POINTS = 7.7, 4.8, 8
sun = "M" + " L".join(
    f"{f(sx + r * math.cos(t))},{f(sy + r * math.sin(t))}"
    for k in range(2 * SUN_POINTS)
    for r, t in [((SUN_OUT if k % 2 == 0 else SUN_IN), math.radians(-90 + k * 180 / SUN_POINTS))]) + " Z"

# (path, colour, stroke width): stroke None = filled shape, else a round-capped line
paths = [(bell_body, BELL, None), (bell_knob, BELL, None), (clapper, BELL, None),
         (moon, GOLD, None), (sun, GOLD, None)]

def svg_path(d_, c, stroke):
    if stroke is None:
        return f'<path d="{d_}" fill="{c}"/>'
    return f'<path d="{d_}" fill="none" stroke="{c}" stroke-width="{stroke}" stroke-linecap="round"/>'

def vector_path(d_, c, stroke, indent):
    paint = (f'android:fillColor="{c}"' if stroke is None else
             f'android:strokeColor="{c}"\n{indent}    android:strokeWidth="{stroke}"\n'
             f'{indent}    android:strokeLineCap="round"')
    return f'{indent}<path\n{indent}    {paint}\n{indent}    android:pathData="{d_}" />'

def svg(size, with_bg, rounded, store=True):
    s = size / 108
    bg = ""
    if with_bg:
        rx = f' rx="{76*0.22}"' if rounded else ""
        bg = f'<rect x="16" y="16" width="76" height="76"{rx} fill="{BG}"/>' if store else f'<rect width="108" height="108" fill="{BG}"/>'
    body = "".join(svg_path(d_, c, w) for d_, c, w in paths)
    vb = "16 16 76 76" if store else "0 0 108 108"
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{vb}">'
            f'{bg}{body}</svg>')

def vector(color_override=None, items_=None, clip=None):
    items = "\n".join(vector_path(d_, color_override or c, w, "    ")
                      for d_, c, w in (items_ or paths))
    if clip:
        inner = items.replace("\n    ", "\n        ")
        items = (f'    <group>\n        <clip-path android:pathData="{clip}" />\n'
                 f'    {inner}\n    </group>')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            f'{items}\n</vector>\n')

def tile_vector():
    """Quick Settings tile icon: the same shapes, white, cropped to their bounds, 24dp."""
    items = "\n".join(vector_path(d_, "#FFFFFFFF", w, "        ") for d_, _, w in paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="56"\n    android:viewportHeight="56">\n'
            '    <group\n        android:translateX="-27.5"\n        android:translateY="-25">\n'
            f'{items}\n    </group>\n</vector>\n')

# Debug icon: draft grey, bell unchanged, sun and moon without gold, plus a "D" badge in
# the bell colour. The badge is cut out of the bell by a ring: painted in the background
# colour in the colour layer (anti-aliased), a clip path in the monochrome layer, where the
# launcher tints every opaque pixel alike.
DEBUG_BG, DEBUG_SYMBOL = "#4A4A55", "#C9C9D2"
kx, ky, kr, gap = 72.0, 72.0, 8.0, 1.5
circle_cw = lambda r: f"M{kx},{ky-r} A{r},{r} 0 1 1 {kx},{ky+r} A{r},{r} 0 1 1 {kx},{ky-r} Z"
circle_ccw = lambda r: f"M{kx},{ky-r} A{r},{r} 0 1 0 {kx},{ky+r} A{r},{r} 0 1 0 {kx},{ky-r} Z"
# Letter D as a hole in the badge (opposite winding), its counter filled again
d_outer = f"M{kx-3.4},{ky-4} V{ky+4} H{kx-0.4} A4,4 0 0 0 {kx-0.4},{ky-4} Z"
d_inner = f"M{kx-1.6},{ky-2.2} H{kx-0.4} A2.2,2.2 0 0 1 {kx-0.4},{ky+2.2} H{kx-1.6} Z"
badge = f"{circle_cw(kr)} {d_outer} {d_inner}"
debug_paths = ([(d_, DEBUG_SYMBOL if c == GOLD else c, w) for d_, c, w in paths]
               + [(circle_cw(kr + gap), DEBUG_BG, None), (badge, BELL, None)])
debug_clip = f"M0,0 H108 V108 H0 Z {circle_ccw(kr + gap)}"

res = f"{ROOT}/app/src/main/res"
os.makedirs(f"{res}/drawable", exist_ok=True)
os.makedirs(f"{res}/mipmap-anydpi", exist_ok=True)
open(f"{res}/drawable/ic_launcher_foreground.xml", "w").write(vector())
open(f"{res}/drawable/ic_launcher_monochrome.xml", "w").write(vector("#FFFFFFFF"))
open(f"{res}/drawable/ic_tile.xml", "w").write(tile_vector())
adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/icon_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n')
open(f"{res}/mipmap-anydpi/ic_launcher.xml", "w").write(adaptive)

dbg = f"{ROOT}/app/src/debug/res"
os.makedirs(f"{dbg}/drawable", exist_ok=True)
os.makedirs(f"{dbg}/values", exist_ok=True)
open(f"{dbg}/drawable/ic_launcher_foreground.xml", "w").write(vector(items_=debug_paths))
mono = vector("#FFFFFFFF", clip=debug_clip)
mono = mono.replace("\n    </group>", "\n    </group>\n" + vector_path(badge, "#FFFFFFFF", None, "    "))
open(f"{dbg}/drawable/ic_launcher_monochrome.xml", "w").write(mono)
open(f"{dbg}/values/colors.xml", "w").write(
    '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by art/generate_icon.py: '
    'debug builds only (SoftRing-D) -->\n<resources>\n'
    f'    <color name="icon_background">{DEBUG_BG}</color>\n</resources>\n')

os.makedirs(f"{ROOT}/art", exist_ok=True)
open(f"{ROOT}/art/icon.svg", "w").write(svg(512, True, True))
img = f"{ROOT}/fastlane/metadata/android/en-US/images"
os.makedirs(img, exist_ok=True)
try:
    import cairosvg
except (ImportError, OSError) as e:
    print(f"Vector icon files written. Store PNG NOT updated (cairosvg unavailable: {e}).")
else:
    cairosvg.svg2png(bytestring=svg(512, True, True).encode(), write_to=f"{img}/icon.png")
    print("Icon files written.")
