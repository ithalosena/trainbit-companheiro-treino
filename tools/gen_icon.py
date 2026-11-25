"""App icon and spinning coin: one source for everything.

    python tools/gen_icon.py              # icon resources, previews and the Kotlin grid from COIN below
    python tools/gen_icon.py candidates   # the 6 candidate designs side by side (docs/icon_options.png)

The coin is an upright oval (22 x 30 on a 32 x 32 grid), with a 2 px rim that suggests its thickness, a recessed
inner ring and a dumbbell in the middle. Original design: no face, no slot, no palette from any game coin.

Tones (characters of the grid):
    .  transparent
    K  outline (darkest)
    S  shade (rim, recess shadow)
    B  base (cream face)
    H  highlight (rim light, recess light)
    D  dumbbell (dark)
    d  dumbbell shade
    G  gold glint (accent only)

Outputs:
    app/src/main/res/drawable/ic_launcher_foreground.xml   coin, 2 units per pixel, centred in the 108 viewport
    app/src/main/res/drawable/ic_launcher_monochrome.xml   silhouette with the dumbbell cut out
    app/src/debug/res/drawable/ic_launcher_foreground.xml  same coin plus a gold "debug" badge
    app/src/main/res/values/ic_launcher_colors.xml          background #1F1F1D
    app/src/main/java/.../ui/retro/CoinArt.kt               the grid for PixelCoin
    docs/icon_512.png, docs/icon_masks.png                  previews (circle, rounded square, drop, themed)
    docs/coin_spin.png                                      the 12 steps of a turn

Requires Pillow for the previews (pip install pillow).
"""
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res')
DEBUG_RES = os.path.join(ROOT, 'app', 'src', 'debug', 'res')
KOTLIN = os.path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'ithalo', 'hybridcoach', 'ui', 'retro', 'CoinArt.kt')
DOCS = os.path.join(ROOT, 'docs')

N = 32
BACKGROUND = '#1F1F1D'
COLORS = {
    'K': '#2B2A25',
    'S': '#8C8573',
    'B': '#D8D0BC',
    'H': '#F2EDE0',
    'D': '#3E3D36',
    'd': '#6E685A',
    'G': '#C8A24A',
}

# The chosen design (candidate 2 "reto_largo", see candidates() and docs/icon_options.png). Edit here; everything else is generated.
COIN = """
................................
.............KKKKKK.............
...........KKHHHHHHKK...........
..........KHHHSSSSHHHK..........
.........KHHSSSSSSSSHHK.........
........KHHSSSBBBBSSSHHK........
.......KKHSSBBBBBBBBSSHKK.......
.......KHSSSBBBBBBBBSSSHK.......
......KKHSSBBBBBBBBBBSSHKK......
......KHSSBBBBBBBBBBBBSSSK......
......KHSSBBBBBBBBBBBBHSSK......
......KHSSBBBBBBBBBBBBHSSK......
.....KHSSDDBBBBBBBBBDDDHSSK.....
.....KHSSDDBBBBBBBBBDDDHSSK.....
.....KHSSDDDBBBBBBBDDDDHSSK.....
.....KHSSDDDDDDDDDDDDDDHSSK.....
.....KHSSDDDDDDDDDDDDDDHSSK.....
.....KHSSDDDBBBBBBBDDDDHSSK.....
.....KHSSDDBBBBBBBBBDDDHSSK.....
.....KHSSDDBBBBBBBBBDDDHSSK.....
......KHSSBBBBBBBBBBBBHSSK......
......KHSSBBBBBBBBBBBBHSSK......
......KHSSBBBBBBBBBBBBHSSK......
......KKHSHBBBBBBBBBBHSSKK......
.......KSSHHBBBBBBBBHHSSK.......
.......KKSSHBBBBBBBBHSSKK.......
........KSSSHHBBBBHHSSSK........
.........KSSSSHHHHSSSSK.........
..........KSSSSSSSSSSK..........
...........KKSSSSSSKK...........
.............KKKKKK.............
................................
"""


def grid_of(text):
    rows = [r for r in text.strip('\n').split('\n')]
    assert len(rows) == N and all(len(r) == N for r in rows), 'COIN must be %dx%d' % (N, N)
    return rows


# ------------------------------------------------------------------ candidates (procedural, for the review sheet)
CX, CY, RX, RY = 16.0, 16.0, 11.0, 15.0  # centre of a 32 grid is 16: symmetric, no stray pixels


def layer(x, y):
    """How deep (in pixels) the pixel centre is inside the oval: -1 outside, 0 the outline, 1.. inner rings."""
    for k in range(0, 12):
        rx, ry = RX - k, RY - k
        if rx <= 0 or ry <= 0:
            return k
        if ((x + 0.5 - CX) / rx) ** 2 + ((y + 0.5 - CY) / ry) ** 2 > 1:
            return k - 1
    return 11


def base_coin():
    g = [['.'] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            k = layer(x, y)
            if k < 0:
                continue
            lit = (x + y) < (CX + CY)  # light comes from the top left
            if k == 0:
                g[y][x] = 'K'
            elif k in (1, 2):
                g[y][x] = 'H' if (lit and k == 1) else 'S'  # 2 px rim: the coin's thickness
            elif k == 3:
                g[y][x] = 'S' if lit else 'H'  # recessed ring: shadow on the lit side
            else:
                g[y][x] = 'B'
    return g


def put(g, x, y, c):
    if 0 <= x < N and 0 <= y < N and g[y][x] == 'B':  # the dumbbell only paints the face, never the rim
        g[y][x] = c


def rect(g, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(g, x, y, c)


def dumbbell_straight(g, plate_w=2, plate_h=6, inner=True):
    cy = 15
    rect(g, 10, cy, 21, cy + 1, 'D')  # bar, 2 px
    rect(g, 11, cy + 1, 20, cy + 1, 'd')
    for x0 in (8, 23 - plate_w):
        rect(g, x0, cy - plate_h // 2 + 1, x0 + plate_w - 1, cy + plate_h // 2, 'D')
    if inner:
        rect(g, 8 + plate_w, cy - 1, 8 + plate_w, cy + 2, 'D')
        rect(g, 22 - plate_w, cy - 1, 22 - plate_w, cy + 2, 'D')


def dumbbell_round(g):
    rect(g, 12, 15, 19, 16, 'D')  # bar, 2 px
    rect(g, 13, 16, 18, 16, 'd')
    for cx in (11.0, 21.0):  # round plates, symmetric about the centre, inside the face
        for y in range(10, 22):
            for x in range(5, 27):
                dx, dy = x + 0.5 - cx, y + 0.5 - 16
                if dx * dx + dy * dy <= 7.0:
                    put(g, x, y, 'd' if dx + dy > 1.6 else 'D')  # lower-right quarter in shade


def dumbbell_tilted(g):
    # Bar from lower left to upper right, 2 px thick, plates across it.
    for t in range(0, 13):
        x, y = 10 + t, 20 - round(t * 0.75)
        put(g, x, y, 'D')
        put(g, x, y + 1, 'D')
    for (px, py) in ((9, 21), (22, 11)):
        for d in range(-3, 4):
            put(g, px + round(d * 0.6), py + d, 'D')
            put(g, px + 1 + round(d * 0.6), py + d, 'D')


def dumbbell_hex(g):
    rect(g, 11, 15, 20, 16, 'D')
    rect(g, 12, 16, 19, 16, 'd')
    for x0 in (8, 21):
        rect(g, x0, 12, x0 + 2, 19, 'D')
        put(g, x0, 12, 'B'), put(g, x0 + 2, 12, 'B'), put(g, x0, 19, 'B'), put(g, x0 + 2, 19, 'B')


def glints(g):
    for (x, y) in ((9, 3), (10, 2), (8, 4)):
        if g[y][x] in 'HS':
            g[y][x] = 'G'


def candidates():
    out = []
    for name, draw in (
        ('reto', lambda g: dumbbell_straight(g)),
        ('reto_largo', lambda g: dumbbell_straight(g, plate_w=3, plate_h=8)),
        ('inclinado', dumbbell_tilted),
        ('anilhas_redondas', dumbbell_round),
        ('anilhas_hex', dumbbell_hex),
        ('reto_brilho', lambda g: (dumbbell_straight(g), glints(g))),
    ):
        g = base_coin()
        draw(g)
        out.append((name, [''.join(r) for r in g]))
    return out


# ------------------------------------------------------------------ outputs
def hexcolor(c):
    return c.lstrip('#')


def runs(grid, chars):
    """Horizontal runs of pixels whose character is in [chars], as (x, y, length)."""
    for y, row in enumerate(grid):
        x = 0
        while x < N:
            if row[x] in chars:
                start = x
                while x < N and row[x] in chars:
                    x += 1
                yield start, y, x - start
            else:
                x += 1


def path_data(grid, chars, unit=2, offset=22):
    return ''.join('M%d,%dh%dv%dh-%dz' % (offset + x * unit, offset + y * unit, w * unit, unit, w * unit) for x, y, w in runs(grid, chars))


def vector(paths):
    body = '\n'.join('    <path android:fillColor="#FF%s" android:pathData="%s" />' % (hexcolor(c), d) for c, d in paths if d)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by tools/gen_icon.py: edit COIN there, not this file. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n' + body + '\n</vector>\n')


DEBUG_BADGE = ('#C8A24A', 'M70,70h14v14h-14z'), ('#1F1F1D', 'M74,74h2v6h4v2h-6z')  # gold square with an "L" (local build)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)


def resources(grid):
    paths = [(COLORS[c], path_data(grid, c)) for c in 'KSBHDdG']
    write(os.path.join(RES, 'drawable', 'ic_launcher_foreground.xml'), vector(paths))
    write(os.path.join(DEBUG_RES, 'drawable', 'ic_launcher_foreground.xml'), vector(paths + list(DEBUG_BADGE)))
    # Monochrome: the coin's silhouette with the dumbbell cut out (the launcher tints it).
    write(os.path.join(RES, 'drawable', 'ic_launcher_monochrome.xml'), vector([('#000000', path_data(grid, 'KSBHG'))]))
    write(os.path.join(RES, 'values', 'ic_launcher_colors.xml'),
          '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/gen_icon.py. -->\n<resources>\n'
          '    <color name="ic_launcher_background">#FF%s</color>\n</resources>\n' % hexcolor(BACKGROUND))
    rows = ',\n'.join('        "%s"' % r for r in grid)
    color_lines = ',\n'.join("        '%s' to 0xFF%s" % (k, hexcolor(v)) for k, v in COLORS.items())
    write(KOTLIN, '''package com.ithalo.hybridcoach.ui.retro

/**
 * The coin of the app icon and of the opening, generated by tools/gen_icon.py (edit COIN there). One character per
 * pixel: '.' transparent, K outline, S shade, B base, H highlight, D dumbbell, d dumbbell shade, G gold glint.
 */
object CoinArt {
    const val SIZE = %d

    /** Fixed colours, the same as the launcher icon (the opening is always on the icon's dark background). */
    const val BACKGROUND = 0xFF%s
    val COLORS: Map<Char, Long> = mapOf(
%s,
    )

    val GRID: List<String> = listOf(
%s,
    )
}
''' % (N, hexcolor(BACKGROUND), color_lines, rows))


def image(grid, scale, bg=BACKGROUND):
    from PIL import Image
    im = Image.new('RGB', (N, N), bg)
    for y, row in enumerate(grid):
        for x, c in enumerate(row):
            if c != '.':
                im.putpixel((x, y), tuple(int(COLORS[c][i:i + 2], 16) for i in (1, 3, 5)))
    return im.resize((N * scale, N * scale), Image.NEAREST)


def icon_layer(grid, size=432):
    """The full 108 x 108 adaptive layer at [size] px: background plus the coin in its 64 x 64 box."""
    from PIL import Image
    s = size / 108
    im = Image.new('RGB', (size, size), BACKGROUND)
    coin = image(grid, 1).resize((round(64 * s), round(64 * s)), Image.NEAREST)
    im.paste(coin, (round(22 * s), round(22 * s)))
    return im


def masked(im, kind):
    from PIL import Image, ImageDraw
    size = im.width
    # Launchers show the inner 72 of the 108 viewport.
    crop = im.crop((size * 18 // 108, size * 18 // 108, size * 90 // 108, size * 90 // 108))
    w = crop.width
    mask = Image.new('L', (w, w), 0)
    d = ImageDraw.Draw(mask)
    if kind == 'circle':
        d.ellipse((0, 0, w - 1, w - 1), fill=255)
    elif kind == 'squircle':
        d.rounded_rectangle((0, 0, w - 1, w - 1), radius=w // 4, fill=255)
    else:  # drop: round with one square corner
        d.ellipse((0, 0, w - 1, w - 1), fill=255)
        d.rectangle((w // 2, w // 2, w - 1, w - 1), fill=255)
    out = Image.new('RGB', (w, w), (245, 245, 245))
    out.paste(crop, (0, 0), mask)
    return out


def previews(grid):
    from PIL import Image
    icon_layer(grid, 512).save(os.path.join(DOCS, 'icon_512.png'))
    layer_img = icon_layer(grid, 432)
    tiles = [masked(layer_img, k) for k in ('circle', 'squircle', 'drop')]
    # Themed (monochrome) icon: the launcher tints the silhouette; the dumbbell is the hole.
    mono_grid = [''.join('B' if c in 'KSBHG' else '.' for c in r) for r in grid]
    tiles.append(masked(icon_layer(mono_grid, 432), 'circle'))
    small = [t.resize((48, 48), Image.LANCZOS) for t in tiles]
    w = tiles[0].width
    sheet = Image.new('RGB', (len(tiles) * (w + 24) + 24, w + 48 + 48 + 72), (245, 245, 245))
    for i, (t, sm) in enumerate(zip(tiles, small)):
        sheet.paste(t, (24 + i * (w + 24), 24))
        sheet.paste(sm, (24 + i * (w + 24) + (w - 48) // 2, w + 48))
    sheet.save(os.path.join(DOCS, 'icon_masks.png'))


def spin_strip(grid, scale=6):
    """The 12 steps of a turn, with the same rule as PixelCoin.kt and CoinSpin (core): docs/coin_spin.png."""
    from PIL import Image
    face = image(grid, 1)
    strip = Image.new('RGB', (12 * (N + 4) * scale, (N + 4) * scale), BACKGROUND)
    rows = [y for y in range(N) if any(c != '.' for c in grid[y])]
    for step in range(12):
        c = math.cos(math.radians(30 * step))
        sc = round(abs(c) * 1000) / 1000
        frame = Image.new('RGB', (N, N), BACKGROUND)
        if sc < 0.2:
            for y in range(rows[0], rows[-1] + 1):
                for x in (N // 2 - 1, N // 2):
                    frame.putpixel((x, y), tuple(int(COLORS['G'][i:i + 2], 16) for i in (1, 3, 5)))
        else:
            visible = max(2, round(N * sc))
            start = (N - visible) // 2
            for j in range(visible):
                src = min(N - 1, int((j + 0.5) / visible * N))
                if c < -1e-9:
                    src = N - 1 - src
                for y in rows:
                    if grid[y][src] != '.':
                        frame.putpixel((start + j, y), face.getpixel((src, y)))
        strip.paste(frame.resize((N * scale, N * scale), Image.NEAREST), ((step * (N + 4) + 2) * scale, 2 * scale))
    strip.save(os.path.join(DOCS, 'coin_spin.png'))


def candidate_sheet():
    from PIL import Image, ImageDraw, ImageFont
    cands = candidates()
    alt = os.path.join(DOCS, 'icon_alternatives')
    os.makedirs(alt, exist_ok=True)
    big, pad, label = 8, 24, 36
    cell = N * big
    sheet = Image.new('RGB', (pad + len(cands) * (cell + pad), pad + label + cell + pad + 48 + pad), (40, 40, 37))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.load_default(size=22)
    except TypeError:
        font = ImageFont.load_default()
    for i, (name, g) in enumerate(cands):
        x = pad + i * (cell + pad)
        d.text((x, pad), '%d %s' % (i + 1, name), fill=(235, 230, 215), font=font)
        sheet.paste(image(g, big), (x, pad + label))
        small = masked(icon_layer(g, 432), 'squircle').resize((48, 48), Image.LANCZOS)
        sheet.paste(small, (x + (cell - 48) // 2, pad + label + cell + pad))
        with open(os.path.join(alt, '%d_%s.txt' % (i + 1, name)), 'w', encoding='utf-8') as f:
            f.write('\n'.join(g) + '\n')
    sheet.save(os.path.join(DOCS, 'icon_options.png'))
    print('docs/icon_options.png: ' + ', '.join(n for n, _ in cands))


def main():
    if len(sys.argv) > 1 and sys.argv[1] == 'candidates':
        candidate_sheet()
        return
    grid = grid_of(COIN)
    resources(grid)
    previews(grid)
    spin_strip(grid)
    print('ok: icon resources, CoinArt.kt, docs/icon_512.png, docs/icon_masks.png, docs/coin_spin.png')


if __name__ == '__main__':
    main()
