"""Cuts the Codex sheets in art/sheets into app drawables (Wildling's cutter).

    python tools/cut.py [names]

Species (3x2): pet_<id>_<0..5>.webp – baby, teen, the grown forms, then extra poses.
Eggs (4x2) egg_<taste>, finds (4x4) find_<id>, decor (4x4) decor_<id>, gear (4x3) gear_<id>,
stamps (4x3) stamp_<n>, icons (4x4) ic_<name>. Scenes: bench_bg, den_bg with the magenta sky see-through.
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from species import SPECIES  # noqa: E402
from gen_art import SPIRITS  # noqa: E402

SHEETS = ROOT / "art" / "sheets"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
MAX = 320

EGGS = ["green", "water", "stone", "height", "dusk", "sky", "snow", "globe"]
FINDS = ["acorn", "coin", "pebble", "feather", "leaf", "key", "cone", "shell", "cap", "bell", "lavender", "glass", "crystal", "horseshoe", "chestnut", "postcard"]
DECOR = ["hammock", "maps", "shelf", "lamp", "sticks", "cushion", "frame", "fireflies", "teatable", "basket", "garland", "globe", "bootpots", "rug", "books", "tent"]
GEAR = ["raincoat", "boots", "scarf", "sunhat", "lantern", "pack", "hat", "umbrella", "compass", "mittens", "crown", "glasses"]
PLACES = ["fountain", "tree", "spring", "lookout", "hilltop", "bridge", "monument", "chapel", "sculpture", "postbox", "garden", "bench", "sparkle", "rainbow", "gate", "lantern"]
ICONS = ["boots", "map", "door", "compass", "cone", "book", "paw", "leaf", "drop", "stone", "peak", "moon", "cloud", "heart", "scroll", "gift"]


def key(im):
    im = im.convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            m = min(r, b) - g
            if m > 90 and r > 170 and b > 170:
                px[x, y] = (0, 0, 0, 0)
            elif m > 40 and r > 150 and b > 150:
                k = min(1, (m - 40) / 50)
                px[x, y] = (int(r - (r - g) * k), g, int(b - (b - g) * k), int(255 * (1 - k)))
    return im


def border_clean(cell):
    import numpy as np
    from scipy import ndimage
    arr = np.array(cell)
    lab, n = ndimage.label(arr[:, :, 3] > 30)
    edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    sizes = ndimage.sum(np.ones_like(lab), lab, range(n + 1))
    big = max(range(1, n + 1), key=lambda k: sizes[k]) if n else 0
    for k in edge:
        if k != big:
            arr[lab == k, 3] = 0
    return Image.fromarray(arr)


def frames(sheet, cols, rows):
    im = key(Image.open(sheet))
    w, h = im.size
    cw, ch = w / cols, h / rows
    out = []
    for j in range(rows):
        for i in range(cols):
            cell = border_clean(im.crop((int(i * cw), int(j * ch), int((i + 1) * cw), int((j + 1) * ch))))
            box = cell.getchannel("A").point(lambda v: 255 if v > 30 else 0).getbbox()
            cell = cell.crop(box) if box else cell
            s = MAX / max(cell.size)
            if s < 1:
                cell = cell.resize((max(1, round(cell.width * s)), max(1, round(cell.height * s))), Image.LANCZOS)
            out.append(cell)
    return out


def save(im, name):
    im.save(OUT / f"{name}.webp", "WEBP", quality=90, method=6)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    want = set(sys.argv[1:])
    for s in SPECIES:
        f = SHEETS / f"{s.id}.png"
        if want and s.id not in want:
            continue
        if not f.exists():
            print("missing", s.id)
            continue
        for n, fr in enumerate(frames(f, 3, 2)):
            save(fr, f"pet_{s.id}_{n}")
        print("pet", s.id)
    for sheet, names, prefix, cols, rows, size in (("eggs", EGGS, "egg", 4, 2, 200), ("finds", FINDS, "find", 4, 4, 160), ("decor", DECOR, "decor", 4, 4, 240),
                                                   ("gear", GEAR, "gear", 4, 3, 160), ("places", PLACES, "place", 4, 4, 200), ("icons", ICONS, "ic", 4, 4, 128), ("stamps", [str(i) for i in range(12)], "stamp", 4, 3, 220)):
        if (SHEETS / f"{sheet}.png").exists() and (not want or sheet in want):
            for name, fr in zip(names, frames(SHEETS / f"{sheet}.png", cols, rows)):
                fr.thumbnail((size, size), Image.LANCZOS)
                save(fr, f"{prefix}_{name}")
            print(sheet)
    for sp in SPIRITS:
        if (SHEETS / f"{sp}.png").exists() and (not want or sp in want):
            for n, fr in enumerate(frames(SHEETS / f"{sp}.png", 3, 2)):
                save(fr, f"{sp}_{n}")
            print(sp)
    if (SHEETS / "seals.png").exists() and (not want or "seals" in want):
        for n, fr in enumerate(frames(SHEETS / "seals.png", 3, 2)):
            fr.thumbnail((180, 180), Image.LANCZOS)
            save(fr, f"seal_{n}")
        print("seals")
    for b in ("banner_bestiary", "banner_journal", "banner_shop", "prologue_valley", "prologue_veil", "prologue_map", "prologue_where", "prologue_pick", "habitat_green", "habitat_water", "habitat_stone", "habitat_height", "habitat_dusk", "habitat_sky"):
        if (SHEETS / f"{b}.png").exists() and (not want or b in want):
            g = Image.open(SHEETS / f"{b}.png").convert("RGB")
            g = g.resize((960, round(960 * g.height / g.width)), Image.LANCZOS)
            g.save(OUT / f"{b}.webp", "WEBP", quality=88, method=6)
            print(b)
    for scene in ("bench", "den"):
        if (SHEETS / f"{scene}.png").exists() and (not want or scene in want):
            g = key(Image.open(SHEETS / f"{scene}.png"))
            g = g.resize((768, round(768 * g.height / g.width)), Image.LANCZOS)
            g.save(OUT / f"{scene}_bg.webp", "WEBP", quality=88, method=6)
            print(scene)


if __name__ == "__main__":
    main()
