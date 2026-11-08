# Como trocar a arte do avatar

O bichinho sai de um desenho só, `assets_src/avatar_base.png`. Um script monta todas as poses a partir dele.

## Trocar o desenho
1. Salve o desenho novo em `assets_src/avatar_base.png`, substituindo o atual.
   - Melhor se for **pixel art de verdade**: grade regular e sem degradê, em pé, de frente, com as mãos na cintura e um braço flexionado, como o atual.
   - Pode ter qualquer tamanho e fundo transparente ou de uma cor só.
   - Use preto e branco ou até 4 tons. As cores viram os 4 tons do tema do app.
2. Rode no terminal, na pasta do projeto:
   ```
   python tools/avatar/build.py sprite
   ```
   Abra `assets_src/avatar_sprite.png` para ver a grade que saiu. Ela deve ficar pequena (o atual tem 42x66) e com cara de pixel art.
3. Ajuste `assets_src/avatar_rig.json` se o desenho mudou de forma. Todos os números são células da grade (`[x0, y0, x1, y1]`):

   | Campo | O que é |
   |---|---|
   | `head` | cabeça com cabelo |
   | `face` | rosto (não recebe sombra automática) |
   | `eyes.left` / `eyes.right` | caixa de cada olho (piscar, dormir, cansado, esforço) |
   | `mouth` | boca (sorriso, bocejo, dentes cerrados, língua) |
   | `neck` / `logo` | pescoço e desenho da camiseta |
   | `shoes.left` / `shoes.right` | tênis |
   | `flex_arm` | braço flexionado: um ponto do punho (`seed`) e a caixa |
   | `sparks` | traços de "força" perto do punho |

   Para achar as coordenadas, abra o `avatar_sprite.png` num editor que mostre a posição do pixel. O Paint mostra no rodapé.
4. Gere tudo:
   ```
   python tools/avatar/build.py all
   ```
5. Abra `build/previews/avatar_contact_sheet.png` e confira cada animação: grande e no tamanho real.
6. Recompile o app (`gradlew assembleDebug`) ou peça o APK.

## Itens (adesivos)
- Um PNG por item em `assets_src/items/<slot>/<id>.png`, de até 32x32 e com fundo transparente. Hoje só existe o slot `sticker`, que fica no chão ao lado do bichinho.
- O `<id>` é o do catálogo (ex.: `sticker_heart`). O `build.py` avisa quais itens do catálogo estão sem arte.
- Para um item que ande junto com o corpo, crie um slot em `avatar_rig.json` com `"anchor": "body"` e a posição `x`, `y` no quadro.

## O que o `build.py all` gera
- `app/src/main/assets/avatar/spritesheet.png` e `spritesheet.json`: o que o app carrega.
- `core/src/main/kotlin/.../avatar/AvatarArt.kt`: a mesma arte embutida, usada se o asset faltar e pelos testes.
- `build/previews/avatar_contact_sheet.png`: a folha para revisar.

## Limites
- As poses (correr, levantar, pular…) são desenhadas por código em `tools/avatar/rig.py`, nas proporções deste menino. Um desenho muito diferente (outro tamanho de corpo, de lado, um animal) pede ajustar o rig, não só o JSON.
- Precisa de Python 3 e Pillow (`pip install pillow`).
