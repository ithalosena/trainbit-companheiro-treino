"""Avatar sprite pipeline.

    python tools/avatar/build.py all        # sprite + animate + items + pack
    python tools/avatar/build.py sprite     # assets_src/avatar_base.png -> assets_src/avatar_sprite.png
    python tools/avatar/build.py animate    # sprite + avatar_rig.json -> assets_src/build/frames.json
    python tools/avatar/build.py items      # assets_src/items/<slot>/<id>.png -> assets_src/build/items.json
    python tools/avatar/build.py pack       # -> app assets spritesheet.png/.json, core AvatarArt.kt, build/previews/avatar_contact_sheet.png

Tones: 0 transparent, 1 outline, 2 shade, 3 base, 4 highlight. The app paints them with the theme colours;
the PNGs store them with the BEGE light ramp below (the loader maps those exact colours back to tones).
Requires Pillow (pip install pillow).
"""
import argparse
import json
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rig as rigmod  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SRC = os.path.join(ROOT, 'assets_src')
BUILD = os.path.join(SRC, 'build')
BASE = os.path.join(SRC, 'avatar_base.png')
SPRITE = os.path.join(SRC, 'avatar_sprite.png')
RIG = os.path.join(SRC, 'avatar_rig.json')
ITEMS_DIR = os.path.join(SRC, 'items')
OUT_ASSETS = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'avatar')
OUT_KT = os.path.join(ROOT, 'core', 'src', 'main', 'kotlin', 'com', 'ithalo', 'hybridcoach', 'core', 'avatar', 'AvatarArt.kt')
CATALOG_KT = os.path.join(ROOT, 'core', 'src', 'main', 'kotlin', 'com', 'ithalo', 'hybridcoach', 'core', 'game', 'ItemCatalog.kt')
CONTACT = os.path.join(ROOT, 'build', 'previews', 'avatar_contact_sheet.png')

# Reference ramp (theme BEGE, light): outline, shade, base, highlight.
TONES = {1: (0x3E, 0x3D, 0x36), 2: (0x8C, 0x85, 0x73), 3: (0xB8, 0xB0, 0x9B), 4: (0xEA, 0xE4, 0xD4)}
CH = '.1234'


def log(msg):
    print(msg, flush=True)


# ---------------------------------------------------------------- colour: nearest tone in Lab
def _lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def lab(rgb):
    r, g, b = (_lin(v) for v in rgb[:3])
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
    f = lambda t: t ** (1 / 3) if t > 0.008856 else 7.787 * t + 16 / 116
    fx, fy, fz = f(x), f(y), f(z)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)


TONES_LAB = {k: lab(v) for k, v in TONES.items()}
_cache = {}


def nearest_tone(rgba):
    """0 for transparent pixels, else the tone whose reference colour is closest in Lab."""
    if len(rgba) > 3 and rgba[3] < 128:
        return 0
    key = rgba[:3]
    if key not in _cache:
        L = lab(key)
        _cache[key] = min(TONES_LAB, key=lambda k: sum((a - b) ** 2 for a, b in zip(L, TONES_LAB[k])))
    return _cache[key]


def grid_to_image(grid):
    h, w = len(grid), len(grid[0])
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        for x in range(w):
            if grid[y][x]:
                px[x, y] = TONES[grid[y][x]] + (255,)
    return im


def image_to_grid(im):
    im = im.convert('RGBA')
    px = im.load()
    return [[nearest_tone(px[x, y]) for x in range(im.width)] for y in range(im.height)]


# ---------------------------------------------------------------- sprite
def remove_background(im):
    """Keeps real transparency; an opaque image loses the colour of its top-left corner (within a tolerance)."""
    im = im.convert('RGBA')
    alpha = im.getchannel('A')
    if alpha.getextrema()[0] < 128:
        return im
    px = im.load()
    bg = px[0, 0]
    for y in range(im.height):
        for x in range(im.width):
            p = px[x, y]
            if sum(abs(a - b) for a, b in zip(p[:3], bg[:3])) < 48:
                px[x, y] = (0, 0, 0, 0)
    log('sprite: fundo opaco removido pela cor do canto')
    return im


def find_grid(im, lo, hi):
    """Pitch and phase of a pixel-art grid: the spacing that best lines up with the colour edges (per axis)."""
    small = im
    w, h = small.size
    px = small.load()
    cls = lambda x, y: nearest_tone(px[x, y])
    col, row = [0] * w, [0] * h
    for y in range(0, h, 2):
        prev = cls(0, y)
        for x in range(1, w):
            c = cls(x, y)
            if c != prev:
                col[x] += 1
            prev = c
    for x in range(0, w, 2):
        prev = cls(x, 0)
        for y in range(1, h):
            c = cls(x, y)
            if c != prev:
                row[y] += 1
            prev = c

    def best(profile):
        top = None
        p = lo
        while p <= hi:
            for ph10 in range(0, int(p * 10)):
                ph = ph10 / 10
                score, k = 0.0, 0
                while True:
                    pos = int(round(ph + k * p))
                    if pos >= len(profile):
                        break
                    score += profile[pos] + 0.5 * ((profile[pos - 1] if pos > 0 else 0) + (profile[pos + 1] if pos + 1 < len(profile) else 0))
                    k += 1
                score /= max(k, 1)
                if top is None or score > top[0]:
                    top = (score, p, ph)
            p += 0.05
        return top

    return best(col), best(row)


def cmd_sprite(cfg):
    im = remove_background(Image.open(BASE))
    bbox = im.getbbox()
    if not bbox:
        raise SystemExit('sprite: imagem vazia')
    im = im.crop(bbox)
    sc = cfg['sprite']
    grid = None
    if sc.get('pixel_art', True):
        (sx, pwx, phx), (sy, pwy, phy) = find_grid(im, *sc['pitch_range'])
        if min(sx, sy) >= 2.0:
            cols, rows = int((im.width - phx) / pwx), int((im.height - phy) / pwy)
            px = im.load()
            grid = []
            for j in range(rows):
                line = []
                for i in range(cols):
                    votes = [0] * 5
                    x0, y0 = phx + i * pwx, phy + j * pwy
                    for dy in range(3, int(pwy) - 3, 2):
                        for dx in range(3, int(pwx) - 3, 2):
                            xx, yy = int(x0 + dx), int(y0 + dy)
                            if xx < im.width and yy < im.height:
                                votes[nearest_tone(px[xx, yy])] += 1
                    line.append(max(range(5), key=lambda t: votes[t]))
                grid.append(line)
            log('sprite: grade de pixel art encontrada (passo %.2f x %.2f, %d x %d)' % (pwx, pwy, cols, rows))
        else:
            log('sprite: nenhuma grade de pixel art clara; usando reducao por caixa')
    if grid is None:
        th = sc['fallback_height']
        tw = max(1, round(im.width * th / im.height))
        grid = image_to_grid(im.resize((tw, th), Image.BOX))
    grid = crop_grid(grid)
    grid = despeckle(grid)
    os.makedirs(SRC, exist_ok=True)
    grid_to_image(grid).save(SPRITE)
    log('sprite: %s (%d x %d)' % (os.path.relpath(SPRITE, ROOT), len(grid[0]), len(grid)))


def crop_grid(grid):
    ys = [j for j, r in enumerate(grid) if any(r)]
    xs = [i for i in range(len(grid[0])) if any(r[i] for r in grid)]
    return [r[xs[0]:xs[-1] + 1] for r in grid[ys[0]:ys[-1] + 1]]


def despeckle(grid):
    """Removes lone pixels (no opaque neighbour among the 8) and fills 1 px holes (all 4 neighbours opaque)."""
    h, w = len(grid), len(grid[0])
    at = lambda x, y: grid[y][x] if 0 <= x < w and 0 <= y < h else 0
    out = [r[:] for r in grid]
    fixed = 0
    for y in range(h):
        for x in range(w):
            n8 = [at(x + a, y + b) for a in (-1, 0, 1) for b in (-1, 0, 1) if a or b]
            n4 = [at(x + 1, y), at(x - 1, y), at(x, y + 1), at(x, y - 1)]
            if grid[y][x] and not any(n8):
                out[y][x] = 0
                fixed += 1
            elif not grid[y][x] and all(n4):
                out[y][x] = max(set(n4), key=n4.count)
                fixed += 1
    if fixed:
        log('sprite: %d pixel(s) soltos ou buracos corrigidos' % fixed)
    return out


# ---------------------------------------------------------------- animate
def load_rig():
    with open(RIG, encoding='utf-8') as f:
        cfg = json.load(f)
    return cfg


def cmd_animate(cfg):
    grid = image_to_grid(Image.open(SPRITE))
    r = rigmod.Rig(grid, cfg)
    frames = {}
    for name, prefix, pose, count, ms in rigmod.ANIMATIONS:
        fn = getattr(r, pose)
        for i in range(count):
            frames['%s_%d' % (prefix, i)] = [''.join(CH[v] for v in row) for row in fn(i).g]
    os.makedirs(BUILD, exist_ok=True)
    with open(os.path.join(BUILD, 'frames.json'), 'w', encoding='utf-8') as f:
        json.dump({'width': r.W, 'height': r.H, 'frames': frames}, f)
    log('animate: %d quadros de %d x %d' % (len(frames), r.W, r.H))


# ---------------------------------------------------------------- items
def catalog_ids():
    """Sticker ids of the item catalog (the items that can appear next to the pet)."""
    try:
        text = open(CATALOG_KT, encoding='utf-8').read()
    except OSError:
        return []
    return re.findall(r'ItemDef\("([a-z0-9_]+)",[^\n]*\bSTICKER\b', text)


def cmd_items(cfg):
    slots = cfg.get('items', {})
    items = {}
    if os.path.isdir(ITEMS_DIR):
        for slot in sorted(os.listdir(ITEMS_DIR)):
            folder = os.path.join(ITEMS_DIR, slot)
            if not os.path.isdir(folder):
                continue
            if slot not in slots:
                log('items: AVISO pasta "%s" sem slot em avatar_rig.json; ignorada' % slot)
                continue
            for fn in sorted(os.listdir(folder)):
                if not fn.lower().endswith('.png'):
                    continue
                item_id = fn[:-4]
                im = Image.open(os.path.join(folder, fn)).convert('RGBA')
                if im.width > 32 or im.height > 32:
                    log('items: AVISO %s/%s tem %dx%d (máximo 32x32); ignorado' % (slot, fn, im.width, im.height))
                    continue
                items[item_id] = {'slot': slot, 'rows': [''.join(CH[v] for v in row) for row in image_to_grid(im)]}
    for item_id in catalog_ids():
        if item_id not in items:
            log('items: AVISO %s sem arte em assets_src/items/sticker/; não aparece no bichinho' % item_id)
    os.makedirs(BUILD, exist_ok=True)
    with open(os.path.join(BUILD, 'items.json'), 'w', encoding='utf-8') as f:
        json.dump(items, f)
    log('items: %d item(ns)' % len(items))


# ---------------------------------------------------------------- pack
def rows_to_image(rows):
    return grid_to_image([[CH.index(c) for c in r] for r in rows])


def cmd_pack(cfg):
    with open(os.path.join(BUILD, 'frames.json'), encoding='utf-8') as f:
        fr = json.load(f)
    with open(os.path.join(BUILD, 'items.json'), encoding='utf-8') as f:
        items = json.load(f)
    W, H, frames = fr['width'], fr['height'], fr['frames']
    anims = rigmod.ANIMATIONS
    cols = max(a[3] for a in anims)
    # extras row: effects then items, packed left to right
    extras = [('effect', k, x, y, rows) for k, (x, y, rows) in rigmod.EFFECTS.items()]
    extras += [('item', k, None, None, v['rows']) for k, v in sorted(items.items())]
    ex_w = sum(len(e[4][0]) + 1 for e in extras)
    ex_h = max(len(e[4]) for e in extras)
    sheet = Image.new('RGBA', (max(cols * W, ex_w), len(anims) * H + ex_h), (0, 0, 0, 0))
    meta = {
        'version': 1,
        'frameWidth': W,
        'frameHeight': H,
        'tones': ['#%02X%02X%02X' % TONES[t] for t in (1, 2, 3, 4)],
        'animations': [],
        'effects': {},
        'items': {},
    }
    for r, (name, prefix, _, count, ms) in enumerate(anims):
        for i in range(count):
            sheet.alpha_composite(rows_to_image(frames['%s_%d' % (prefix, i)]), (i * W, r * H))
        meta['animations'].append({'name': name, 'frame': prefix, 'row': r, 'frames': count, 'frameMs': ms})
    x = 0
    y0 = len(anims) * H
    slots = cfg.get('items', {})
    for kind, key, ex, ey, rows in extras:
        w, h = len(rows[0]), len(rows)
        sheet.alpha_composite(rows_to_image(rows), (x, y0))
        if kind == 'effect':
            meta['effects'][key] = {'x': ex, 'y': ey, 'rect': [x, y0, w, h]}
        else:
            slot = items[key]['slot']
            sc = slots[slot]
            meta['items'][key] = {'slot': slot, 'anchor': sc.get('anchor', 'body'), 'x': sc.get('x', 0), 'y': sc.get('y', 0), 'rect': [x, y0, w, h]}
        x += w + 1
    os.makedirs(OUT_ASSETS, exist_ok=True)
    sheet.save(os.path.join(OUT_ASSETS, 'spritesheet.png'))
    with open(os.path.join(OUT_ASSETS, 'spritesheet.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(meta, f, indent=1, ensure_ascii=False)
        f.write('\n')
    log('pack: %s (%d x %d) e spritesheet.json' % (os.path.relpath(OUT_ASSETS, ROOT), sheet.width, sheet.height))
    write_kotlin(W, H, frames, items)
    contact_sheet(W, H, frames, items)


def write_kotlin(W, H, frames, items):
    """The same art baked into core (AvatarArt.kt): the fallback when the asset is missing, and what the JVM tests use."""
    out = ['''package com.ithalo.hybridcoach.core.avatar

/**
 * Pet avatar art, generated by tools/avatar/build.py (see tools/avatar/ARTE.md). Do not edit by hand.
 * The app loads the same art from assets/avatar/spritesheet.png; this copy is the fallback and what the tests use.
 * Each body frame is BODY_WIDTH x BODY_HEIGHT, four tones (`1` outline, `2` shade, `3` base, `4` highlight)
 * plus `.` transparent. Frames are named `<clip>_<n>`.
 *
 * Effects are sparse layers placed relative to the body by [LayerArt.x]/[LayerArt.y].
 * Stickers stand on the floor next to the pet.
 */
internal class LayerArt(val x: Int, val y: Int, val rows: List<String>)

internal object AvatarArt {
    const val BODY_WIDTH = %d
    const val BODY_HEIGHT = %d

    val bodies: Map<String, List<String>> = mapOf(
''' % (W, H)]
    for name, prefix, _, count, _ in rigmod.ANIMATIONS:
        for i in range(count):
            key = '%s_%d' % (prefix, i)
            out.append('        "%s" to listOf(\n' % key)
            out += ['            "%s",\n' % row for row in frames[key]]
            out.append('        ),\n')
    out.append('    )\n\n    val effects: Map<String, LayerArt> = mapOf(\n')
    for key, (x, y, rows) in rigmod.EFFECTS.items():
        out.append('        "%s" to LayerArt(%d, %d, listOf(\n' % (key, x, y))
        out += ['            "%s",\n' % row for row in rows]
        out.append('        )),\n')
    out.append('    )\n\n    val stickers: Map<String, List<String>> = mapOf(\n')
    for key, v in sorted(items.items()):
        if v['slot'] != 'sticker':
            continue
        out.append('        "%s" to listOf(\n' % key)
        out += ['            "%s",\n' % row for row in v['rows']]
        out.append('        ),\n')
    out.append('    )\n}\n')
    with open(OUT_KT, 'w', encoding='utf-8', newline='\n') as f:
        f.write(''.join(out))
    log('pack: %s' % os.path.relpath(OUT_KT, ROOT))


def contact_sheet(W, H, frames, items):
    """Every animation frame by frame at 8x on a dark screen, then each row again at 48 px tall (true phone size)."""
    S = 8
    small_h = 48
    small_w = round(W * small_h / H)
    pad, label = 16, 40
    anims = rigmod.ANIMATIONS
    cols = max(a[3] for a in anims)
    row_h = label + H * S + 8 + small_h + pad
    sheet = Image.new('RGB', (pad + cols * (W * S + pad), pad + len(anims) * row_h + label + 40), (20, 20, 18))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.load_default(size=28)
    except TypeError:  # Pillow < 10.1
        font = ImageFont.load_default()
    screen = (0xD4, 0xCC, 0xB8)
    y = pad
    for name, prefix, _, count, ms in anims:
        d.text((pad, y), '%s  (%d quadros, %d ms)' % (name, count, ms), fill=(230, 226, 214), font=font)
        for i in range(count):
            im = rows_to_image(frames['%s_%d' % (prefix, i)])
            big = Image.new('RGBA', (W, H), screen + (255,))
            big.alpha_composite(im)
            x = pad + i * (W * S + pad)
            sheet.paste(big.resize((W * S, H * S), Image.NEAREST), (x, y + label))
            sheet.paste(big.resize((small_w, small_h), Image.BOX), (pad + i * (small_w + 8), y + label + H * S + 8))
        y += row_h
    d.text((pad, y), 'itens: %d adesivos' % len(items), fill=(230, 226, 214), font=font)
    x = pad
    for key, v in sorted(items.items()):
        im = rows_to_image(v['rows'])
        bg = Image.new('RGBA', im.size, screen + (255,))
        bg.alpha_composite(im)
        sheet.paste(bg.resize((im.width * S, im.height * S), Image.NEAREST), (x, y + label))
        x += im.width * S + pad
    os.makedirs(os.path.dirname(CONTACT), exist_ok=True)
    sheet.save(CONTACT)
    log('pack: %s' % os.path.relpath(CONTACT, ROOT))


def main():
    ap = argparse.ArgumentParser(description='Pipeline de sprites do avatar')
    ap.add_argument('command', choices=['sprite', 'animate', 'items', 'pack', 'all'])
    args = ap.parse_args()
    cfg = load_rig()
    steps = ['sprite', 'animate', 'items', 'pack'] if args.command == 'all' else [args.command]
    for s in steps:
        globals()['cmd_' + s](cfg)
    log('pronto')


if __name__ == '__main__':
    main()
