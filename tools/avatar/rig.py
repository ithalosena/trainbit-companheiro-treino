"""Cut-out rig: poses the avatar from its sprite grid.

The head, the shoes, the shirt logo and the flexed arm are cut from the sprite (regions in avatar_rig.json);
the torso, arms and legs are drawn here in the same style (light fill, 1 px dark outline). Every pose function
returns a Canvas of tones: 0 transparent, 1 outline, 2 shade, 3 base, 4 highlight.
"""
import math


class Rig:
    def __init__(self, grid, cfg):
        """[grid] is the sprite as rows of tones (0..4); [cfg] is avatar_rig.json."""
        self.grid = grid
        self.cfg = cfg
        cv = cfg['canvas']
        self.W, self.H = cv['width'], cv['height']
        self.CX, self.GROUND = cv['center_x'], cv['ground']
        self.PX, self.PY = cv['place']  # where the sprite grid sits on the canvas when standing
        self.LA = (self.CX - 6, self.GROUND - 8)  # ankles when standing
        self.RA = (self.CX + 8, self.GROUND - 8)

        g = self.cell
        hx0, hy0, hx1, hy1 = cfg['head']
        self.head_box = (hx0, hy0)
        face = cfg['face']
        in_face = lambda x, y: face[0] <= x <= face[2] and face[1] <= y <= face[3]
        self.HEAD = shade_skin(shade_interior(self.piece(*cfg['head']), skip=lambda x, y: in_face(x + hx0, y + hy0)), xmin=cfg['skin_shade_from_x'] - hx0)
        self.NECK = self.piece(*cfg['neck'], keep=lambda x, y: g(x, y) == 4)
        self.LOGO = self.piece(*cfg['logo'], keep=lambda x, y: g(x, y) == 4)
        self.SHOE_L = shade_interior(self.piece(*cfg['shoes']['left']))
        self.SHOE_R = shade_interior(self.piece(*cfg['shoes']['right']))
        fa = cfg['flex_arm']
        bx0, by0, bx1, by1 = fa['box']
        arm, seen, stack = {}, set(), [tuple(fa['seed'])]
        while stack:  # the flexed arm: cells reachable from the fist inside its box
            x, y = stack.pop()
            if (x, y) in seen or not (bx0 <= x <= bx1 and by0 <= y <= by1) or g(x, y) == 0:
                continue
            seen.add((x, y))
            arm[(x, y)] = g(x, y)
            stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
        self.FLEX_ARM = shade_skin(arm, xmin=0)
        sx0, sy0, sx1, sy1 = cfg['sparks']
        self.SPARKS = {(x, y): 1 for y in range(sy0, sy1 + 1) for x in range(sx0, sx1 + 1) if g(x, y) == 1 and (x, y) not in arm}
        self.neck_at = (cfg['neck'][0], cfg['neck'][1])

    # ------------------------------------------------------------ sprite access
    def cell(self, x, y):
        if 0 <= y < len(self.grid) and 0 <= x < len(self.grid[y]):
            return self.grid[y][x]
        return 0

    def piece(self, x0, y0, x1, y1, keep=None):
        """Cells of the sprite in [x0..x1]x[y0..y1] as {(dx, dy): tone}, relative to (x0, y0)."""
        out = {}
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                v = self.cell(x, y)
                if v and (keep is None or keep(x, y)):
                    out[(x - x0, y - y0)] = v
        return out

    def canvas(self):
        return Canvas(self.W, self.H)

    # ------------------------------------------------------------ faces
    def face(self, kind):
        """A copy of the head with the mouth changed for [kind] (coordinates relative to the mouth box)."""
        h = dict(self.HEAD)
        hx0, hy0 = self.head_box
        mx, my = self.cfg['mouth'][0] - hx0, self.cfg['mouth'][1] - hy0

        def setr(y, x0, x1, v):
            for x in range(x0, x1 + 1):
                if v:
                    h[(mx + x, my + y)] = v
                else:
                    h.pop((mx + x, my + y), None)

        if kind == 'grin':  # wide open smile
            setr(0, 0, 7, 1)
            setr(1, 0, 7, 1); setr(1, 1, 6, 4)
            setr(2, 0, 7, 1)
            setr(3, 1, 6, 1); setr(3, 0, 0, 4); setr(3, 7, 7, 4)
            setr(4, 2, 5, 1)
        elif kind in ('sleep', 'sleepy'):  # small closed mouth
            setr(0, 0, 7, 4); setr(1, 0, 7, 4); setr(2, 0, 7, 4); setr(3, 1, 6, 4)
            setr(2, 3, 4, 1)
        elif kind == 'yawn':  # a big round "O"
            setr(0, 0, 7, 4); setr(1, 0, 7, 4); setr(2, 0, 7, 4); setr(3, 0, 7, 4)
            setr(0, 2, 5, 1)
            for r in (1, 2, 3):
                setr(r, 1, 1, 1); setr(r, 2, 5, 2); setr(r, 6, 6, 1)
            setr(4, 2, 5, 1)
        elif kind == 'strain':  # gritted teeth
            setr(0, 0, 7, 1); setr(1, 0, 7, 1); setr(1, 1, 6, 4)
            for x in (2, 4, 6):
                h[(mx + x, my + 1)] = 1
            setr(2, 0, 7, 1)
        elif kind == 'tired':  # open, panting mouth
            setr(0, 1, 6, 4)
            setr(1, 1, 6, 1)
            setr(2, 1, 6, 1)
            setr(3, 2, 5, 1)
        return h

    def eyes_overlay(self, c, ox, oy, kind):
        """Redrawn eyes for the expressions (the sprite's eyes are wiped to skin first). Relative to the eye boxes."""
        hx0, hy0 = self.head_box
        L = [v - o for v, o in zip(self.cfg['eyes']['left'], (hx0, hy0, hx0, hy0))]
        R = [v - o for v, o in zip(self.cfg['eyes']['right'], (hx0, hy0, hx0, hy0))]

        def p(x, y, v=1):
            c.put(ox + x, oy + y, v)

        def line(a, b):
            (x0, y0), (x1, y1) = a, b
            n = max(abs(x1 - x0), abs(y1 - y0)) or 1
            for i in range(n + 1):
                p(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n))

        if kind in ('strain', 'tired', 'sleep', 'blink', 'sleepy', 'yawn'):
            for E in (L, R):
                for y in range(E[1], E[3] + 1):
                    for x in range(E[0], E[2] + 1):
                        p(x, y, 4)
        if kind == 'strain':  # > <, two pixels thick
            for d in (0, 1):
                line((L[0] + d, L[1]), (L[0] + 4 + d, L[1] + 3)); line((L[0] + 4 + d, L[1] + 3), (L[0] + d, L[1] + 6))
                line((R[2] - d, R[1]), (R[2] - 4 - d, R[1] + 3)); line((R[2] - 4 - d, R[1] + 3), (R[2] - d, R[1] + 6))
        if kind in ('tired', 'sleepy'):  # heavy lids, pupils half hidden
            for E, px in ((L, 2), (R, 1)):
                for x in range(E[0], E[2] + 1):
                    p(x, E[1] + 3)
                for x in range(E[0] + px, E[0] + px + 3):
                    p(x, E[1] + 4); p(x, E[1] + 5)
        if kind in ('sleep', 'blink', 'yawn'):  # closed eyes as small arcs
            for E in (L, R):
                p(E[0], E[1] + 3); p(E[0] + 4, E[1] + 3)
                for x in range(E[0] + 1, E[0] + 4):
                    p(x, E[1] + 4)
        if kind == 'determined':  # angry brows over the normal eyes
            for d in (0, 1):
                line((L[0] - 1, L[1] - 2 + d), (L[0] + 4, L[1] + d))
                line((R[2] + 1, R[1] - 2 + d), (R[2] - 4, R[1] + d))

    # ------------------------------------------------------------ body parts
    def torso(self, c, dy=0, lean=0, squash=0):
        CX = self.CX
        top = 37 + dy + squash
        hip = 58 + dy
        sh = poly([(CX - 12 + lean, top + 2), (CX - 9 + lean, top), (CX + 9 + lean, top), (CX + 12 + lean, top + 2),
                   (CX + 10 + lean * .5, 49 + dy), (CX - 10 + lean * .5, 49 + dy)])
        shorts = poly([(CX - 10 + lean * .5, 49 + dy), (CX + 10 + lean * .5, 49 + dy), (CX + 11, hip), (CX + 1, hip),
                       (CX, hip - 3), (CX - 1, hip), (CX - 11, hip)])
        c.mask(sh | shorts, 2)
        for (x, y) in shorts:  # the waistband a step darker
            if y == 49 + dy:
                c.put(x, y, 1)
        for i in range(4):  # a fold on the shirt
            c.put(CX + 5 + lean * .7, 44 + dy + i, 1)
        for i in range(6):  # stripes on the shorts
            c.put(CX - 6 - i * .5 + lean * .5, 51 + dy + i, 4)
            c.put(CX + 8 + i * .5 + lean * .5, 51 + dy + i, 4)
        c.piece(self.LOGO, CX - 12 + lean * .8, 41 + dy + squash)  # a little left of centre, as in the drawing
        return (CX - 11 + lean, top + 3), (CX + 11 + lean, top + 3)

    def arm(self, c, shoulder, elbow, hand, fist=2.8):
        c.mask(capsule(shoulder, elbow, 2.4) | capsule(elbow, hand, 2.1), 4, shade=3)
        c.mask(disc(hand[0], hand[1], fist), 4, shade=3)
        c.put(hand[0], hand[1], 1)  # knuckle line

    def leg(self, c, hip, ankle, shoe):
        c.mask(capsule(hip, ankle, 2.3), 4, shade=3)
        if shoe == 'L':
            c.piece(self.SHOE_L, ankle[0] - 9, ankle[1] - 1)
        else:
            c.piece(self.SHOE_R, ankle[0] - 4, ankle[1] - 1)

    def head(self, c, dx=0, dy=0, kind=None):
        ox, oy = self.PX + self.head_box[0] + dx, self.PY + self.head_box[1] + dy
        c.piece(self.NECK, self.PX + self.neck_at[0] + dx, self.PY + self.neck_at[1] + dy)
        c.piece(self.face(kind) if kind else self.HEAD, ox, oy)
        if kind in ('strain', 'tired', 'determined', 'sleep', 'blink', 'sleepy', 'yawn'):
            self.eyes_overlay(c, ox, oy, kind)
        if kind == 'tired':  # tongue hanging out, under the mouth box
            mx, my = self.cfg['mouth'][0] - self.head_box[0] + ox, self.cfg['mouth'][1] - self.head_box[1] + oy
            for (x, y, v) in ((2, 4, 1), (3, 4, 4), (4, 4, 4), (5, 4, 1), (2, 5, 1), (3, 5, 4), (4, 5, 4), (5, 5, 1), (3, 6, 1), (4, 6, 1)):
                c.put(mx + x, my + y, v)

    def stand_legs(self, c, dy=0, la=None, ra=None):
        self.leg(c, (self.CX - 5, 57 + dy), la or self.LA, 'L')
        self.leg(c, (self.CX + 6, 57 + dy), ra or self.RA, 'R')

    # ------------------------------------------------------------ poses
    def idle(self, i, kind=None):
        b = [0, 1, 1, 0][i]  # breathing bounce
        c = self.canvas()
        CX = self.CX
        self.stand_legs(c, b)
        ls, rs = self.torso(c, b, squash=-1 if b == 0 else 0)
        self.head(c, 0, b, kind)
        self.arm(c, ls, (ls[0] - 6, 47 + b), (CX - 9, 52 + b))  # hands on hips
        self.arm(c, rs, (rs[0] + 6, 47 + b), (CX + 10, 52 + b))
        return c

    def blink(self, i):
        return self.idle(i, 'blink')

    def flex(self, i):
        pump = [0, 1, 0, 1][i]
        c = self.canvas()
        CX = self.CX
        self.stand_legs(c)
        ls, rs = self.torso(c)
        self.head(c, 0, 0, 'grin')
        self.arm(c, ls, (ls[0] - 6, 47), (CX - 9, 52))
        c.piece(self.FLEX_ARM, self.PX, self.PY + pump)
        if i % 2 == 0:
            c.piece(self.SPARKS, self.PX, self.PY)
        else:
            c.piece(self.SPARKS, self.PX + 1, self.PY - 1)
        return c

    def run(self, i):
        ph = 2 * math.pi * i / 6
        b = [0, 1, 2, 1, 0, 1][i]
        c = self.canvas()
        CX, G = self.CX, self.GROUND
        lean = 6
        # legs: a cartoon wheel of motion (dashed arcs) with the two shoes whirling on it
        cx, cy, rx, ry = CX + 2, G - 9, 9, 6
        for ring, gap in ((1.0, 0), (0.6, 1)):
            for k in range(28):
                a = 2 * math.pi * k / 28 + ph * (1 if ring == 1.0 else -1)
                if (k + gap) % 4 != 0:
                    x, y = cx + math.cos(a) * rx * ring, cy + math.sin(a) * ry * ring
                    c.put(x, y, 1)
                    if ring == 1.0:
                        c.put(x + 1, y, 1)
        for off, shoe in ((0, 'L'), (math.pi, 'R')):
            a = -ph * 2 + off
            ax, ay = cx + math.cos(a) * rx * .8, cy + math.sin(a) * ry * .8
            c.mask(capsule((CX + (-2 if shoe == 'L' else 5), 57 - b), (ax, ay - 1), 2.0), 4)
            if shoe == 'L':
                c.piece(self.SHOE_L, ax - 9, ay - 2)
            else:
                c.piece(self.SHOE_R, ax - 4, ay - 2)
        for k in range(4):  # dust puffs behind
            x, y = 6 + k * 3 + (i % 2), G - 1 - (k % 2) * 2
            c.put(x, y, 1); c.put(x + 1, y, 1)
        ls, rs = self.torso(c, -b, lean=lean)
        self.head(c, lean + 2, -b + 1, 'determined')
        sw = math.sin(ph)
        self.arm(c, ls, (ls[0] - 4 - sw * 3, 46 - b), (ls[0] - 1 - sw * 7, 50 - b - abs(sw) * 4))
        self.arm(c, rs, (rs[0] + 3 + sw * 3, 47 - b), (rs[0] + 7 + sw * 4, 41 - b))
        for k, y in enumerate((30, 40, 50, 60)):  # speed lines
            x0 = 1 + ((i + k) % 3)
            for x in range(x0, x0 + 6 - (k % 2) * 2):
                c.put(x, y, 1)
        return c

    def lift(self, i):
        """0-1 pull up, 2-5 overhead with a wobble."""
        c = self.canvas()
        CX = self.CX
        shake = [0, 0, 1, -1, 1, 0][i]
        self.stand_legs(c, 1, la=(self.LA[0] - 2, self.LA[1]), ra=(self.RA[0] + 2, self.RA[1]))
        ls, rs = self.torso(c, 1)
        if i < 2:
            hy = [46, 34][i]
            dumbbell(c, CX + shake, hy, 0)
            self.arm(c, ls, (ls[0] - 4, hy + 4), (CX - 13, hy))
            self.arm(c, rs, (rs[0] + 4, hy + 4), (CX + 13, hy))
            self.head(c, 0, 1, 'strain')
            if i == 1:
                dumbbell(c, CX, hy, 0)
        else:
            wob = [1, -1, 2, -1][i - 2]
            hy = 10 + abs(wob)
            self.head(c, shake, 2, 'strain')
            self.arm(c, ls, (ls[0] - 5, 30), (CX - 12, hy + 2 + wob))
            self.arm(c, rs, (rs[0] + 5, 30), (CX + 12, hy + 2 - wob))
            dumbbell(c, CX + shake, hy, wob)
            # sweat flying off to the sides, clear of the raised arms
            drop(c, 2 + (i % 2), 30 + (i - 2) * 2)
            drop(c, 50, 28 + ((i + 1) % 3) * 2)
        return c

    def celebrate(self, i):
        """Crouch, jump up, peak, land, with stars."""
        jy = [0, -6, -9, -5, 0, 0][i]
        tuck = [0, 2, 3, 2, 0, 0][i]
        crouch = [2, 0, 0, 0, 2, 0][i]
        c = self.canvas()
        CX = self.CX
        self.stand_legs(c, jy + crouch, la=(self.LA[0] - tuck, self.LA[1] + jy - tuck), ra=(self.RA[0] + tuck, self.RA[1] + jy - tuck))
        ls, rs = self.torso(c, jy + crouch)
        self.head(c, 0, jy + crouch, 'grin')
        if jy < 0:
            self.arm(c, ls, (ls[0] - 5, ls[1] - 6), (ls[0] - 7, ls[1] - 13))
            self.arm(c, rs, (rs[0] + 5, rs[1] - 6), (rs[0] + 7, rs[1] - 13))
        else:
            self.arm(c, ls, (ls[0] - 5, 48 + crouch), (CX - 13, 51 + crouch))
            self.arm(c, rs, (rs[0] + 5, 48 + crouch), (CX + 13, 51 + crouch))
        for k, (x, y) in enumerate([(4, 10), (50, 8), (3, 40), (52, 36), (10, 2), (46, 50)]):
            if (k + i) % 2 == 0:
                star(c, x, y, big=(k + i) % 3 != 0)
        return c

    def slump(self, i, kind, sweat):
        """Slouched with hands on the knees: tired (panting, sweat) and sluggish (yawning)."""
        b = [0, 1, 2, 1][i]
        c = self.canvas()
        CX = self.CX
        self.stand_legs(c, 3, la=(self.LA[0] - 1, self.LA[1]), ra=(self.RA[0] + 1, self.RA[1]))
        ls, rs = self.torso(c, 3 + b // 2, squash=1)
        self.head(c, 0, 6 + b, kind)
        self.arm(c, ls, (ls[0] - 3, 50 + b), (CX - 7, 59))
        self.arm(c, rs, (rs[0] + 3, 50 + b), (CX + 9, 59))
        if sweat:
            paste(c, BIG_DROP, 41, 10 + b)
        return c

    def tired(self, i):
        return self.slump(i, 'tired', sweat=True)

    def sluggish(self, i):
        return self.slump(i, ['sleepy', 'yawn', 'yawn', 'sleepy'][i], sweat=False)

    def walk(self, i):
        lift = [(2, 0), (0, 0), (0, 2), (0, 0)][i]  # (left, right) foot raised
        b = [1, 0, 1, 0][i]
        c = self.canvas()
        CX = self.CX
        self.leg(c, (CX - 5, 57 - b), (self.LA[0] + 1, self.LA[1] - lift[0]), 'L')
        self.leg(c, (CX + 6, 57 - b), (self.RA[0] - 1, self.RA[1] - lift[1]), 'R')
        ls, rs = self.torso(c, -b)
        self.head(c, 0, -b)
        sw = [1, 0, -1, 0][i]
        self.arm(c, ls, (ls[0] - 3, 47 - b), (ls[0] - 3 + sw, 53 - b - sw * 2))
        self.arm(c, rs, (rs[0] + 3, 47 - b), (rs[0] + 3 - sw, 53 - b + sw * 2))
        return c

    def sleep(self, i):
        b = [0, 1][i]
        c = self.canvas()
        CX = self.CX
        self.stand_legs(c, 3, la=(self.LA[0] - 1, self.LA[1]), ra=(self.RA[0] + 1, self.RA[1]))
        ls, rs = self.torso(c, 3 + b, squash=1)
        self.head(c, 0, 7 + b, 'sleep')
        self.arm(c, ls, (ls[0] - 3, 51 + b), (CX - 7, 59))
        self.arm(c, rs, (rs[0] + 3, 51 + b), (CX + 9, 59))
        return c


# ---------------------------------------------------------------- drawing helpers
class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.g = [[0] * w for _ in range(h)]

    def put(self, x, y, v):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < self.w and 0 <= y < self.h:
            self.g[y][x] = v

    def piece(self, cells, ox, oy):
        for (dx, dy), v in cells.items():
            self.put(ox + dx, oy + dy, v)

    def mask(self, m, fill, shade=None):
        """1 px dark ring around [m], then [m] filled with [fill]; [shade] tints its right edge."""
        for (x, y) in m:
            for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + ax, y + ay) not in m:
                    self.put(x + ax, y + ay, 1)
        for (x, y) in m:
            self.put(x, y, shade if shade and (x + 1, y) not in m else fill)


def shade_interior(cells, skip=lambda x, y: False):
    """Dark cells fully surrounded by dark become the shade tone (2): big dark areas read as shapes with an outline."""
    out = dict(cells)
    for (x, y), v in cells.items():
        if v == 1 and not skip(x, y) and all(cells.get((x + ax, y + ay)) == 1 for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            out[(x, y)] = 2
    return out


def shade_skin(cells, xmin):
    """Light cells touching the outline on their right get the soft tone (3): the light comes from the left."""
    out = dict(cells)
    for (x, y), v in cells.items():
        if v == 4 and x >= xmin and cells.get((x + 1, y), 0) in (0, 1):
            out[(x, y)] = 3
    return out


def capsule(a, b, r):
    (ax, ay), (bx, by) = a, b
    m = set()
    for y in range(int(min(ay, by) - r - 1), int(max(ay, by) + r + 2)):
        for x in range(int(min(ax, bx) - r - 1), int(max(ax, bx) + r + 2)):
            dx, dy = bx - ax, by - ay
            l2 = dx * dx + dy * dy
            t = 0 if l2 == 0 else max(0, min(1, ((x + .5 - ax) * dx + (y + .5 - ay) * dy) / l2))
            if math.hypot(x + .5 - (ax + t * dx), y + .5 - (ay + t * dy)) <= r:
                m.add((x, y))
    return m


def disc(cx, cy, r):
    return {(x, y) for y in range(int(cy - r - 1), int(cy + r + 2)) for x in range(int(cx - r - 1), int(cx + r + 2))
            if math.hypot(x + .5 - cx, y + .5 - cy) <= r}


def poly(points):
    """Filled polygon (even-odd scanline)."""
    m = set()
    ys = [p[1] for p in points]
    for y in range(int(min(ys)), int(max(ys)) + 1):
        yc = y + .5
        xs = []
        for i in range(len(points)):
            (x1, y1), (x2, y2) = points[i], points[(i + 1) % len(points)]
            if (y1 <= yc < y2) or (y2 <= yc < y1):
                xs.append(x1 + (yc - y1) * (x2 - x1) / (y2 - y1))
        xs.sort()
        for i in range(0, len(xs) - 1, 2):
            for x in range(int(round(xs[i])), int(round(xs[i + 1]))):
                m.add((x, y))
    return m


# ---------------------------------------------------------------- effects (small grids)
TONE = {'.': 0, '1': 1, '2': 2, '3': 3, '4': 4}

BIG_DROP = [
    "...1...",
    "...1...",
    "..141..",
    "..141..",
    ".14441.",
    "1444441",
    "1444441",
    "1414441",
    "1441441",
    ".14441.",
    "..111..",
]
SMALL_DROP = [
    ".1..",
    "1.1.",
    "141.",
    "1441",
    "1441",
    ".11.",
]
Z_SMALL = ["1111", "..1.", ".1..", "1111"]
Z_BIG = ["111111", "....1.", "...1..", "..1...", ".1....", "111111"]


def paste(c, rows, x, y):
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != '.':
                c.put(x + dx, y + dy, TONE[ch])


def star(c, x, y, big=True):
    pts = [(0, -2), (0, -1), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (0, 1), (0, 2)] if big else [(0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]
    for dx, dy in pts:
        c.put(x + dx, y + dy, 1)


def drop(c, x, y):
    paste(c, SMALL_DROP, x - 1, y)


def dumbbell(c, cx, cy, wob=0):
    """Oversized cartoon dumbbell: a long bar and two fat plates with a shine."""
    c.mask(capsule((cx - 15, cy + wob), (cx + 15, cy - wob), 1.0), 4)
    for side in (-1, 1):
        px, py = cx + side * 17, cy - side * wob
        c.mask(disc(px, py, 7.0), 1)
        c.mask(disc(px + side * 1, py, 3.2), 1)
        for (dx, dy) in ((-3, -4), (-2, -5), (-4, -3)):
            c.put(px + dx * side, py + dy, 4)
        c.put(px, py, 4)


# ---------------------------------------------------------------- the animation list
# name in the app (frames are "<frame prefix>_<n>"), frame prefix, rig pose, frame count, ms per frame
ANIMATIONS = [
    ("idle", "idle", "idle", 4, 400),
    ("blink", "blink", "blink", 4, 400),  # shown one tick at a time, in the phase of idle
    ("walk", "walk", "walk", 4, 200),
    ("flex", "flex", "flex", 4, 200),
    ("train_run", "run", "run", 6, 200),
    ("train_strength", "lift", "lift", 6, 200),
    ("celebrate", "cheer", "celebrate", 6, 200),
    ("tired", "tired", "tired", 4, 400),
    ("sluggish", "sluggish", "sluggish", 4, 600),
    ("sleeping", "sleep", "sleep", 2, 800),
]
EFFECTS = {"z_a": (43, 22, Z_SMALL), "z_b": (45, 12, Z_BIG)}
