# Pipeline do avatar

`python tools/avatar/build.py all` gera tudo a partir de `assets_src/`:

| Passo | Entrada | Saída |
|---|---|---|
| `sprite` | `assets_src/avatar_base.png` | `assets_src/avatar_sprite.png` (grade de pixels em 4 tons) |
| `animate` | sprite + `assets_src/avatar_rig.json` | `assets_src/build/frames.json` |
| `items` | `assets_src/items/<slot>/<id>.png` | `assets_src/build/items.json` |
| `pack` | os dois acima | `app/src/main/assets/avatar/spritesheet.png` e `.json`, `core/.../avatar/AvatarArt.kt` e `build/previews/avatar_contact_sheet.png` |

- `rig.py`: as poses. Recorta do sprite a cabeça, os tênis, o logo da camiseta e o braço flexionado; desenha tronco, braços e pernas no mesmo estilo.
- A arte de `assets_src/avatar_base.png` é o desenho real (o menino de cabelo espetado), não um placeholder.
- Passo a passo para trocar a arte: [ARTE.md](ARTE.md).
- Precisa de Python 3 e Pillow (`pip install pillow`).
