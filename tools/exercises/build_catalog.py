"""Exercise catalog with muscles, from free-exercise-db (github.com/yuhonas/free-exercise-db, Unlicense).

    python tools/exercises/build_catalog.py

Downloads the database once into tools/exercises/.cache (shared with build_photos.py) and writes
app/src/main/assets/exercises_catalog.json:

    {"version": 1, "source": "...", "exercises": [
        {"id", "name_pt", "name_en", "aliases": [...], "primary": [...], "secondary": [...],
         "equipment", "kind": "força" | "tempo" | "cardio", "auto_translated": true|false}, ...]}

The app's own 61 exercises (app/src/main/assets/exercises.json) keep their id, Portuguese name and aliases and get
the muscles of their free-exercise-db match (tools/exercises/mapping.json). Every other name is translated here by
rules (movement + modifiers) and marked "auto_translated": true for review. Muscles and equipment use fixed
pt-BR labels (MUSCLES, EQUIPMENT below).
"""
import json
import os
import re
import unicodedata
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, '.cache')
DB_URL = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json'
APP_EXERCISES = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'exercises.json')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'exercises_catalog.json')

MUSCLES = {
    'abdominals': 'abdômen', 'abductors': 'abdutores', 'adductors': 'adutores', 'biceps': 'bíceps',
    'calves': 'panturrilha', 'chest': 'peitoral', 'forearms': 'antebraço', 'glutes': 'glúteos',
    'hamstrings': 'posterior de coxa', 'lats': 'dorsal', 'lower back': 'lombar', 'middle back': 'meio das costas',
    'neck': 'pescoço', 'quadriceps': 'quadríceps', 'shoulders': 'deltoides', 'traps': 'trapézio', 'triceps': 'tríceps',
}
EQUIPMENT = {
    'barbell': 'barra', 'dumbbell': 'halteres', 'other': 'outro', 'body only': 'peso do corpo', 'cable': 'cabo',
    None: 'nenhum', 'machine': 'máquina', 'kettlebells': 'kettlebell', 'bands': 'elástico', 'medicine ball': 'medicine ball',
    'exercise ball': 'bola suíça', 'foam roll': 'rolo', 'e-z curl bar': 'barra W',
}

# Movements, longest first: the head of the Portuguese name.
MOVES = [
    ('romanian deadlift', 'levantamento terra romeno'), ('stiff-legged deadlift', 'stiff'), ('stiff leg deadlift', 'stiff'),
    ('sumo deadlift', 'levantamento terra sumô'), ('deadlift', 'levantamento terra'),
    ('bench press', 'supino'), ('floor press', 'supino no chão'), ('leg press', 'leg press'),
    ('shoulder press', 'desenvolvimento'), ('military press', 'desenvolvimento militar'), ('overhead press', 'desenvolvimento'),
    ('push press', 'push press'), ('arnold press', 'desenvolvimento Arnold'), ('chest press', 'supino na máquina'),
    ('hammer curl', 'rosca martelo'), ('preacher curl', 'rosca Scott'), ('concentration curl', 'rosca concentrada'),
    ('spider curl', 'rosca spider'), ('drag curl', 'rosca drag'), ('wrist curl', 'rosca de punho'), ('leg curl', 'mesa flexora'),
    ('curl', 'rosca'),
    ('front squat', 'agachamento frontal'), ('hack squat', 'hack'), ('goblet squat', 'agachamento goblet'),
    ('split squat', 'agachamento búlgaro'), ('box squat', 'agachamento no banco'), ('squat', 'agachamento'),
    ('lunge', 'afundo'), ('step-up', 'subida no banco'), ('step up', 'subida no banco'),
    ('hip thrust', 'elevação pélvica'), ('glute bridge', 'ponte de glúteo'), ('bridge', 'ponte'),
    ('good morning', 'bom-dia'), ('hyperextension', 'hiperextensão'), ('back extension', 'hiperextensão'),
    ('bent over row', 'remada curvada'), ('upright row', 'remada alta'), ('seated row', 'remada sentada'),
    ('t-bar row', 'remada cavalinho'), ('row', 'remada'),
    ('lat pulldown', 'puxada frontal'), ('pulldown', 'puxada'), ('pullover', 'pullover'),
    ('chin-up', 'barra fixa supinada'), ('chinup', 'barra fixa supinada'), ('pull-up', 'barra fixa'), ('pullup', 'barra fixa'),
    ('push-up', 'flexão'), ('pushup', 'flexão'), ('dip', 'mergulho'),
    ('fly', 'crucifixo'), ('flye', 'crucifixo'), ('flyes', 'crucifixo'), ('crossover', 'crossover'),
    ('lateral raise', 'elevação lateral'), ('front raise', 'elevação frontal'), ('rear delt raise', 'crucifixo inverso'),
    ('calf raise', 'elevação de panturrilha'), ('calf press', 'panturrilha no leg press'), ('leg raise', 'elevação de pernas'),
    ('raise', 'elevação'),
    ('leg extension', 'cadeira extensora'), ('triceps extension', 'extensão de tríceps'), ('extension', 'extensão'),
    ('pushdown', 'tríceps na polia'), ('triceps kickback', 'tríceps coice'), ('glute kickback', 'coice de glúteo'), ('kickback', 'coice'), ('skullcrusher', 'tríceps testa'), ('skull crusher', 'tríceps testa'),
    ('shrug', 'encolhimento'), ('face pull', 'face pull'),
    ('sit-up', 'abdominal'), ('situp', 'abdominal'), ('crunch', 'abdominal crunch'), ('plank', 'prancha'),
    ('russian twist', 'rotação russa'), ('twist', 'rotação'), ('rollout', 'roda abdominal'), ('ab roller', 'roda abdominal'),
    ('mountain climber', 'escalador'), ('burpee', 'burpee'), ('jumping jack', 'polichinelo'), ('jump', 'salto'),
    ('stretch', 'alongamento'), ('smr', 'liberação com rolo'), ('clean and jerk', 'arremesso'), ('clean', 'clean'),
    ('snatch', 'arranco'), ('jerk', 'jerk'), ('swing', 'swing'), ('carry', 'caminhada do fazendeiro'), ("farmer's walk", 'caminhada do fazendeiro'),
    ('external rotation', 'rotação externa'), ('internal rotation', 'rotação interna'), ('rotation', 'rotação'),
    ('rack pull', 'levantamento terra parcial'), ('slam', 'arremesso de bola'), ('rope climb', 'subida na corda'),
    ('sprint', 'tiro'), ('sissy squat', 'agachamento sissy'), ('woodchop', 'lenhador'), ('hug', 'abraço'),
    ('bicycling', 'bicicleta'), ('rowing', 'remo'), ('running', 'corrida'), ('walking', 'caminhada'), ('treadmill', 'esteira'),
    ('elliptical', 'elíptico'), ('stairmaster', 'escada'), ('press', 'desenvolvimento'),
]
MODIFIERS = [
    ('smith machine', 'no Smith'), ('e-z bar', 'com barra W'), ('ez-bar', 'com barra W'), ('ez bar', 'com barra W'),
    ('barbell', 'com barra'), ('dumbbell', 'com halteres'), ('kettlebell', 'com kettlebell'), ('cable', 'no cabo'),
    ('machine', 'na máquina'), ('band', 'com elástico'), ('bands', 'com elástico'), ('medicine ball', 'com medicine ball'),
    ('exercise ball', 'na bola suíça'), ('stability ball', 'na bola suíça'), ('lever', 'na máquina'),
    ('incline', 'inclinado'), ('decline', 'declinado'), ('flat', 'reto'), ('seated', 'sentado'), ('standing', 'em pé'),
    ('lying', 'deitado'), ('kneeling', 'ajoelhado'), ('bent-over', 'curvado'), ('bent over', 'curvado'),
    ('one-arm', 'unilateral'), ('one arm', 'unilateral'), ('single-arm', 'unilateral'), ('single arm', 'unilateral'),
    ('one-leg', 'unilateral'), ('single-leg', 'unilateral'), ('single leg', 'unilateral'), ('alternate', 'alternado'),
    ('alternating', 'alternado'), ('reverse', 'invertido'), ('close-grip', 'pegada fechada'), ('close grip', 'pegada fechada'),
    ('wide-grip', 'pegada aberta'), ('wide grip', 'pegada aberta'), ('narrow', 'pegada fechada'), ('neutral grip', 'pegada neutra'),
    ('underhand', 'pegada supinada'), ('overhand', 'pegada pronada'), ('behind the neck', 'por trás'), ('rear', 'posterior'),
    ('front', 'frontal'), ('side', 'lateral'), ('lateral', 'lateral'), ('weighted', 'com peso'), ('assisted', 'assistido'),
    ('hanging', 'suspenso'), ('pause', 'com pausa'), ('box', 'no banco'), ('bench', 'no banco'), ('floor', 'no chão'),
    ('two-arm', 'com os dois braços'), ('two arm', 'com os dois braços'), ('one-legged', 'unilateral'),
    ('towel', 'com toalha'), ('chain', 'com correntes'), ('chains', 'com correntes'), ('full', 'completo'),
    ('hang', 'suspenso'), ('palms-down', 'palmas para baixo'), ('palms-up', 'palmas para cima'), ('rope', 'com corda'),
    ('isometric', 'isométrico'), ('static', 'estático'), ('double', 'duplo'), ('high', 'alto'), ('low', 'baixo'),
    ('wrist', 'punho'), ('hip', 'quadril'), ('hamstring', 'posterior de coxa'), ('quad', 'quadríceps'), ('calf', 'panturrilha'),
    ('chest', 'peitoral'), ('shoulder', 'ombro'), ('triceps', 'tríceps'), ('biceps', 'bíceps'), ('glute', 'glúteo'),
    ('lat', 'dorsal'), ('neck', 'pescoço'), ('ankle', 'tornozelo'), ('groin', 'virilha'), ('it band', 'trato iliotibial'),
    ('lower back', 'lombar'), ('upper back', 'parte alta das costas'), ('back', 'costas'), ('leg', 'perna'), ('arm', 'braço'),
]
STOP = {'the', 'with', 'on', 'to', 'a', 'of', 'and', 'in', 'from', 'for', 'at', 'grip', '-', 'medium', 'variation', 'version'}


def fetch(url, path):
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        req = urllib.request.Request(url, headers={'User-Agent': 'hybrid-coach-tools'})
        with urllib.request.urlopen(req, timeout=60) as r, open(path, 'wb') as f:
            f.write(r.read())
    return path


def slug(text):
    t = unicodedata.normalize('NFD', text.lower())
    t = ''.join(c for c in t if unicodedata.category(c) != 'Mn')
    return re.sub(r'[^a-z0-9]+', '-', t).strip('-')


def translate(name):
    """English name to a Portuguese one, by rules: the movement first, then its modifiers. Unknown words stay."""
    text = ' ' + re.sub(r'\s+', ' ', name.lower().replace('–', '-').replace('/', ' ').replace(',', ' ').replace('(', ' ').replace(')', ' ')) + ' '
    head = None
    for en, pt in MOVES:
        m = re.search(r'(?<![a-z])' + re.escape(en) + r's?(?![a-z])', text)
        if m:
            head = pt
            text = text[:m.start()] + ' ' + text[m.end():]
            break
    mods = []
    for en, pt in MODIFIERS:
        pattern = r'(?<![a-z])' + re.escape(en) + r's?(?![a-z])'
        if re.search(pattern, text):
            # No repeats, also of a word already in the movement ("elevação lateral" + "side").
            if pt not in mods and not (head and pt in head):
                mods.append(pt)
            text = re.sub(pattern, ' ', text)
    rest = [w.strip('-') for w in text.split() if w not in STOP and not w.isdigit()]
    rest = [w for w in rest if w]
    # Without a known movement, the untranslated words are the name: they go first.
    parts = ([head] + mods + rest) if head else (rest + mods)
    out = ' '.join(parts).strip() or name
    return out[0].upper() + out[1:]


def kind(e):
    if e.get('category') == 'cardio':
        return 'cardio'
    if e.get('category') == 'stretching' or e.get('force') == 'static':
        return 'tempo'
    return 'força'


def main():
    db = json.load(open(fetch(DB_URL, os.path.join(CACHE, 'exercises.json')), encoding='utf-8'))
    app = json.load(open(APP_EXERCISES, encoding='utf-8'))['exercises']
    mapping = {k: v for k, v in json.load(open(os.path.join(HERE, 'mapping.json'), encoding='utf-8')).items() if not k.startswith('_')}
    by_en = {e['name']: e for e in db}
    used_en = set()
    out = []
    # The app's own exercises first: their ids and names stay, the muscles come from their match.
    for a in app:
        en = by_en.get(mapping.get(a['id'], ''))
        entry = {
            'id': a['id'], 'name_pt': a['canonical_name'], 'name_en': en['name'] if en else None,
            'aliases': a['aliases'] + ([en['name']] if en and en['name'] not in a['aliases'] else []),
            'primary': [MUSCLES[m] for m in en['primaryMuscles']] if en else [],
            'secondary': [MUSCLES[m] for m in en['secondaryMuscles']] if en else [],
            'equipment': EQUIPMENT.get(en.get('equipment')) if en else None,
            'kind': kind(en) if en else 'força', 'auto_translated': False,
        }
        if en:
            used_en.add(en['name'])
        out.append(entry)
    missing = [a['id'] for a in app if a['id'] not in mapping or mapping[a['id']] not in by_en]
    ids = {e['id'] for e in out}
    for e in sorted(db, key=lambda x: x['name']):
        if e['name'] in used_en:
            continue
        i = 'fedb-' + slug(e['name'])
        if i in ids:
            continue
        ids.add(i)
        out.append({
            'id': i, 'name_pt': translate(e['name']), 'name_en': e['name'], 'aliases': [e['name']],
            'primary': [MUSCLES[m] for m in e['primaryMuscles']], 'secondary': [MUSCLES[m] for m in e['secondaryMuscles']],
            'equipment': EQUIPMENT.get(e.get('equipment')), 'kind': kind(e), 'auto_translated': True,
        })
    doc = {
        'version': 1,
        'source': 'free-exercise-db (github.com/yuhonas/free-exercise-db), Unlicense; nomes em português traduzidos por regras onde auto_translated é true',
        'exercises': out,
    }
    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(doc, f, ensure_ascii=False, separators=(',', ':'))
    print('%d exercícios (%d do app, %d traduzidos automaticamente); sem músculos: %s' % (
        len(out), len(app), sum(1 for e in out if e['auto_translated']), ', '.join(missing) or 'nenhum'))


if __name__ == '__main__':
    main()
