# TrainBit.exe

> Nome interno do código: Hybrid Coach. Nome em teste local: Treino_debug.exe

App Android de treino híbrido (musculação + corrida) com cara de jogo retrô. Especificação completa em [SPEC.md](SPEC.md).

## Como funciona
1. **Questionário**: o atleta responde sobre o momento atual, disponibilidade, equipamentos e limitações.
2. **Prompt**: o app monta um prompt com essas respostas, o histórico e o contrato do plano, para colar em qualquer IA.
3. **JSON**: a IA devolve o plano do bloco em JSON ([schema/schema_v1.md](schema/schema_v1.md)).
4. **Validação e importação**: o app valida o JSON (erros apontam o campo), normaliza os exercícios, mostra a prévia e importa para o calendário.
5. **Treino guiado**: player de musculação (séries, cargas, descanso com voz) e de corrida (GPS, blocos, avisos por voz), com XP, troféus e o bichinho.
6. **Resumo do bloco**: no fim, o app exporta um resumo em texto para a IA planejar o próximo bloco.

## Builds
| Build | Nome exibido | applicationId |
|---|---|---|
| debug (teste local) | Treino_debug.exe | `com.ithalo.hybridcoach` |
| release | TrainBit.exe | `com.ithalo.trainbit` |

Os dois podem ficar instalados juntos. O debug mantém o id original de propósito: é a instalação de teste, com o progresso salvo. O namespace e os pacotes do código continuam `com.ithalo.hybridcoach`.

## Documentos
- [SPEC.md](SPEC.md): especificação por fases.
- [schema/schema_v1.md](schema/schema_v1.md): formato do JSON do plano (contrato entre a IA e o app).
- [tools/avatar/](tools/avatar/README.md): pipeline do sprite do bichinho e [como trocar a arte](tools/avatar/ARTE.md).
- `tools/exercises/`: script que gera as fotos dos exercícios em pixel (free-exercise-db, domínio público).

A pasta `docs/` guarda só anotações de trabalho (decisões, progresso, backlog, roadmap, prompts) e fica fora do repositório (ver `.gitignore`).

## Status
- Fase 0 (contrato): concluída.
- Fase 1 (plano e calendário): concluída. Questionário, prompt, importação com validação e prévia, normalização de exercícios, calendário semana/mês, remarcar/pular e exportação do resumo.
- Fase 2 (player de musculação): concluída. Player com timer de descanso e voz pt-BR, substituir/pular/série extra, histórico por exercício com gráfico e resumo de musculação no export.
- Ajustes pós-teste (0.2.0): questionário reorganizado, versão em casa dos treinos e correção do crash ao gerar o prompt.
- 0.5.0 (visual retrô e gamificação): todas as telas no estilo programa/jogo do Windows 3.1 com 3 paletas claro/escuro e muitas animações; aba Jogador com nível, XP, estatísticas, recordes, troféus estilo PlayStation e itens cosméticos (títulos e molduras), avisos de novidades. Não testado em aparelho ainda.
- Fase 3 (player de corrida, 0.3.0; 0.4.0 com modo esteira, pace robusto a ruído de GPS, histórico por sessão e descanso que sobrevive ao app fechar): implementada; falta o aceite em campo (corrida de 30 min no Poco X4 Pro com tela bloqueada e música). GPS em serviço em primeiro plano, avisos de pace por voz com a música abaixada, marcos (blocos, km, metade, contagem para o intervalo, resumo final), pausar/pular/encerrar, retomada após o app ser fechado, RunLog com parciais por km e bateria, resumo de corridas no export e tela de Configurações.

- 0.9.0 (bichinho-avatar): o boneco da aba Troféus virou um bichinho vivo em pixel art 32x32 (estilo Tamagotchi, sem culpa): reage ao progresso (energizado, normal, cansado, parado, dormindo), anima em 5 fps, reage ao toque e a treino concluído/nível/troféu, e veste o adesivo equipado. Arte e animações em `core/.../avatar`; contato visual em `build/previews/avatar_contact_sheet.png`. Não testado em aparelho ainda.
- 0.10.0 (avatar com a arte de referência): o bichinho agora usa o menino da imagem de referência, convertido para 64x64 em 4 tons (seis poses: parado, músculos, correndo, levantando, comemorando, cansado). A animação é o balanço entre dois quadros de cada pose; o adesivo vai na bandana. Sem piscar nem troca de boca (o rosto vem desenhado junto com o corpo). Não testado em aparelho ainda.
- 0.11.0: aba Jogador com sub-menu e tabela de pontos, XP reequilibrado, player com o descanso no lugar da barra e botão de registrar fixo, notificações de descanso e de treino em andamento, resumo do treino, janela de detalhes dos itens, esforço no lugar de RPE e voz que abaixa a música. Não testado em aparelho ainda.
- 0.12.0: bichinho novo animado por código a partir do desenho dele (parado, andando pela tela, flexionando, correndo, levantando, comemorando, cansado, dormindo; 36 quadros a 10 fps), adesivo no chão, botão "Ver execução no YouTube" no player, volume do resumo comparado com a última vez do mesmo treino e recordes do resumo lidos do banco. Não testado em aparelho ainda.
- 0.12.1 e 0.12.2: corrida/caminhada (`steady` + `recovery` com o mesmo `repeat`) agora intercaladas, como o prompt pede; aviso de voz 10 s antes do fim de todo bloco com 25 s ou mais ("Faltam 10 segundos. Depois, recuperação."), no lugar do aviso que só existia antes de intervalos.
- 0.12.3: a corrida não termina sozinha quando acabam os blocos (volta livre, conta até finalizar à mão ou ficar 1 min parado); parado 45 s no meio do treino pausa sozinho, pergunta se está tudo bem (continuar ou "preciso de uma pausa") e retoma ao voltar a correr (só GPS, não na esteira).
- 0.12.4: recordes de corrida na aba Recordes (maior distância, maior tempo, melhor ritmo médio em corridas de 1 km ou mais, quilômetro mais rápido), com a data.
- 0.12.5: bater um recorde de corrida dá XP de recorde (conta para os troféus de recorde) e aparece como "NOVO RECORDE!" só no resumo, depois de finalizar; a tela Hoje volta a ter ilustração no lugar do bichinho, agora animada por situação (halter levantando, tênis pulando com poeira, troféu e medalha brilhando quando feito, ampulheta virando no descanso). O bichinho fica na aba Jogador.
- 0.13.0: lembretes de treino (Ajustes > Lembretes: horário da academia e da corrida, aviso na hora ou 15/30/60 min antes, alarme exato quando o Android deixa, volta depois de reiniciar); pipeline do avatar em tools/avatar (build.py gera o spritesheet que o app carrega, com a arte embutida como reserva), bichinho piscando e bocejando, relógio a 5 fps.
- 0.14.0: controle de música no player e na corrida; próximo exercício no fim do player; treino complementar (`"optional": true`, cartão "Extra", banco v5); fotos dos exercícios em pixel animadas (free-exercise-db, 60 exercícios); pontuação do Perfil numa janela ("Ver pontuação").
- 0.14.1: tira o leitor de notificações (nome da música), que fazia o Play Protect bloquear a instalação; a barra de música fica com tocar/pausar, próxima e "música tocando".

## Como compilar e testar
Requer JDK 17+ (testado com 21) e o Android SDK (plataforma 35). Crie `local.properties` com `sdk.dir=C:/caminho/do/Android/Sdk` (o arquivo não é versionado).

```
./gradlew :core:test              # testes unitários do núcleo (validador, calendário, prompt, resumo, musculação, corrida, jogo, avatar)
./gradlew :app:testDebugUnitTest  # testes do app (fixtures do contrato, migrações)
./gradlew :app:lintDebug
./gradlew :app:assembleDebug      # Treino_debug.exe em app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # TrainBit.exe (sem assinatura) em app/build/outputs/apk/release/
```

## Estrutura
- `core/` — Kotlin puro, sem Android, totalmente testado: plano e validador (`plan`), exercícios (`exercises`), calendário (`schedule`), questionário (`questionnaire`), prompt (`prompt`), resumo (`summary`), player de musculação (`strength`: timer de descanso, progresso, resumo), player de corrida (`run`: blocos, filtro de GPS, média móvel do pace, regras de aviso e o motor `RunEngine`), gamificação (`game`: XP, níveis, troféus, itens), bichinho-avatar (`avatar`: arte em strings, estado `computeAvatarState`, animador por tempo).
- `app/` — Android (Compose + Room): `data/` (entidades, DAO, repositório, configurações em DataStore), `run/` (serviço de corrida, voz com ducking), `ui/` (telas e navegação; `ui/theme` e `ui/retro` são o design system retrô, `ui/game` o perfil, troféus, itens e o overlay de novidades, `ui/avatar` o desenho e o relógio do bichinho).
- `app/src/test/resources/` — fixtures do contrato; `app/src/main/assets/` — `exercises.json`, `prompt_template.md`, `example_plan.json` (cópia de `valid_full.json`, conferida por teste).
- `schema/` — formato do JSON do plano. `tools/` — scripts que geram a arte do bichinho e as fotos dos exercícios; `assets_src/` — fontes dessa arte e o ícone da loja (`assets_src/store/`). As prévias geradas vão para `build/previews/`.

## Fluxo
Bloco → Questionário → Prompt (copiar/compartilhar) → colar o JSON da IA em Importar → Calendário/Hoje → Exportar resumo → próximo bloco.
