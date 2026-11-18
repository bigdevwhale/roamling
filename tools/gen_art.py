"""Generates Roamling's art with the Codex CLI image tool (same pipeline as Wildling).

Codex saves images into ~/.codex/generated_images/<session>/; the image of the session named in
the run's output is copied to art/sheets/<id>.png. Already present sheets are skipped.

    python tools/gen_art.py              # everything missing
    python tools/gen_art.py rustle bench # only these
"""
import re
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from species import SPECIES  # noqa: E402

OUT = ROOT / "art" / "sheets"
GEN = Path.home() / ".codex" / "generated_images"

STYLE = (
    "Style: detailed 16-bit pixel art like a modern cozy indie game, crisp pixels, clean dark outline, "
    "3-4 tone shading ramps, cozy warm palette, cute and round, big shiny eyes with a white shine and a little blush. "
    "Use the creature's own colours exactly as described. It is an invented fantasy creature, not a real animal species. "
    "Anatomy must stay consistent: a four-legged creature has exactly four legs in every cell, never an extra paw or arm; "
    "props are held in the mouth or tail, or rest beside it."
)
GRID = (
    "Layout: 3 columns x 2 rows of equal square cells on a flat solid pure magenta (#FF00FF) background. "
    "One creature per cell, centered, with clear margin; nothing touches the cell borders. No text, no grid lines, "
    "no shadows on the background, no other objects except those listed."
)
ICON_STYLE = (
    "Style: chunky 16-bit pixel art icons like a cozy indie game's inventory, crisp pixels, clean dark outline, 3-4 tone shading, warm palette. "
    "Plain objects: NO faces, NO eyes."
)
SCENE_STYLE = (
    "Style: detailed 16-bit pixel art like a modern cozy indie game, crisp pixels, clean dark outline, 3-4 tone shading ramps, "
    "warm late-summer palette."
)


def cells(s):
    """What each of the 6 cells shows: baby, teen, the grown forms, then extra poses to fill the sheet."""
    out = [f"baby: tiny, round, very cute, oversized head, {s.look}",
           f"teen: a bit bigger and lankier, same creature, adventurous, {s.look}"]
    for _, _, _, look in s.forms:
        out.append(f"grown-up form: the same creature fully grown, bigger and more detailed, {look}")
    extra = ["the baby asleep, curled up, peaceful face",
             "the teen walking along happily, mid-step",
             "the first grown-up form cheering, jumping with joy"]
    out += extra[: 6 - len(out)]
    return out[:6]


def pet_prompt(s):
    lines = "\n".join(f"{i + 1}) {c}" for i, c in enumerate(cells(s)))
    return f"""Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sprite sheet for a cozy walking app companion creature that grows as its owner walks: {s.look}.
{STYLE}
{GRID}
Cells, left to right, top to bottom:
{lines}
Keep the same character, palette and markings in every cell (the grown-up forms vary only as described), facing three-quarters right."""


OTHER = {
    "bench": """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a background scene for a cozy walking app, side-on like a 2D game level, seen from the front. Portrait 2:3.
""" + SCENE_STYLE + """
A quiet resting spot on a country trail at the edge of a park: in the lower half a wide grassy path with a few flowers and pebbles, a long wooden trail bench in the middle, an open canvas hiking backpack (olive green with leather straps and big front pockets, open on top) standing on the ground leaning against the bench on the left, a wooden trail signpost with blank arrows on the right, a low stone wall and bushes behind, distant soft hills.
The SKY in the upper 40% must be flat solid pure magenta (#FF00FF) (it will be painted later), no clouds, no sun, no moon.
Every object must be COMPLETE, nothing cropped: the tree on the left is a small tree whose whole round crown, with a soft irregular leafy outline, sits fully below the sky band and is surrounded by magenta on top; no straight cut edges anywhere against the magenta. The hills' tops are soft curves.
IMPORTANT: no animals, no creatures, no people, no text. The bench seat, the grass in front of it and the top of the backpack must be clear free space (creatures will be added later).""",
    "den": """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a cut-away side view of a cosy burrow den under the roots of a big old tree, like a 2D game, seen from the front. Portrait 2:3.
""" + SCENE_STYLE + """
Top: the base of a big oak with grass and a small round wooden door in the trunk. Below the ground: one large round earthen room held by twisting roots, a plank floor, a small stone hearth with a warm fire on the right, a lantern hanging from a root, a round rug in the middle, a few shelves in the root walls with empty space, a little round window of glass jars. The area above the grass (sky) is flat solid pure magenta (#FF00FF).
IMPORTANT: no animals, no creatures, no people, no text. The rug and the floor must be clear free space (creatures and furniture will be added later).""",
    "eggs": """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sprite sheet of 8 creature eggs for a cozy walking app.
""" + ICON_STYLE + """
Layout: 4 columns x 2 rows of equal square cells on a flat solid pure magenta (#FF00FF) background. One egg per cell, centered, with clear margin; no text, no grid lines.
Eggs in order (left to right, top to bottom):
1 a leafy green egg with moss and a small sprout on top, 2 a pale blue egg with water ripple stripes and a droplet, 3 a grey egg patterned like cobblestones, 4 a speckled brown-and-white egg with mountain peaks pattern,
5 a dusky violet egg with tiny stars and a crescent moon, 6 a pale cloud-white egg with a little rain cloud pattern, 7 a snowy white egg with frost crystals, 8 a golden egg with an old world map pattern, softly glowing.""",
    "finds": """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sprite sheet of 16 small things found on walks, for a cozy walking app.
""" + ICON_STYLE + """
Layout: 4 columns x 4 rows of equal square cells on a flat solid pure magenta (#FF00FF) background. One object per cell, centered, filling most of the cell, with clear margin; no text, no grid lines.
Objects in order (left to right, top to bottom):
1 an acorn with its cap, 2 an old copper coin, 3 a smooth striped river pebble, 4 a long bird feather,
5 a red maple leaf, 6 a small brass key, 7 a pinecone, 8 a spiral snail shell,
9 a bottle cap, 10 a little bell, 11 a sprig of lavender, 12 a blue glass shard smoothed by water,
13 a crystal shard, 14 a small iron horseshoe, 15 a bundle of chestnuts, 16 a postcard with a stamp (no text).""",
    "decor": """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sprite sheet of 16 cosy decorations for a burrow den under a tree, each a separate object, front view like a 2D game.
""" + ICON_STYLE + """
Layout: 4 columns x 4 rows of equal square cells on a flat solid pure magenta (#FF00FF) background. One object per cell, centered, with clear margin; no text, no grid lines.
Objects in order (left to right, top to bottom):
1 a hammock made of a big leaf, 2 a stack of old maps, 3 a small shelf with pebbles and shells, 4 a lantern on a stand,
5 a walking-stick rack with three sticks, 6 a cushion made of moss, 7 a framed trail map (no text), 8 a jar of fireflies,
9 a tiny wooden table with a teapot, 10 a basket of acorns, 11 a hanging garland of dried leaves, 12 a small globe on a stand,
13 a pair of old boots with flowers growing in them, 14 a woven rug, 15 a bookshelf with a few books, 16 a little tent of blankets.""",
    "gear": """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sprite sheet of 12 tiny clothes and gear for small creatures in a cozy walking app.
""" + ICON_STYLE + """
Layout: 4 columns x 3 rows of equal square cells on a flat solid pure magenta (#FF00FF) background. One item per cell, centered, with clear margin; no text, no grid lines.
Items in order (left to right, top to bottom):
1 a tiny yellow raincoat, 2 tiny red rubber boots, 3 a knitted striped scarf, 4 a straw sun hat,
5 a tiny brass lantern, 6 a little backpack, 7 a knitted wool hat with a pompom, 8 a small umbrella,
9 a tiny compass, 10 a pair of mittens on a string, 11 a flower crown, 12 tiny round sunglasses.""",
    "stamps": """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sheet of 12 small postage stamps with perforated edges, each showing a weather in tiny pixel art, for a walking app's stamp album.
""" + ICON_STYLE + """
Layout: 4 columns x 3 rows of equal cells on a flat solid pure magenta (#FF00FF) background. One stamp per cell, centered, with clear margin; NO text or numbers on the stamps, no grid lines.
Stamps in order (left to right, top to bottom):
1 clear sunny day over a green hill, 2 a few soft clouds, 3 heavy rain over a puddle, 4 light drizzle with an umbrella,
5 thick fog with a lamp post, 6 strong wind bending a tree, 7 snowfall over a little house, 8 a blizzard,
9 frost: ice crystals on a window, 10 heat: a big sun over a dry field, 11 a thunderstorm with lightning, 12 a rainbow after rain.""",
    "icons": """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sprite sheet of 16 icons for a cozy walking app with creatures.
""" + ICON_STYLE + """
Layout: 4 columns x 4 rows of equal square cells on a flat solid pure magenta (#FF00FF) background. One icon per cell, centered, filling most of the cell, with clear margin; no text, no grid lines.
Icons in order (left to right, top to bottom):
1 a pair of hiking boots, 2 a folded paper map, 3 a burrow door in a tree trunk, 4 a brass compass,
5 a pinecone (currency), 6 a small open book (bestiary), 7 a paw print, 8 a little green leaf,
9 a blue water drop, 10 a grey cobblestone, 11 a mountain peak, 12 a crescent moon with a star,
13 a rain cloud, 14 a red heart, 15 a scroll with a ribbon (quests), 16 a gift box.""",
}


BANNER = """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a wide banner illustration for a cozy walking app with little creatures. Landscape, about 3:1, a full-bleed scene edge to edge (no border, no frame, no text, no letters, no numbers, no logo).
Style: detailed 16-bit pixel art like a modern cozy indie game, crisp pixels, 3-4 tone shading ramps, warm palette.
Keep the left third calmer and simpler (a title will be written over it).
"""
OTHER.update({
    "banner_bestiary": BANNER + "Scene: a sunny meadow edge with soft grass, wildflowers and a forest behind; in the left part an open naturalist's notebook lies on the grass with BLANK pages, a magnifying glass and a pencil beside it. The middle and right part is open flat grass in the foreground, empty, as a stage (little creatures will be added on it later). IMPORTANT: NO animals, NO creatures, NO birds, NO butterflies, NO drawings on the pages.",
    "banner_journal": BANNER + "Scene: a hiking journal on a wooden trail table: an unfolded old trail map with a dashed route line, a few colourful weather postage stamps, a brass compass, a pair of small hiking boots, a thermos, golden evening light, hills far behind.",
    "banner_shop": BANNER + "Scene: a tiny cozy roadside trading stall on a forest trail: a wooden counter with a striped awning, little hanging raincoats, scarves and lanterns, jars of treats, a basket of pinecones used as money, a lantern glowing, flowers around.",
})


PROLOGUE = """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: an illustration for the story prologue of a cozy walking app. Landscape 4:3, a full-bleed scene edge to edge (no border, no frame, no text, no letters, no logo).
Style: detailed 16-bit pixel art like a modern cozy indie game, crisp pixels, 3-4 tone shading ramps, warm palette, a little magical.
IMPORTANT: NO animals, NO creatures, NO people, NO characters anywhere.
"""
OTHER.update({
    "prologue_valley": """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a calm side-on 2D game background, like a level of a cozy 16-bit indie game seen from the front. Landscape 4:3, full-bleed (no border, no text, no logo).
Style: crisp 16-bit pixel art with a LIMITED palette of about 16 colours, big simple shapes, flat colour areas with 2-3 tone shading, clean dark outlines, NOT painterly, NOT photo-like, NOT hyper-detailed, no tiny clutter.
Scene: a small green valley at sunset, seen from the side. Along one winding path from left to right stand five small cosy homes, each clearly different and readable: a short round stone tower with a pointed roof, a timber tavern with a hanging sign (no text), a tea house on a little cliff by the sea on the right, a round door in a grassy hill, and a log cabin at the edge of a dark fir forest on the left. Warm lit windows. A big calm orange-pink sky with two simple clouds takes the top half. Soft pale fog lies low at both edges of the picture.
IMPORTANT: NO animals, NO creatures, NO people.""",
    "prologue_veil": PROLOGUE + "Scene: an ordinary modern town street with old houses, a tram line, a bridge and a little square, half swallowed by soft pale-grey fog: the near street is clear and colourful, further away the colours fade into the fog, a few street lamps glow inside it. Calm and a bit melancholic, not scary.",
    "prologue_map": PROLOGUE + "Scene: a big old parchment map pinned on the wooden wall of a cozy village hall, almost entirely blank and white like fog, with only a small inked patch of streets in one corner and a tiny wax seal; a lantern, a quill and a rolled spare map on the table below, warm candle light.",
})


OTHER["places"] = """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sprite sheet of 16 small town and park landmarks for a cozy walking app's map, each a separate little scene object seen from slightly above, like map icons in a cozy indie RPG.
""" + ICON_STYLE + """
Layout: 4 columns x 4 rows of equal square cells on a flat solid pure magenta (#FF00FF) background. One landmark per cell, centered, filling most of the cell, with clear margin; no text, no letters, no grid lines.
IMPORTANT: no animals, no creatures, no people.
Landmarks in order (left to right, top to bottom):
1 a round stone fountain with water, 2 a huge old oak tree, 3 a little spring with water flowing from stones, 4 a wooden lookout platform with a railing,
5 a grassy hilltop with a small cairn of stones, 6 a small arched stone footbridge, 7 a stone monument on a pedestal (an abstract shape, not a person), 8 a tiny chapel with a bell,
9 an abstract modern sculpture (spiral shape), 10 an old red post box, 11 a small garden with flower beds and an arch, 12 a park bench under a lamp,
13 a soft glowing sparkle in the grass (something interesting), 14 the end of a rainbow touching the grass with a pot of crystals, 15 an old wooden gate in a stone wall, 16 a street lantern in fog."""


HABITAT = """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a simple background for a creature's portrait card in a cozy walking app: a side-on 2D game scene, landscape 4:3, full-bleed (no border, no text).
Style: crisp 16-bit pixel art with a limited palette, big simple shapes, 2-3 tone shading, calm and uncluttered, NOT painterly, NOT hyper-detailed.
Composition: the lower third is a flat clear ground area in the middle (a creature will stand there later); scenery around the edges and in the background.
IMPORTANT: NO animals, NO creatures, NO people.
Scene: """
OTHER.update({
    "habitat_green": HABITAT + "a sunny park meadow with soft grass, a few flowers, an old tree on one side, bushes, blue sky with one cloud.",
    "habitat_water": HABITAT + "a quiet riverside with a grassy bank in front, calm blue water behind, reeds, a small stone footbridge far away.",
    "habitat_stone": HABITAT + "an old-town cobblestone square with house fronts behind, a street lamp, flower boxes, warm afternoon light.",
    "habitat_height": HABITAT + "a grassy hilltop with a view over distant rooftops and a small bell tower, a stone stair going up on one side, wide sky.",
    "habitat_dusk": HABITAT + "a park path at night under a big moon and stars, a glowing street lantern, fireflies, deep blue colours.",
    "habitat_sky": HABITAT + "a rainy street corner with puddles on the ground, light rain falling, grey-blue clouds, a little rainbow far away.",
})


OTHER["prologue_where"] = """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: an illustration for a cozy walking app's onboarding page. PORTRAIT 3:4, full-bleed (no border, no text, no letters, no logo).
Style: crisp 16-bit pixel art with a limited warm palette, big simple shapes, 2-3 tone shading, calm, NOT painterly, NOT hyper-detailed.
Scene: seen from a grassy hilltop at golden hour. In the foreground on the grass lies an unrolled old paper map with a brass compass on it. From the map a softly glowing dotted path winds down the hill toward a cozy little town below: tiled roofs, a church spire, a bridge over a river, warm lit windows. The nearer streets are clear and colourful, the farther districts fade into soft pale fog. A big warm sky with a few clouds in the top third.
IMPORTANT: NO animals, NO creatures, NO people."""


OTHER["prologue_pick"] = """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a background for a cozy walking app's page where you choose a companion. PORTRAIT 3:4, full-bleed (no border, no text, no letters, no logo).
Style: crisp 16-bit pixel art with a limited soft palette, big simple shapes, 2-3 tone shading, calm, NOT painterly, NOT hyper-detailed.
Scene: the start of a trail at dawn. A wooden signpost with blank arrows and an old lantern glowing on a post stand on one side; a mossy stone wall and a few wildflowers; the path leads away between birch trees into soft pale morning fog with the first sunlight glowing through it. The lower half is calm open grass with little detail (cards will be placed over it).
IMPORTANT: NO animals, NO creatures, NO people."""


# Spirits of places: one per district family. id: (look)
SPIRITS = {
    "spirit_green": "a big gentle forest spirit like a round mossy badger-elk with a beard of moss, little mushrooms and bird nests in its antler-branches, kind sleepy eyes",
    "spirit_water": "a big gentle river spirit like a plump otter-miller with sleek blue-grey fur, a small wooden water-wheel on its back and a fishing net shawl",
    "spirit_stone": "a big gentle house-spirit of the streets like a fluffy bearded marten wearing a red roof-tile cap, a ring of old brass keys on its belt, warm lantern eyes",
    "spirit_height": "a big gentle wind spirit of the bell towers like a tall fluffy owl-goat with a weather-vane crest, pigeons on its shoulders and a brass spyglass",
    "spirit_cross": "a big gentle crossroads spirit like a long-coated fox-lady with a lantern on a staff, a wooden signpost hat and pockets full of letters",
}


def spirit_prompt(look):
    return f"""Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sprite sheet of a big gentle spirit of a city district for a cozy walking app: {look}.
{STYLE}
{GRID}
Cells, left to right, top to bottom:
1) asleep, curled up and half hidden in soft grey fog
2) awake in spring, with blossoms on it
3) awake in summer, with green leaves and sunlight
4) awake in autumn, with orange leaves
5) awake in winter, with snow on its back
6) happily pressing a big round wax seal stamp down with its paw
Exactly ONE creature in every cell (never two). The same character in every cell, facing three-quarters right."""


OTHER["seals"] = """Use your image generation tool to create ONE image. Do nothing else (no file copying).

Image: a sprite sheet of 6 round wax seals for a cozy map, each pressed with a tiny symbol (no letters).
""" + ICON_STYLE + """
Layout: 3 columns x 2 rows of equal square cells on a flat solid pure magenta (#FF00FF) background. One seal per cell, centered, with clear margin; no text.
Seals in order: 1 green wax with a leaf, 2 blue wax with a water wave, 3 red-brown wax with a little house roof, 4 slate wax with a bell tower, 5 amber wax with a crossroads signpost, 6 silver wax with a soft cloud of fog and a lantern."""


def image_of(output):
    """The image made in the Codex session named in its output (other sessions may draw at the same time)."""
    m = re.search(r"session id:\s*([0-9a-f-]+)", output)
    if not m:
        return None
    pngs = sorted((GEN / m.group(1)).glob("*.png"), key=lambda p: p.stat().st_mtime)
    return pngs[-1] if pngs else None


def run(job):
    name, prompt = job
    dst = OUT / f"{name}.png"
    if dst.exists():
        print("skip", name, flush=True)
        return
    for attempt in range(2):
        r = subprocess.run([shutil.which("codex.cmd") or shutil.which("codex") or "codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only", "-"],
                           input=prompt, text=True, encoding="utf-8", errors="replace", capture_output=True, cwd=str(ROOT))
        p = image_of((r.stdout or "") + (r.stderr or ""))
        if p is not None:
            shutil.copy(p, dst)
            print("ok", name, p, flush=True)
            return
    print("FAIL", name, (r.stdout or "")[-300:], (r.stderr or "")[-300:], flush=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    want = sys.argv[1:]
    jobs = list(OTHER.items()) + [(k, spirit_prompt(v)) for k, v in SPIRITS.items()] + [(s.id, pet_prompt(s)) for s in SPECIES]
    jobs = [j for j in jobs if not want or j[0] in want]
    with ThreadPoolExecutor(4) as ex:
        list(ex.map(run, jobs))


if __name__ == "__main__":
    main()
