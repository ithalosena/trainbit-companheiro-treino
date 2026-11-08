# Suposições e decisões

## Fase 0
- **Módulo `core`** (Kotlin/JVM puro, sem Android) contém o validador, para rodar testes sem SDK. O módulo `app` (Android) entra na Fase 1; as fixtures já ficam em `app/src/test/resources/` e os assets em `app/src/main/assets/`, e `core` os usa como recursos de teste.
- **`assets/`** do SPEC = `app/src/main/assets/` (`exercises.json`, `prompt_template.md`).
- **Campos obrigatórios** (SPEC não listou): `plan.{name,start_date,duration_weeks}`, `weeks[].{weeks,sessions}`, `session.{day_of_week,type,title}` + `exercises`/`blocks`, `exercise.{name,sets,reps,rest_seconds}`, `block.kind` + um entre duração/distância. O resto tem padrão (ver `schema/schema_v1.md`).
- **`repeat` em corrida** repete o bloco isolado; o intervalado usa o mesmo `repeat` no `interval` e no `recovery` seguinte (as fixtures seguem isso).
- **Ordem de semanas**: grupos podem aparecer em qualquer ordem em `weeks[]`.
- **Template do prompt**: marcadores `{{CHAVE}}` e seções condicionais `{{#CHAVE}}...{{/CHAVE}}` (omitidas quando vazias); o gerador (Fase 1) substitui `{{EXAMPLE_JSON}}` pelo conteúdo de `valid_full.json`.
- **Dependências**: `kotlinx-serialization-json` (JSON, exigido no SPEC) e `kotlin-test` (testes). Kotlin 2.0.21, Gradle 8.9 (wrapper). Sem toolchain fixa; compila com o JDK 21 local.
- **Normalização de nomes**: implementada em `ExerciseCatalog` na Fase 1 (ver abaixo); `ExercisesAssetTest` a usa para garantir que nenhum apelido aponta para dois exercícios.

## Fase 1
- **Versões** (as já presentes no cache do Gradle da máquina): AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.26, Room 2.6.1, Compose BOM 2024.10.00, Navigation 2.8.3, compileSdk/targetSdk 35. Dependências do app: Compose + Material 3, Activity/Lifecycle/Navigation Compose, Room (+ktx), core-ktx, kotlinx-serialization (via `core`). Nenhuma outra.
- **Sem DataStore** nesta fase: não há configurações ainda (voz, pace e trilha GPS entram nas Fases 2 e 3). As respostas do questionário ficam em Room (`QuestionnaireEntity`, JSON).
- **Room v1** só tem as tabelas da Fase 1 (plano, sessões, exercícios planejados, blocos de corrida, exercícios/apelidos, questionário, resumo exportado). As tabelas de log (`SetLog`, `RunLog`, ...) entram com migração nas fases seguintes.
- **Datas**: guardadas como texto ISO. Se `start_date` não for segunda-feira, a semana 1 é a semana do calendário que contém a data (âncora = segunda-feira anterior); sessões dessa semana antes da data de início continuam no calendário.
- **Importar**: a importação nova arquiva o plano ativo (`status = archived`); o histórico não é apagado. Exercício não reconhecido e sem decisão do usuário é criado como novo (`custom-<nome>`), sempre com o apelido gravado; mapear para um existente também grava o apelido.
- **Normalização de nomes** trata pontuação como espaço (`Step-up` = `step up`), além de acento, caixa e espaços.
- **Questionário**: sessões acima dos dias disponíveis é aviso (não bloqueia, pois até 2 sessões por dia são aceitas); data inválida, nenhum dia ou duração fora de 4–12 bloqueiam. A data de início sugerida é a próxima segunda-feira; uma data salva e já passada é descartada.
- **Resumo exportável**: nesta fase gera o esqueleto (aderência e motivos de pulo; `strength`/`runs` vazios). Fica salvo; o prompt seguinte usa o mais recente se o usuário mantiver "incluir resumo" (padrão).
- **Marcar como concluída** manualmente na sessão (não está no SPEC) para a aderência do resumo funcionar antes dos players. O botão Iniciar fica desabilitado até as Fases 2 e 3.
- **Ícone** do app: provisório (ícone padrão do sistema).
- **Não verificado em aparelho**: o emulador não sobe nesta máquina (sem aceleração de virtualização). Compila, passa no lint e a lógica de `core` tem testes, mas as telas e o Room não foram executados; testar no Poco X4 Pro.

## Fase 2
- **Room v2** com migração `MIGRATION_1_2` (sem `fallbackToDestructiveMigration`). O schema passou a ser exportado em `app/schemas/` (versionar junto do código). A migração foi conferida com um script SQLite: o banco migrado fica idêntico ao criado do zero (colunas, chaves estrangeiras e índices). Não foi executada no Android.
- **Estado do treino** vem do banco: a posição (exercício e série) é calculada pelas séries já registradas, então sobrevive a rotação, a fechar o app e a processo morto. O timer de descanso e os campos digitados **não** sobrevivem a processo morto (só a rotação de tela); ao reabrir, o app volta na próxima série sem descanso.
- **Posição automática**: primeiro exercício com séries pendentes; os botões ‹ › permitem pular para outro exercício pendente. Exercício concluído volta a ficar pendente com "Série extra".
- **Pré-preenchimento**: reps = limite superior do alvo ("6-8" → 8; "AMRAP"/"30s" ficam vazios); carga = última série do exercício na sessão, senão a carga planejada, senão a última carga registrada em qualquer treino. Com `load_kg` nulo a série só pode ser registrada depois de informar a carga, e a última carga aparece como sugestão. Após substituir um exercício, a carga planejada deixa de valer.
- **Substituir**: o log guarda o exercício realizado (o substituto), então o histórico segue o exercício real; o resumo mantém o planejado original. Pular e substituir (com motivo) aparecem em `user_notes` do resumo, junto do RPE geral e da nota de cada sessão.
- **Voz**: `TextToSpeech` do sistema em pt-BR, sem foco de áudio (o ducking da música é da Fase 3). Sem voz pt-BR instalada, o app avisa e fica mudo. O liga/desliga da voz vale só para o treino aberto (ainda não há tela de Configurações).
- **Descanso**: relógio monotônico; −15/+15 s e pular; avisos em 3, 2, 1 e "Descanso terminado". Depois da última série do treino não há descanso.
- **Resumo de musculação**: melhor série = maior carga (desempate por reps) entre as séries sem falha; `load_trend_kg` = maior carga de cada sessão. Se o JSON passar de 6.000 caracteres, encurta as tendências de carga (6, 4, 2, 1 pontos) e só depois descarta exercícios/observações. Diferença do SPEC: não agrega por semana.
- **Histórico**: lista de exercícios com séries, gráfico de maior carga por sessão e as séries de cada dia; o histórico por sessão é o detalhe da sessão. Acessível pela aba Bloco.
- **Fora do escopo desta fase**: tela de Configurações (voz/volume), foco de áudio.

## Ajustes do teste da Fase 2 (0.2.0)
- Feedback do teste no aparelho em `docs/FEEDBACK_FASE2.md`.
- Questionário reorganizado: os dias são escolhidos por modalidade (corrida/musculação por dia, os dois no mesmo dia permitido); o número de sessões por semana deriva dos dias. Respostas salvas da 0.1.0 com os campos antigos (`availableDays`, `location`, `runSurface`, `runSessionsPerWeek`...) são ignoradas e voltam aos padrões novos.
- "Já corre?" substitui o critério só por km: quem não marca "Já corro" é tratado como iniciante (caminhada + corrida). Longão só é perguntado para quem já corre.
- Versão em casa: campo opcional `home_exercises` em sessões de musculação (compatível com o schema v1; planos antigos continuam válidos). Banco v3: coluna `variant` ("gym"/"home") em sessão e exercício planejado, com migração 2→3 conferida contra o schema exportado. A troca de versão fica travada depois da primeira série registrada para não misturar registros.
- Bike ergométrica não virou tipo de sessão: entra como exercício (`Bike ergométrica`, reps em tempo) nas sessões de musculação. Exercícios novos do catálogo passam a ser adicionados a bancos já existentes na abertura do app.
- Crash do "Gerar prompt": regex com `}` sem escape funciona na JVM e quebra no Android (ICU). Os testes do `core` rodam na JVM, então esse tipo de diferença só aparece no aparelho; há um teste que impede chave solta no padrão.

## Fase 3 — Player de corrida (0.3.0)
- **Dependências novas:** `play-services-location` (o SPEC pede `FusedLocationProvider`, que só existe no Google Play Services) e `datastore-preferences` (o SPEC pede DataStore para as configurações).
- **Lógica pura no `core/run`**, testada sem aparelho: `RunSteps` (expande `repeat`; `interval` seguido de `recovery` com o mesmo `repeat` é intercalado), `LocationFilter` (descarta precisão pior que 20 m e saltos acima de 12 m/s), `PaceSmoother` (média móvel por tempo ativo; sem pace com menos de metade da janela ou abaixo de 20 min/km, tratado como parado), `PaceCoach` (regras de aviso do SPEC) e `RunEngine` (distância, blocos, km, marcos, pausa, estado serializável).
- **Avisos de pace:** só depois de uma janela de suavização desde o início do bloco (a média ainda reflete o bloco anterior). "Acelere"/"Reduza" após 10 s contínuos fora da faixa + tolerância; no mínimo 30 s entre avisos; "No ritmo" só depois de um aviso, no máximo uma vez por minuto. Bloco sem faixa: sem avisos. Paces falados como "5 e 30" (min e seg por km).
- **Marcos:** início e fim de cada bloco, km (tempo do km e pace médio geral), metade do bloco só em blocos de 2 min ou 1 km para cima (em tiros curtos seria ruído), "Intervalo em N segundos" ~10 s antes de um bloco de intervalo (em bloco por distância, estimado pelo pace), resumo no fim.
- **Tempo:** relógio de parede, descontando pausas. Se o app for morto, o tempo continua contando ao retomar (o corredor seguiu correndo); a distância do período sem app não é medida. Na retomada o primeiro ponto de GPS só define a posição.
- **Serviço:** `RunService` em primeiro plano (tipo localização), notificação com pausar/retomar, WakeLock parcial (limite de 4 h), estado salvo no banco a cada 5 s e em cada pausa/pulo. `START_STICKY` tenta retomar sozinho; sem a permissão "o tempo todo" o Android pode recusar o reinício em segundo plano, e então a tela Hoje oferece "Abrir corrida" → Continuar/Descartar.
- **Permissões em sequência** na tela da corrida, cada uma com explicação: localização precisa (obrigatória), notificações, "o tempo todo" (recomendada, pode pular) e otimização de bateria (abre a lista do sistema; dica específica para Poco/Xiaomi). Não usamos `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` direto.
- **Áudio:** `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` com `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`; o foco é liberado quando a fila de fala esvazia. Volume da voz em Configurações.
- **Banco v4:** `RunLogEntity` (com `stepsJson` dos blocos executados e bateria no início/fim), `RunSplitEntity`, `RunProgressEntity` (corrida em andamento) e `RunTrackPointEntity` (trilha, só com a opção ligada). Migração 3→4 conferida contra o schema exportado.
- **Resumo:** tipo "interval" se o plano tem intervalos, senão "steady"; pace planejado e real referem-se aos blocos desse tipo (com fallback para a corrida toda). Se o JSON passar de 6.000 caracteres, as corridas são agregadas por semana.
- **Configurações:** voz liga/desliga e volume, tolerância de pace, suavização (10/20/30 s), intervalo mínimo entre avisos, guardar trilha e apagar dados. Unidades fixas (kg, km, min/km); a voz da musculação continua com o botão próprio do player.
- **Fora desta versão:** modo esteira (o `RunEngine` recebe distância só por `onFix`; o ponto de extensão é alimentar distância manual), escolha de saída de áudio (usa a saída atual do sistema, o fone Bluetooth quando conectado).
- **Não verificado:** nada da Fase 3 rodou em aparelho daqui; o aceite (30 min com tela bloqueada e música) depende do teste no Poco X4 Pro.

## 0.4.0 — Fechamento sem interação do usuário
- **GPS ruidoso (achado por simulação):** com erro de ±3 m por leitura, o pace por pontas da janela oscilava ±13% e gerava avisos falsos. Agora a distância só é somada quando o deslocamento desde o último ponto contado passa de 8 m (`LocationFilter.MIN_STEP_M`), e o pace é a inclinação por mínimos quadrados da distância no tempo dentro da janela. Teste `RunSimulationTest`: 37 min de intervalado com ruído, leituras fracas e saltos; sem avisos no aquecimento, alertas só no tiro lento, ≥30 s entre alertas, distância dentro de -3%/+5%.
- **Modo esteira:** escolhido na tela antes de começar; a distância vem da velocidade informada no app (passos de 0,5 km/h), e a voz diz a faixa de velocidade de cada bloco e pede para aumentar/diminuir a velocidade. Continua exigindo a permissão de localização porque o serviço em primeiro plano é do tipo localização.
- **Descanso da musculação após o app fechar:** o timer é retomado a partir da hora da última série registrada (ajustes de ±15 s se perdem).
- **Histórico por sessão** (SPEC §8): lista de sessões concluídas, com acesso ao detalhe (séries, corrida, RPE).
- **Serviço de corrida:** a frase final não é mais cortada ao encerrar; toque duplo em "Começar" não inicia duas vezes; frases pedidas antes da voz ficar pronta entram numa fila.
- Ícone e visual ficam para a conversa sobre aparência.

## 0.5.0 — Visual retrô e gamificação
- **Visual:** todas as telas usam o design system retrô (`ui/theme`, `ui/retro`): fundo pontilhado, barra de título estilo programa (`HOJE.EXE`, `CALENDÁRIO.EXE`...), janelas com zoom de abertura, botões chanfrados, chips, caixas de mensagem/erro, barras de progresso em blocos. Material só sobra no seletor de data e no slider de volume (tingidos pelo `colorScheme` mapeado). Nenhuma cor fixa nas telas: só `Retro.colors`.
- **Aparência:** paleta (Vintage/Brisa/Pôster) e modo (Sistema/Claro/Escuro) em DataStore (`palette`, `dark_mode`), aplicados na hora. Ícones da barra de status seguem a barra de título; os da barra de navegação seguem o fundo. A tela "Vitrine do design" (Configurações) abre o showcase do design system.
- **Fontes:** as TTF embutidas (Silkscreen, VT323, Press Start 2P) têm todos os acentos do português (conferido nos `cmap` com fontTools). Faltam só `−` (U+2212) e `✓`, que deixaram de ser usados (viraram `-` e ícone pixel). Nenhuma fonte nova.
- **Navegação:** barra inferior retrô com 4 abas (Hoje, Calendário, Bloco, Jogador). Transições curtas com fade + deslize em 6 passos "duros". Calendário semana/mês e as etapas do questionário trocam por dissolução em dither.
- **Gamificação:** o estado do jogo é recalculado pelo `GameEngine` (core) a partir do Room inteiro (todos os planos, inclusive arquivados) sempre que uma tabela relevante muda (observador do `InvalidationTracker`, debounce de 250 ms, cálculo fora da main thread). Datas inválidas no banco são ignoradas.
- **"Visto":** DataStore guarda nível, troféus, itens e XP já mostrados, mais título e moldura equipados. Na primeira abertura da 0.5.0 tudo que já estava liberado é marcado como visto e aparece só um aviso "Progresso importado: Nível N, X troféus". Depois, cada novidade aparece uma vez (LEVEL UP em tela cheia, banner de troféu, banner de item, "+N XP" flutuante) e só é gravada como vista depois de exibida. Dentro dos players o LEVEL UP espera a saída da tela (banners continuam). "Apagar todos os dados" zera também o "visto" (e o equipado).
- **XP no fim do treino/corrida:** mostra a estimativa pelas regras base (sessão + séries, ou sessão + 1 XP a cada 100 m). Recordes, sequências e troféus somam depois e aparecem pelo "+N XP" global.
- **Molduras:** 7 estilos (clássica, pontilhada, listrada, em chamas, prateada, dourada, platina) desenhados com as cores da paleta; a escolhida muda a janela JOGADOR.EXE.
- **Teclado:** as telas passaram a reservar espaço para o teclado (insets de IME), já que o edge-to-edge ignora `adjustResize`.
- **Não verificado em aparelho:** nada da 0.5.0 rodou num aparelho daqui (só compilação, lint e testes do core). Conferir no Poco X4 Pro: legibilidade das fontes pixel, contraste nas 6 combinações de paleta/modo, desempenho das animações em listas longas (prompt e resumo grandes), o fluxo de novidades na primeira abertura, e que os players (descanso com voz, corrida com GPS/esteira) continuam iguais.

## 0.9.0 — Bichinho-avatar (Tamagotchi sem culpa)
- **Sem git:** o pedido falava em commits pequenos por parte; a regra do usuário é não rodar comandos git, então o trabalho foi dividido em partes pequenas e verificadas por build/testes (parser e arte, estado, animador, itens, integração).
- **Itens existentes e slots (Etapa 0):** títulos (9, só texto, na faixa do perfil), molduras (7, estilo da janela do perfil) e adesivos (8). Só os adesivos não tinham onde aparecer (equipar não fazia nada). Decisão simples: **o adesivo equipado passa a ser vestido no peito do bichinho** (um slot, `GameSeen.equippedSticker`, equipar/remover na tela Itens). Título e moldura continuam como estão, sem camada no personagem (o pedido diz que eles ficam como estão). Os 8 adesivos têm arte 7x6 que segue o corpo; um item sem arte só registra aviso no log e não desenha nada.
- **Paleta:** 4 papéis mapeados para tokens do tema: 1 contorno = `ink`, 2 sombra = `spriteMid`, 3 base = `spriteLight`, 4 brilho = `spriteHighlight`; transparente = nada. Nenhuma cor fixa no sprite. Nas paletas escuras a rampa inverte (contorno claro), como os outros sprites.
- **Arte e camadas:** corpo (18 poses 32x32, cada uma com âncoras de cabeça e peito), olhos (7), bocas (6), adesivo e efeitos (suor, zZ, brilhos, poeira, linhas de velocidade) como camadas esparsas na mesma grade 32x32. Os rostos são montados por cima do corpo e seguem o balanço da cabeça. Gerada por script (fora do repositório) e gravada como strings em `AvatarArt.kt`; o parser (`AvatarSprites.parse`) valida tamanho e caracteres e tem teste.
- **Relógio:** 5 fps (200 ms por quadro), pausa fora da tela (`repeatOnLifecycle(STARTED)`), o quadro é lido na fase de desenho (redesenha sem recompor). Piscar a cada 3 a 5 s, olhar para o lado, e a cada 9 a 15 s um comportamento (alongar ou coçar a cabeça; bocejo quando cansado/parado). O sorteio é um hash do tempo, então é repetível e testável.
- **Animações desligadas no sistema** (`ANIMATOR_DURATION_SCALE = 0`): pose estática do estado; eventos e toque são ignorados.
- **Estado (`computeAvatarState`):** constantes em `AvatarTuning`. Vitalidade = 30 + 35 x aderência dos últimos 7 dias (feitas/planejadas; sem plano conta 0,5) + 4 por semana de sequência (máx. 24) + bônus por treino recente (11 se ontem ou hoje, 5 até 3 dias) - 6 por dia parado além de 4 (máx. 50). Cansaço = 14 x (RPE médio dos 3 últimos treinos - 5) (máx. 56) + 12 por treino extra nos últimos 3 dias (máx. 30) + 7 por dia seguido de treino além do primeiro (máx. 14) - 20 por dia de descanso (máx. 60). Prioridade: dormindo (23h às 6h ou cansaço >= 90) > cansado (>= 60) > parado (5 dias sem treinar) > energizado (vitalidade >= 70) > normal. Quem nunca treinou fica neutro, sem punição. O horário de dormir é fixo (23h às 6h), sem tela de ajuste.
- **Recalcular:** ao abrir a tela e a cada mudança no histórico (o estado do jogo já é reavaliado quando o banco muda, inclusive ao concluir uma sessão); a hora é reavaliada a cada hora com o app aberto (verificação por minuto). Se o app ficar aberto passando da meia-noite, o "hoje" do histórico só atualiza na próxima mudança.
- **Transição entre estados:** 3 quadros (piscada, olhos fechados, brilho) antes de assumir o novo estado.
- **Eventos:** `TRAIN_STRENGTH`/`TRAIN_RUN` disparam ao concluir uma sessão (feedback salvo), `CELEBRATE` ao fechar o aviso de nível ou troféu, `TAP` ao tocar. Um evento espera até 20 s e só toca uma vez, na primeira tela com avatar que abrir (Hoje ou Troféus). Não há avatar dentro dos players.
- **Onde aparece:** tela Troféus (grande, 4 dp por pixel) com a frase do dia; tela Hoje (3 dp) só quando não há sessão para começar (dia de descanso ou sessão concluída), no lugar da ilustração; onboarding. O sprite antigo "avatar" foi removido.
- **Textos:** as frases do bichinho são fixas, em pt-BR, no `core` (`AvatarState.kt`); a descrição de acessibilidade diz o estado e o adesivo.
- **Folha de contato:** `AvatarContactSheetTest` desenha todos os estados e eventos quadro a quadro (8x, claro e escuro), os adesivos e a seguida das poses em `build/previews/avatar_contact_sheet.png`.
- **Limites conhecidos da arte:** a corrida (`run_*`) e o levantamento acima da cabeça (`lift_b`) são as poses mais fracas; o sono é em pé, com a cabeça caída. Revisar no aparelho.

## 0.10.0 — Avatar com a arte de referência

- A arte 32x32 desenhada no código foi trocada pelas seis poses do menino da imagem de referência do usuário, reduzidas automaticamente para 64x64 em 4 tons (recorte, fundo removido, quantização por luminância global, remoção de pontos soltos). Sem retoque manual além disso. Os scripts ficam fora do repositório (scratchpad: `kid_art.py`, `kid_export.py`); `AvatarArt.kt` é gerado.
- Mapeamento: NORMAL = parado; ENERGIZED = músculos; TIRED/SLUGGISH = cansado; SLEEPING = cansado + "zZ"; treino de musculação = levantando; corrida = correndo; nível/troféu = comemorando; toque = músculos.
- Cada pose tem um quadro `_a` e um `_b` (o mesmo desenho deslocado 1–2 px): é daí que vem o movimento. O comportamento de 9–15 s é alongar/músculos quando bem, e cochilar quando cansado.
- Removidos: olhos e bocas como camadas (piscar, olhar para os lados, bocejar) e os efeitos de suor, poeira e faíscas, que já vêm desenhados em algumas poses.
- Adesivos (7x6) ficam na bandana, com âncora por pose.
- Limites conhecidos: ruído de pontilhado nas áreas médias; dormindo usa a pose cansada (olhos entreabertos). Próximo passo possível: retoque manual por pose, olhos fechados desenhados, outros personagens (gato, pinguim, fantasma).

## Descanso na notificação (pós-0.10.0, ainda sem build)

- O descanso entre séries agora também aparece como notificação com contagem regressiva desenhada pelo sistema (`ui/player/RestNotifier.kt`), então continua ao sair da tela ou do app. Um alarme inexato (`setAndAllowWhileIdle`, sem permissão extra) avisa "Descanso acabou" mesmo se o app morrer; pode atrasar em Doze.
- Ajustar o tempo reinicia a notificação; pular o descanso ou finalizar a sessão cancela. A permissão de notificações é pedida uma vez ao abrir o player (Android 13+); sem ela nada aparece.
- Não compilado nem testado: o usuário pediu para não buildar.
- Treino em andamento: ao abrir o player de uma sessão não concluída, `WorkoutService` (serviço em primeiro plano, tipo `specialUse`) mostra uma notificação fixa com o título e o tempo decorrido e mantém o app vivo em segundo plano; termina ao finalizar o treino (ou sozinho após 4 h). Sair da tela não cancela mais o descanso nem o serviço. `specialUse` exige declaração na Play Console (anotar em docs/PUBLICACAO.md). Também sem build.

## Jogador, XP e player (pós-0.10.0, sem build)

- XP das atividades reduzido, mantendo a curva de níveis (decisão dele): ver `GameEngine`, `WeeklyMissions` e `AchievementCatalog.xpOf`. O XP é recalculado do histórico, então o nível dele cai; o overlay de level up já ignora níveis menores que o visto.
- A aba "Troféus" virou "Jogador" com sub-menu (Perfil, Coleção, Recordes, XP) e a tabela de pontos vem de `XpTable`, gerada das constantes.
- Player: o descanso aparece no lugar da barra; o botão de registrar a série fica fixo no rodapé.

## Pipeline do avatar (tarefa autônoma de 2026-11-07)
- O prompt foi escrito antes do boneco atual: pedia 32x32 e um boneco provisório com bandana. Como a arte real já existe (o desenho dele) e ele recusou bandana e outro chibi, o pipeline usa o desenho dele em `assets_src/avatar_base.png`. Não há placeholder.
- Tamanho: em vez de reduzir para 32x32 (já tentamos e o desenho se perde), o `sprite` recupera a grade de pixels do desenho, que já é pixel art (42x66), e o rig monta quadros de 56x76. Se a imagem não for pixel art, cai na redução por caixa para a altura do `avatar_rig.json`.
- O sprite fica em `assets_src/avatar_sprite.png` (não `avatar_32.png`, porque não tem 32 px).
- Animações do prompt, mapeadas para os quadros do rig: idle, blink (novo), tired, sluggish (novo, com bocejo), sleeping, celebrate, train_strength e train_run, mais walk e flex, que já existiam.
- Relógio a 5 fps (200 ms), dentro dos 4 a 6 do prompt; antes era 10 fps. O passeio anda 1 px por tique.
- Itens: os adesivos continuam no chão, ao lado do bichinho (pedido dele, 2026-11-03). O JSON aceita itens presos ao corpo (`anchor: body`), que acompanham o movimento, para o futuro.
- O app carrega `assets/avatar/spritesheet.png` + `.json`. Se faltar ou não bater o tamanho, usa a arte embutida em `AvatarArt.kt`, que o mesmo `build.py` gera (fallback com a mesma arte).
- A folha de contato do pipeline é `build/previews/avatar_contact_sheet.png`. A do teste do core, que mostra estados e eventos do animador, passou a se chamar `build/previews/avatar_states_sheet.png`.

## Treino complementar (2026-11-08)
- Formato escolhido: `"optional": true` em qualquer sessão do plano (ele pediu para fazer sem definir entre "segundo treino no dia" e "sessão extra"; o campo cobre os dois). Bike em casa entra como sessão `strength` com "Bike ergométrica" em tempo.
- Sessão opcional pendente ou pulada não existe para o jogo (XP, bichinho, missões, semana perfeita, total do bloco); feita, conta como qualquer sessão.
- Hoje: o destaque vai para a sessão obrigatória; a opcional aparece como cartão com "Extra". "Dia concluído" considera só as obrigatórias. A semana mostra o check do dia mesmo com um extra pendente.
- Lembretes ignoram sessões opcionais.
- Banco: coluna `optional` em `PlannedSessionEntity`, migração 4 -> 5. Planos já importados ficam todos obrigatórios.
- Prompt: instrução para propor no máximo 2 extras leves por semana, nunca no lugar das principais.

## Fotos dos exercícios (2026-11-08)
- Fonte: free-exercise-db (Unlicense). Ele deriva do wrkout/exercises.json, também Unlicense. A origem das fotos não é documentada além disso: risco pequeno na publicação da Play, nenhum para uso pessoal.
- Converter foto em sprite de 4 tons ficou ilegível, porque a pessoa se mistura com o fundo da academia. Recortar a pessoa precisaria do rembg, que pede instalação de código e modelo de fora, e foi bloqueado. Ficou um visual "Game Boy Camera": cinza equalizado, 128x85, 5 tons, legível.
- GIF não entra no app: o player alterna os 2 quadros, que seguem a paleta e o modo escuro, e o asset fica menor (396 KB). Os GIFs existem só como prévia.
- Crédito pequeno sob a foto: "foto: free-exercise-db (domínio público)".

## Nomenclatura TrainBit.exe (2026-11-08)
- Produto: **TrainBit.exe**. Nome interno do código continua Hybrid Coach (namespace, pacotes, classes, tema `Theme.HybridCoach` e as ações do `RunService` não mudaram).
- `app_name` é o rótulo do ícone: "TrainBit.exe" no main e "Treino_debug.exe" em `app/src/debug/res/values/strings.xml`. Dentro do app (abertura e boas-vindas) o nome vem de `product_name`, sempre "TrainBit.exe", para a abertura não mostrar "Treino_debug.exe". A abertura deixou de pôr o nome em maiúsculas, para manter o T e o B.
- O texto da bateria ("procure X e escolha Não otimizar") usa `app_name`, porque é o nome que aparece na lista do Android.
- "Ferro & Asfalto" (nome anterior do rótulo) também foi trocado por TrainBit.exe, e o "C:\HYBRID>" dos terminais virou "C:\TRAINBIT>".
- applicationId: AGP 8.7 só deixa o build type acrescentar sufixo, então o release recebe `com.ithalo.trainbit` pela API de variantes (`androidComponents.onVariants`, em `app/build.gradle.kts`). O debug segue `com.ithalo.hybridcoach`. Conferido no manifesto mesclado e com `aapt dump badging` nos dois APKs.
- O release sai sem assinatura (`app-release-unsigned.apk`); a chave de assinatura fica para a publicação (docs/PUBLICACAO.md).
- `.gitignore`: além de build e segredos, deixa de fora tudo que é pessoal: `.claude/` e quase toda a `docs/` (só vão `docs/README.md`, `schema_v1.md`, `AVATAR_ARTE.md` e `store/`). Pedido dele: subir o projeto sem os arquivos pessoais.
- Busca por segredos: nada encontrado (chaves de API, tokens, chaves privadas, keystores, `.env`). `local.properties` só tem o caminho do SDK e já era ignorado.

## Organização das pastas (2026-11-08)
- `docs/` virou só anotações de trabalho e é ignorada inteira pelo git. Nada nela é preciso para compilar ou rodar.
- Saíram de lá: o contrato do JSON para `schema/schema_v1.md`; o guia de troca da arte para `tools/avatar/ARTE.md`; o ícone da loja para `assets_src/store/icon-512.png`.
- Prévias geradas (folhas do avatar, fotos e GIFs dos exercícios) vão para `build/previews/` (ignorada): `tools/avatar/build.py`, `tools/exercises/build_photos.py` e o `AvatarContactSheetTest` escrevem lá.
