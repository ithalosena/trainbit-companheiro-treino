"""Exercise photos in pixel style.

    python tools/exercises/build_photos.py

For each exercise in tools/exercises/mapping.json, takes the start and end photos of the free-exercise-db
(github.com/yuhonas/free-exercise-db, Unlicense / public domain), downloads them once into tools/exercises/.cache,
and turns them into a "Game Boy Camera" frame: grayscale, equalized, sharpened, reduced to FRAME_W x FRAME_H and
posterized to LEVELS tones (no dithering). The app paints the tones with the theme colours and alternates the
two frames, like a 2-frame GIF.

Outputs:
- app/src/main/assets/exercise_frames/<id>.png: the two frames side by side, tone i stored as gray i * 255/(LEVELS-1);
- build/previews/exercise_photos_sheet.png: every exercise, both frames, for review;
- build/previews/exercise_gifs/<id>.gif: the same animation as a GIF, only as a preview (not used by the app).

Requires Pillow (pip install pillow) and internet access the first time.
"""
import json
import os
import urllib.request

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, '.cache')
DB_URL = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json'
IMG_URL = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/'
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'exercise_frames')
SHEET = os.path.join(ROOT, 'build', 'previews', 'exercise_photos_sheet.png')
GIFS = os.path.join(ROOT, 'build', 'previews', 'exercise_gifs')

FRAME_W, FRAME_H = 128, 85
LEVELS = 5
# Preview colours: the BEGE light ramp, from ink to highlight.
INK, HIGHLIGHT = (0x3E, 0x3D, 0x36), (0xEA, 0xE4, 0xD4)
FRAME_MS = 800


def fetch(url, path):
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        req = urllib.request.Request(url, headers={'User-Agent': 'hybrid-coach-tools'})
        with urllib.request.urlopen(req, timeout=60) as r, open(path, 'wb') as f:
            f.write(r.read())
    return path


def levels(photo):
    """The photo as a FRAME_W x FRAME_H grid of tone levels 0 (dark) .. LEVELS-1 (light)."""
    im = Image.open(photo).convert('L')
    im = ImageOps.equalize(im)  # spreads the tones, so the athlete stands out from walls and floor
    im = im.filter(ImageFilter.UnsharpMask(radius=3, percent=150, threshold=3))
    im = ImageOps.fit(im, (FRAME_W * 4, FRAME_H * 4), Image.LANCZOS).resize((FRAME_W, FRAME_H), Image.BOX)
    return im.point(lambda v: min(LEVELS - 1, int(round(v / 255 * (LEVELS - 1)))))


def colour(level):
    t = level / (LEVELS - 1)
    return tuple(round(INK[c] + (HIGHLIGHT[c] - INK[c]) * t) for c in range(3))


def preview(frame):
    """A level grid in the preview colours."""
    return Image.merge('RGB', [frame.point(lambda v, c=c: colour(v)[c]) for c in range(3)])


def main():
    mapping = {k: v for k, v in json.load(open(os.path.join(HERE, 'mapping.json'), encoding='utf-8')).items() if not k.startswith('_')}
    db = {e['name']: e for e in json.load(open(fetch(DB_URL, os.path.join(CACHE, 'exercises.json')), encoding='utf-8'))}
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(GIFS, exist_ok=True)
    done = []
    for ex_id, name in sorted(mapping.items()):
        entry = db.get(name)
        if not entry or len(entry.get('images', [])) < 2:
            print('AVISO sem fotos: %s (%s)' % (ex_id, name))
            continue
        frames = [levels(fetch(IMG_URL + rel, os.path.join(CACHE, 'img', rel.replace('/', '_')))) for rel in entry['images'][:2]]
        strip = Image.new('L', (FRAME_W * 2, FRAME_H))
        for i, f in enumerate(frames):
            strip.paste(f.point(lambda v: round(v * 255 / (LEVELS - 1))), (i * FRAME_W, 0))
        strip.save(os.path.join(OUT, '%s.png' % ex_id), optimize=True)
        gif = [preview(f).resize((FRAME_W * 3, FRAME_H * 3), Image.NEAREST) for f in frames]
        gif[0].save(os.path.join(GIFS, '%s.gif' % ex_id), save_all=True, append_images=gif[1:], duration=FRAME_MS, loop=0)
        done.append((ex_id, frames))
    contact(done)
    print('%d exercícios com fotos em %s' % (len(done), os.path.relpath(OUT, ROOT)))


def contact(done):
    cols, s, pad, label = 4, 2, 12, 30
    cw = FRAME_W * 2 * s + 8
    ch = FRAME_H * s + label
    rows = (len(done) + cols - 1) // cols
    sheet = Image.new('RGB', (pad + cols * (cw + pad), pad + rows * (ch + pad)), (20, 20, 18))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.load_default(size=20)
    except TypeError:
        font = ImageFont.load_default()
    for k, (ex_id, frames) in enumerate(done):
        x, y = pad + (k % cols) * (cw + pad), pad + (k // cols) * (ch + pad)
        d.text((x, y), ex_id, fill=(230, 226, 214), font=font)
        for i, f in enumerate(frames):
            sheet.paste(preview(f).resize((FRAME_W * s, FRAME_H * s), Image.NEAREST), (x + i * (FRAME_W * s + 8), y + label))
    sheet.save(SHEET)


if __name__ == '__main__':
    main()
