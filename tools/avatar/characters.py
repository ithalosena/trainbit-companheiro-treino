"""Gato, pinguim e fantasma do bichinho, desenhados por código (arte original do projeto).

Cada personagem sai como uma folha no mesmo layout da folha do menino (app/src/main/assets/avatar/spritesheet.png):
uma linha por animação, 56x76 por quadro, 4 tons (1 contorno, 2 sombra, 3 base, 4 realce). O app só troca os
corpos; efeitos e adesivos são os do menino. A folha do menino define a ordem das linhas e quantos quadros cada uma tem.

    python tools/avatar/characters.py            # escreve assets/avatar/char_<id>.png e build/previews/avatar_characters.png

Em 1-bit (como o app desenha) o contorno vira tinta, a sombra vira xadrez e o resto fica vazio: por isso o corpo
dos bichos usa o tom 2 (pelo, costas) e a barriga/rosto usam o tom 3 (vazio).
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'avatar')
PREVIEWS = os.path.join(ROOT, 'build', 'previews')
W, H = 56, 76
CX = 28
GROUND = 72

with open(os.path.join(ASSETS, 'spritesheet.json'), encoding='utf-8') as f:
    META = json.load(f)
TONES = [tuple(int(t[i:i + 2], 16) for i in (1, 3, 5)) + (255,) for t in META['tones']]
OUT, SHADE, BASE, LIGHT = TONES


def pose_for(name, i):
    """Pose of frame i of the clip: the same clips and lengths the animator plays (core AvatarAnimator)."""
    p = dict(dy=0, lean=0, arm=(8, 8), leg=(0, 0), eyes='open', mouth='smile', sweat=False, bar=None, sway=0)
    if name in ('idle', 'blink'):
        p['dy'] = [0, -1, -1, 0][i]
        p['arm'] = (10, 10)
        if name == 'blink':
            p['eyes'] = 'closed'
    elif name == 'walk':
        p['dy'] = [0, -1, 0, -1][i]
        p['leg'] = [(0, 3), (0, 0), (3, 0), (0, 0)][i]
        p['arm'] = [(28, -14), (10, 10), (-14, 28), (10, 10)][i]
        p['sway'] = [-1, 0, 1, 0][i]
    elif name == 'flex':
        p['arm'] = [(100, 100), (125, 125), (100, 100), (125, 125)][i]
        p['eyes'] = 'happy'
        p['mouth'] = 'open'
    elif name == 'run':
        p['dy'] = [-1, 0, 0, -1, 0, 0][i]
        p['lean'] = 3
        p['leg'] = [(5, 0), (3, 0), (0, 0), (0, 5), (0, 3), (0, 0)][i]
        p['arm'] = [(55, -40), (30, -20), (-20, 30), (-40, 55), (-20, 30), (30, -20)][i]
        p['mouth'] = 'open'
    elif name == 'lift':
        p['bar'] = [8, 5, 2, 0, -1, 0][i]
        p['dy'] = [3, 2, 1, 0, 0, 0][i]
        p['arm'] = (165, 165)
        p['eyes'] = 'strain'
        p['mouth'] = 'grit'
        p['sweat'] = i >= 3
    elif name == 'cheer':
        p['dy'] = [0, -4, -6, -3, 0, -4][i]
        p['arm'] = [(150, 150), (165, 165), (170, 140), (140, 170), (160, 160), (170, 170)][i]
        p['eyes'] = 'happy'
        p['mouth'] = 'open'
    elif name == 'tired':
        p['dy'] = [2, 3, 3, 2][i]
        p['arm'] = (3, 3)
        p['eyes'] = 'half'
        p['mouth'] = 'flat'
        p['sweat'] = True
        p['lean'] = -1
    elif name == 'sluggish':
        p['dy'] = [1, 1, 2, 2][i]
        p['arm'] = (5, 5)
        p['eyes'] = 'half'
        p['mouth'] = 'flat'
    elif name == 'sleep':
        p['dy'] = [3, 4][i]
        p['arm'] = (4, 4)
        p['eyes'] = 'closed'
        p['mouth'] = 'flat'
        p['lean'] = 2
    return p


class Canvas:
    def __init__(self):
        self.img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def ellipse(self, cx, cy, rx, ry, fill=BASE, outline=OUT):
        self.d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)

    def poly(self, pts, fill=BASE, outline=OUT):
        self.d.polygon(pts, fill=fill, outline=outline)

    def limb(self, a, b, width=3, fill=BASE):
        self.d.line([a, b], fill=OUT, width=width + 2)
        self.d.line([a, b], fill=fill, width=width)

    def px(self, x, y, c=OUT):
        if 0 <= x < W and 0 <= y < H:
            self.img.putpixel((int(x), int(y)), c)


def arm_end(sx, sy, angle, side, length):
    """angle 0 = hanging down, 90 = out to the side, 180 = straight up; side -1 left, +1 right."""
    a = math.radians(angle)
    return sx + side * length * math.sin(a), sy + length * math.cos(a)


def eyes(c, y, dx, p, cx, big=False):
    """Eyes at (cx -/+ dx, y): hollow white with a pupil (open), lines (closed/happy/strain/half)."""
    r = 3 if big else 2
    for side in (-1, 1):
        x = cx + side * dx
        kind = p['eyes']
        if kind == 'open':
            c.ellipse(x, y, r, r + 1, fill=LIGHT)
            c.d.rectangle([x - 0 + (0 if side < 0 else -0), y, x, y + 1], fill=OUT)
        elif kind == 'closed':
            c.d.line([(x - r, y + 1), (x + r, y + 1)], fill=OUT, width=1)
        elif kind == 'happy':
            c.d.arc([x - r, y - 1, x + r, y + 3], 180, 360, fill=OUT)
        elif kind == 'strain':
            c.d.line([(x - r, y - 1), (x + r, y + 2)], fill=OUT)
            c.d.line([(x - r, y + 2), (x + r, y - 1)], fill=OUT)
        elif kind == 'half':
            c.ellipse(x, y + 1, r, r, fill=LIGHT)
            c.d.line([(x - r, y), (x + r, y)], fill=OUT)
            c.px(x, y + 1)


def mouth(c, x, y, p):
    m = p['mouth']
    if m == 'smile':
        c.d.arc([x - 3, y - 3, x + 3, y + 2], 20, 160, fill=OUT)
    elif m == 'open':
        c.ellipse(x, y, 2, 2, fill=OUT)
    elif m == 'grit':
        c.d.rectangle([x - 3, y - 1, x + 3, y + 1], fill=LIGHT, outline=OUT)
        c.d.line([(x, y - 1), (x, y + 1)], fill=OUT)
    else:
        c.d.line([(x - 2, y), (x + 2, y)], fill=OUT)


def sweat(c, x, y):
    c.d.polygon([(x, y), (x - 2, y + 4), (x + 2, y + 4)], fill=LIGHT, outline=OUT)


def barbell(c, y):
    c.d.line([(8, y), (48, y)], fill=OUT, width=2)
    for x in (6, 44):
        c.d.rectangle([x, y - 6, x + 5, y + 6], fill=OUT)
        c.d.rectangle([x + 1, y - 4, x + 4, y + 4], fill=SHADE)


def band(c, y, x0, x1, tail=True):
    c.d.line([(x0, y), (x1, y)], fill=OUT, width=2)
    if tail:
        c.d.line([(x1, y), (x1 + 4, y + 3)], fill=OUT)
        c.d.line([(x1, y + 1), (x1 + 3, y + 5)], fill=OUT)


# ---------------------------------------------------------------- characters

def cat(p):
    c = Canvas()
    dy, lean, sw = p['dy'], p['lean'], p['sway']
    hx = CX + lean + sw
    # tail
    tail = [(CX + 9, 58 + dy), (CX + 18, 54 + dy), (CX + 21, 46 + dy), (CX + 17, 40 + dy)]
    c.d.line(tail, fill=OUT, width=5)
    c.d.line(tail, fill=SHADE, width=3)
    # legs and feet
    for side, lift in ((-1, p['leg'][0]), (1, p['leg'][1])):
        x = CX + side * 5 + sw
        c.limb((x, 60 + dy), (x, GROUND - 3 - lift), 4, SHADE)
        c.ellipse(x + side, GROUND - 2 - lift, 4, 2, fill=SHADE)
    # body
    c.ellipse(hx, 48 + dy, 11, 15, fill=SHADE)
    c.ellipse(hx, 51 + dy, 6, 10, fill=BASE)
    # arms
    for side, ang in ((-1, p['arm'][0]), (1, p['arm'][1])):
        sx, sy = hx + side * 10, 40 + dy
        ex, ey = arm_end(sx, sy, ang, side, 11)
        c.limb((sx, sy), (ex, ey), 4, SHADE)
        c.ellipse(ex, ey, 2, 2, fill=BASE)
    # head: ears first
    for side in (-1, 1):
        c.poly([(hx + side * 12, 20 + dy), (hx + side * 10, 5 + dy), (hx + side * 3, 12 + dy)], fill=SHADE)
        c.poly([(hx + side * 10, 17 + dy), (hx + side * 9, 9 + dy), (hx + side * 5, 13 + dy)], fill=BASE, outline=BASE)
    c.ellipse(hx, 22 + dy, 14, 11, fill=SHADE)
    c.ellipse(hx, 27 + dy, 7, 5, fill=BASE)
    band(c, 15 + dy, hx - 13, hx + 13)
    eyes(c, 21 + dy, 6, p, hx)
    c.d.polygon([(hx - 2, 25 + dy), (hx + 2, 25 + dy), (hx, 27 + dy)], fill=OUT)
    mouth(c, hx, 29 + dy, p)
    for side in (-1, 1):
        for k in (0, 3):
            c.d.line([(hx + side * 9, 26 + dy + k), (hx + side * 17, 24 + dy + k * 2)], fill=OUT)
    if p['sweat']:
        sweat(c, hx + 17, 14 + dy)
    if p['bar'] is not None:
        barbell(c, 8 + p['bar'])
    return c.img


def penguin(p):
    c = Canvas()
    dy, lean, sw = p['dy'], p['lean'], p['sway']
    hx = CX + lean + sw * 2
    # feet
    for side, lift in ((-1, p['leg'][0]), (1, p['leg'][1])):
        c.ellipse(CX + side * 7 + sw, GROUND - 1 - lift, 5, 2, fill=SHADE)
    # body: dark back, light belly
    c.ellipse(hx, 48 + dy, 15, 22, fill=SHADE)
    c.ellipse(hx, 54 + dy, 10, 15, fill=BASE)
    # face patch with big eyes, then the band over the forehead
    c.ellipse(hx, 33 + dy, 11, 8, fill=BASE)
    band(c, 26 + dy, hx - 14, hx + 14)
    eyes(c, 32 + dy, 5, p, hx, big=True)
    c.poly([(hx - 4, 37 + dy), (hx + 4, 37 + dy), (hx, 42 + dy)], fill=SHADE)
    if p['mouth'] in ('open', 'grit'):
        c.d.line([(hx - 2, 44 + dy), (hx + 2, 44 + dy)], fill=OUT)
    # flippers in front of the body, so they read when hanging and when raised
    for side, ang in ((-1, p['arm'][0]), (1, p['arm'][1])):
        sx, sy = hx + side * 12, 42 + dy
        ex, ey = arm_end(sx, sy, max(ang, 14), side, 13)
        c.limb((sx, sy), (ex, ey), 5, SHADE)
    if p['sweat']:
        sweat(c, hx + 17, 24 + dy)
    if p['bar'] is not None:
        barbell(c, 10 + p['bar'])
    return c.img


def ghost(p):
    c = Canvas()
    dy, lean, sw = p['dy'] - 4, p['lean'], p['sway']
    hx = CX + lean + sw
    # shadow on the floor, smaller when it floats higher
    r = 11 + dy // 2
    for x in range(CX - r, CX + r + 1):
        for y in (GROUND - 1, GROUND):
            if (x + y) % 2 == 0:
                c.px(x, y)
    # arms (stubs)
    for side, ang in ((-1, p['arm'][0]), (1, p['arm'][1])):
        sx, sy = hx + side * 12, 40 + dy
        ex, ey = arm_end(sx, sy, ang, side, 10)
        c.limb((sx, sy), (ex, ey), 4, BASE)
    # sheet: dome + straight sides + scalloped hem
    hem = 62 + dy + (1 if p['leg'][0] or p['leg'][1] else 0)
    pts = [(hx - 15, 30 + dy)]
    pts += [(hx - 15, hem)]
    n = 5
    for k in range(n):
        x0 = hx - 15 + k * 6
        pts += [(x0 + 3, hem + 5 + (1 if (k + sw) % 2 else 0)), (x0 + 6, hem)]
    pts += [(hx + 15, 30 + dy)]
    c.ellipse(hx, 30 + dy, 15, 15, fill=BASE)
    c.poly(pts, fill=BASE)
    c.d.rectangle([hx - 14, 30 + dy, hx + 14, hem], fill=BASE)
    # re-draw the side lines (the rectangle covered the outline)
    c.d.line([(hx - 15, 30 + dy), (hx - 15, hem)], fill=OUT)
    c.d.line([(hx + 15, 30 + dy), (hx + 15, hem)], fill=OUT)
    # shade on the right side
    for y in range(24 + dy, hem + 4):
        for x in range(hx + 9, hx + 15):
            if c.img.getpixel((int(x), int(y)))[:3] == BASE[:3] and 0 <= y < H:
                c.px(x, y, SHADE)
    band(c, 21 + dy, hx - 14, hx + 14)
    # big solid eyes
    k = p['eyes']
    for side in (-1, 1):
        x = hx + side * 6
        if k in ('open',):
            c.ellipse(x, 29 + dy, 3, 4, fill=OUT)
            c.px(x - 1, 27 + dy, LIGHT)
        else:
            eyes(c, 29 + dy, 6, p, hx)
            break
    mouth(c, hx, 38 + dy, p)
    if p['sweat']:
        sweat(c, hx + 18, 20 + dy)
    if p['bar'] is not None:
        barbell(c, 8 + p['bar'])
    return c.img


CHARACTERS = {'cat': ('Gato', cat), 'penguin': ('Pinguim', penguin), 'ghost': ('Fantasma', ghost)}


def sheet(draw):
    anims = META['animations']
    cols = max(a['frames'] for a in anims)
    img = Image.new('RGBA', (cols * W, len(anims) * H), (0, 0, 0, 0))
    for a in anims:
        for i in range(a['frames']):
            img.alpha_composite(draw(pose_for(a['frame'], i)), (i * W, a['row'] * H))
    return img


def one_bit(frame):
    """What the app draws: outline = ink, shade = checkerboard, everything else empty."""
    out = Image.new('RGB', frame.size, (0, 0, 0))
    px = frame.load()
    o = out.load()
    for y in range(frame.height):
        for x in range(frame.width):
            r, g, b, a = px[x, y]
            if a < 128:
                continue
            tone = min(range(4), key=lambda t: sum((TONES[t][k] - (r, g, b)[k]) ** 2 for k in range(3)))
            if tone == 0 or (tone == 1 and (x + y) % 2 == 0):
                o[x, y] = (255, 255, 255)
    return out


def main():
    os.makedirs(PREVIEWS, exist_ok=True)
    names = ['idle_0', 'idle_2', 'walk_1', 'flex_1', 'run_2', 'lift_3', 'cheer_2', 'tired_1', 'sluggish_0', 'sleep_1']
    scale = 4
    preview = Image.new('RGB', (len(names) * (W * scale + 6) + 6, len(CHARACTERS) * (H * scale + 6) + 6), (30, 31, 27))
    for r, (cid, (label, draw)) in enumerate(CHARACTERS.items()):
        s = sheet(draw)
        s.save(os.path.join(ASSETS, 'char_%s.png' % cid))
        for c, n in enumerate(names):
            prefix, idx = n.rsplit('_', 1)
            row = next(a['row'] for a in META['animations'] if a['frame'] == prefix)
            frame = s.crop((int(idx) * W, row * H, int(idx) * W + W, row * H + H))
            tile = one_bit(frame).resize((W * scale, H * scale), Image.NEAREST)
            preview.paste(tile, (6 + c * (W * scale + 6), 6 + r * (H * scale + 6)))
        print('char_%s.png' % cid)
    preview.save(os.path.join(PREVIEWS, 'avatar_characters.png'))


if __name__ == '__main__':
    sys.exit(main())
