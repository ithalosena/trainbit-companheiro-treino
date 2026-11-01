# SPEC — Hybrid Coach (app Android de coach de treino híbrido)

> Documento de especificação para construção com Claude Code. Leia inteiro antes de escrever código. Trabalhe **fase por fase** e pare ao fim de cada uma (veja "Fases"). Não pergunte sobre decisões já tomadas aqui; onde algo não estiver definido, escolha o padrão mais simples, siga em frente e registre a suposição em `docs/ASSUMPTIONS.md`.

## 1. Visão

App pessoal (uso do autor primeiro; distribuição só se funcionar) que:

1. Faz um **questionário** (sistema especialista, 100% mecânico, sem IA no app na v1).
2. Gera um **prompt** que o usuário copia e cola numa IA de chat da sua preferência.
3. Recebe de volta um **JSON** (formato definido aqui), valida e **importa** como plano de treino de 4 a 12 semanas (bloco fixo), distribuído em calendário.
4. Funciona como **coach durante o treino**: player de musculação (timer de descanso, voz, registro de falhas) e player de corrida (GPS, aviso falado de pace).
5. **Exporta um resumo** do bloco executado, que entra no prompt do bloco seguinte (loop: gerar → executar → exportar → gerar).

### Fora de escopo (não implemente)
Login, servidor/backend, contas, social, nutrição, iOS, frequência cardíaca (Fase 4), chamada de API de IA dentro do app (Fase 4), pagamento, anúncios, sincronização em nuvem.

## 2. Plataforma e stack

- Android nativo, **Kotlin**, **Jetpack Compose (Material 3)**, arquitetura MVVM (`ViewModel` + `StateFlow`).
- **Room** para dados, **DataStore** para configurações, **kotlinx.serialization** para JSON.
- minSdk 26, targetSdk a última estável.
- Dispositivo de teste principal: Poco X4 Pro (aparelho modesto; se rodar bem nele, está bom). Idioma da UI e da voz: **pt-BR**. Unidades: kg, km, min/km.
- Package: `com.ithalo.hybridcoach` (alterável).
- Sem injeção de dependência pesada: construtores simples ou um container manual.
- Testes unitários obrigatórios para: validador de JSON, normalização de nomes de exercício, suavização de pace, cálculo de datas do calendário.

## 3. Formato do plano (JSON v1)

O prompt (seção 5) exige que a IA responda **somente** com este JSON. O app **rejeita** versões desconhecidas.

### 3.1 Estrutura

```json
{
  "schema_version": 1,
  "plan": {
    "name": "Bloco 1 - Base híbrida",
    "start_date": "2026-11-09",
    "duration_weeks": 4,
    "goal": "Melhorar 5 km e ganhar força geral",
    "notes": "Texto livre curto"
  },
  "weeks": [
    {
      "weeks": [1, 2],
      "focus": "Adaptação",
      "is_deload": false,
      "sessions": [
        {
          "day_of_week": "mon",
          "type": "strength",
          "title": "Superiores",
          "estimated_minutes": 60,
          "exercises": [
            {
              "name": "Supino reto com barra",
              "sets": 4,
              "reps": "6-8",
              "load_kg": 40,
              "rest_seconds": 120,
              "rpe_target": 8,
              "notes": ""
            }
          ]
        },
        {
          "day_of_week": "tue",
          "type": "run",
          "title": "Intervalado leve",
          "estimated_minutes": 40,
          "blocks": [
            { "kind": "warmup", "duration_seconds": 600, "target_pace_sec_per_km": { "min": 400, "max": 440 }, "repeat": 1 },
            { "kind": "interval", "distance_m": 400, "target_pace_sec_per_km": { "min": 300, "max": 330 }, "repeat": 6 },
            { "kind": "recovery", "duration_seconds": 90, "target_pace_sec_per_km": null, "repeat": 1 },
            { "kind": "cooldown", "duration_seconds": 600, "target_pace_sec_per_km": { "min": 420, "max": 480 }, "repeat": 1 }
          ]
        }
      ]
    },
    {
      "weeks": [3],
      "focus": "Progressão",
      "is_deload": false,
      "sessions": []
    },
    {
      "weeks": [4],
      "focus": "Deload",
      "is_deload": true,
      "sessions": []
    }
  ]
}
```

(Em arquivos reais `sessions` das semanas 3 e 4 não ficam vazias; o exemplo acima é só ilustrativo. A fixture `valid_full.json` deve ter todas as semanas preenchidas.)

### 3.2 Regras de campos

- `weeks[].weeks`: lista de números de semana (1..`duration_weeks`) que compartilham as mesmas sessões. Isso evita repetir semanas idênticas e reduz o tamanho da resposta da IA. Toda semana de 1 a `duration_weeks` deve aparecer **exatamente uma vez**.
- `day_of_week`: `mon|tue|wed|thu|fri|sat|sun`. Dias sem sessão simplesmente não aparecem (descanso). Máximo de 2 sessões no mesmo dia.
- `type`: `strength` ou `run`. Sessão `strength` tem `exercises`; sessão `run` tem `blocks`.
- `reps`: string (`"8"`, `"6-8"`, `"AMRAP"`, `"30s"`). Não converta para número.
- `load_kg`: número ou `null` (null = usuário escolhe na hora; o app pede a carga ao registrar).
- `rest_seconds`: 0 a 600. `sets`: 1 a 10. `rpe_target`: 1 a 10 ou null.
- Bloco de corrida: exatamente um entre `duration_seconds` e `distance_m`. `kind`: `warmup|steady|interval|recovery|cooldown`. `repeat` >= 1. `target_pace_sec_per_km`: objeto `{min,max}` com `min <= max` (segundos por km; **min = mais rápido**) ou `null`. Faixa plausível: 150 a 900.
- `duration_weeks`: 1 a 12. `start_date`: ISO `YYYY-MM-DD`, preferencialmente uma segunda-feira (se não for, o app avisa e usa a semana corrente do calendário a partir dessa data).
- Campos desconhecidos: ignorar com aviso (não rejeitar). Campos obrigatórios ausentes: rejeitar.

### 3.3 Validação e mensagens de erro

O validador devolve uma lista de erros no formato `caminho: mensagem em português simples`, por exemplo `weeks[1].sessions[0].exercises[2].sets: esperado número entre 1 e 10, veio "três"`.

Se houver erros, a tela de importação mostra a lista e um botão **"Copiar pedido de correção"**, que gera:

> "O JSON que você enviou falhou na validação com estes erros: [lista]. Corrija e responda somente com o JSON completo e válido, sem texto antes ou depois."

Aceite JSON dentro de cercas de código (``` ou ```json) e com texto em volta: extraia o primeiro objeto JSON de nível superior. Rejeite se houver mais de um candidato ambíguo.

### 3.4 Normalização de exercícios

- Arquivo `assets/exercises.json`: lista curta inicial (~60 exercícios comuns de musculação em pt-BR) com `id`, `canonical_name` e `aliases` (variações em pt e en, como "supino reto", "bench press", "supino com barra").
- Na importação, cada `name` é casado (normalizando acentos, caixa e espaços) com `canonical_name` ou `aliases`.
- Não casou: marcar como **"não reconhecido"** na prévia, permitir ao usuário mapear para um existente ou criar exercício novo (que fica salvo com o apelido). O histórico sempre usa o `id` do exercício.

## 4. Questionário (sistema especialista)

Fluxo em etapas (stepper), uma tela por bloco de perguntas, com validação e valores padrão sensatos. Tudo é salvo localmente e pode ser reutilizado no bloco seguinte (pré-preenchido).

1. **Objetivo:** prioridade (corrida / musculação / equilibrado), objetivo em texto livre curto, prova-alvo e data (opcional).
2. **Corrida hoje:** km por semana, maior corrida recente, tempo recente de 5 km ou 10 km (opcional), sessões por semana desejadas.
3. **Musculação hoje:** tempo de treino, sessões por semana, divisão preferida (ou "deixe a IA decidir"), cargas conhecidas nos principais exercícios (ou "não sei").
4. **Disponibilidade:** dias da semana possíveis, minutos por sessão (corrida e musculação), dia preferido do longão e do treino de pernas.
5. **Local e equipamento:** academia completa / equipamentos limitados / casa; esteira ou rua.
6. **Limitações:** lesões ou dores atuais e exercícios a evitar (texto livre; ficam só no aparelho).
7. **Recuperação:** qualidade média do sono (1-5), nível de estresse (1-5).
8. **Bloco:** duração (4 a 12 semanas), data de início, preferência de deload (incluir ou não).
9. **Se houver bloco anterior exportado:** perguntar se o resumo do bloco anterior deve ser incluído no prompt (padrão: sim).

Regras do especialista (mecânicas, sem IA): se corrida + musculação somam mais sessões do que dias disponíveis, avisar na tela e pedir ajuste; se "corrida km/semana" for 0, marcar iniciante e adicionar ao prompt instrução de progressão conservadora; se houver limitação preenchida, incluir o bloco de limitações no prompt com ênfase.

## 5. Gerador de prompt

Template em `assets/prompt_template.md`, com placeholders preenchidos pelo app. Estrutura:

1. Papel: "Você é um treinador que monta blocos de treino híbrido (musculação + corrida). Seja conservador na progressão de carga e de volume."
2. Perfil do usuário (respostas do questionário, em lista).
3. Limitações e exercícios a evitar (se houver).
4. Resumo do bloco anterior (se houver; formato da seção 7).
5. Instruções: usar apenas exercícios comuns, nomes em português, incluir deload se solicitado, respeitar dias e minutos disponíveis, preencher `load_kg` com `null` quando não houver base.
6. **Regras de saída:** responder **somente** com JSON válido, sem comentários, sem texto fora do JSON, sem vírgula sobrando; seguir exatamente o schema.
7. O schema descrito em campos (seção 3.2) e **um exemplo completo** (a fixture `valid_full.json`).
8. Lembrete final de formato.

Tela do prompt: botão **Copiar**, botão **Compartilhar**, contador de caracteres, aviso de que o texto vai para uma IA externa e contém os dados do questionário.

## 6. Modelo de dados (Room)

Entidades principais (nomes sugeridos):

- `Plan` (id, name, startDate, durationWeeks, goal, notes, rawJson, createdAt, status)
- `PlannedSession` (id, planId, weekNumber, dayOfWeek, date, type, title, estimatedMinutes, status: pending/done/skipped, skipReason)
- `PlannedExercise` (id, sessionId, exerciseId, order, sets, reps, loadKg?, restSeconds, rpeTarget?, notes)
- `RunBlock` (id, sessionId, order, kind, durationSeconds?, distanceM?, paceMin?, paceMax?, repeat)
- `Exercise` (id, canonicalName) e `ExerciseAlias` (exerciseId, alias)
- `SetLog` (id, sessionId, plannedExerciseId, setIndex, repsDone, loadKg, failed, rpe?, timestamp)
- `RunLog` (id, sessionId, startedAt, endedAt, distanceM, durationSeconds, avgPace, notes) e `RunSplit` (runLogId, kmIndex, seconds)
- `SessionFeedback` (sessionId, rpeOverall 1-10, note)
- `QuestionnaireAnswers` (json) e configurações em DataStore.

`PlannedSession.date` é calculada: `start_date` + (semana - 1) × 7 dias + deslocamento do dia da semana. O usuário pode **remarcar** uma sessão (muda `date`, preserva o plano). Guardar `rawJson` original do plano para reimportar/depurar.

Não armazenar a trilha completa do GPS por padrão; guardar só resumo e parciais por km (opção em Configurações para guardar a trilha, desligada por padrão).

## 7. Resumo exportável (para o próximo bloco)

Botão **"Exportar resumo do bloco"** gera JSON compacto (e uma versão em texto para colar). Conteúdo:

```json
{
  "summary_version": 1,
  "plan_name": "Bloco 1 - Base híbrida",
  "period": { "start": "2026-11-09", "end": "2026-12-06" },
  "adherence": { "planned_sessions": 16, "done": 13, "skipped": 3, "skip_reasons": ["cansaço", "trabalho", "chuva"] },
  "strength": [
    {
      "exercise": "Supino reto com barra",
      "planned": { "sets": 4, "reps": "6-8", "load_kg": 40 },
      "done": { "best_set": { "reps": 8, "load_kg": 45 }, "failed_sets": 2, "avg_rpe": 8.5, "load_trend_kg": [40, 40, 42.5, 45] }
    }
  ],
  "runs": [
    { "week": 2, "type": "interval", "planned_pace_sec_per_km": { "min": 300, "max": 330 }, "actual_avg_pace_sec_per_km": 338, "distance_km": 5.2, "avg_rpe": 8 }
  ],
  "user_notes": ["joelho direito incomodou na semana 3"]
}
```

Regras: **resumir, não despejar dados brutos**; incluir sessões puladas e o motivo; incluir RPE médio; limitar o tamanho (alvo menor que 6.000 caracteres; se passar, agregar por semana). O resumo é incluído na seção 4 do prompt do bloco seguinte.

## 8. Telas

1. **Hoje:** sessão de hoje (ou próxima), botão Iniciar, estado da semana.
2. **Calendário:** visão semana e mês, com status por sessão; tocar abre detalhe; remarcar/pular.
3. **Questionário** (stepper) e **Prompt** (copiar/compartilhar).
4. **Importar:** campo para colar JSON (ou abrir arquivo), validação, prévia por semana, mapeamento de exercícios não reconhecidos, botão Confirmar.
5. **Player de musculação** (seção 9).
6. **Player de corrida** (seção 10).
7. **Histórico** por sessão e por exercício (gráfico simples de carga ao longo do tempo).
8. **Exportar resumo** e **Configurações** (voz, volume, tolerância de pace, unidades, guardar trilha GPS, limpar dados).

Tema claro/escuro seguindo o sistema. Tela do player sempre acesa. Botões grandes e legíveis durante o treino.

## 9. Player de musculação

- Mostra exercício atual, série X de Y, repetições-alvo, carga-alvo, e campos para **reps feitas**, **carga usada**, **falhou?** (toggle) e RPE opcional (1-10).
- Ao concluir uma série, inicia automaticamente o **timer de descanso** (`rest_seconds`), com aviso falado ao zerar e contagem final (3-2-1). Permitir +15 s / -15 s e pular descanso.
- Se `load_kg` for null, pedir a carga antes da primeira série e sugerir a última carga usada naquele exercício.
- Ações por exercício: **substituir** (escolher outro da lista; registrar o motivo), **pular** (com motivo), adicionar série extra.
- Ao final: RPE geral da sessão e nota livre. Marcar sessão como concluída.
- Estado da sessão sobrevive a rotação de tela e a processo morto (salvar em Room a cada série).
- Voz em pt-BR via `TextToSpeech`: "Próximo: supino reto, série 2 de 4", "Descanso terminado".

## 10. Player de corrida

Objetivo: coach de voz que avisa pace, tempo e marcos, **sem frequência cardíaca nesta versão**.

- **Localização:** `FusedLocationProvider`, atualização a ~1 s, descartar leituras com acurácia pior que 20 m; ignorar saltos fisicamente impossíveis.
- **Serviço em primeiro plano** (tipo localização) com notificação persistente, `WakeLock` parcial; funcionar com tela bloqueada. Pedir permissões em sequência (localização precisa, depois "sempre", notificações) com telas de explicação. Orientar o usuário a retirar o app da otimização de bateria.
- **Pace:** média móvel configurável (padrão **20 s**, opções 10/20/30 s). Comparar com a faixa `target_pace_sec_per_km` do bloco atual.
- **Avisos de pace (usar "acelere"/"reduza", nunca "pace alto/baixo", que é ambíguo):**
  - Fora da faixa por mais de **10 s contínuos**: falar "Acelere, pace atual X" (mais lento que `max`) ou "Reduza, pace atual X" (mais rápido que `min`).
  - **Tolerância** configurável (padrão ±5 s/km além da faixa).
  - **Intervalo mínimo entre avisos:** 30 s (configurável). Avisar "No ritmo" ao voltar para a faixa (no máximo uma vez por minuto).
  - Sem faixa (`null`): sem avisos de pace nesse bloco.
- **Marcos falados:** início e fim de cada bloco, a cada km (tempo do km e pace médio), metade do tempo/distância do bloco, contagem para o próximo bloco de intervalo, fim do treino com resumo (distância, tempo, pace médio).
- **Áudio:** usar foco de áudio para **abaixar** a música enquanto fala (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) e voltar ao normal depois. Saída pelo fone Bluetooth do usuário.
- **Controles:** pausar/retomar, pular bloco, encerrar; salvar `RunLog` e parciais por km. Recuperar corrida em andamento se o app for fechado.
- **Esteira:** modo sem GPS com entrada manual de distância/velocidade (fora do escopo da primeira versão do player; deixar o ponto de extensão).
- Registrar métricas de bateria (percentual no início e no fim) no `RunLog` para o autor avaliar o consumo.

## 11. Fases e critérios de aceite

Trabalhe **somente a fase solicitada**. Ao terminar, rode os testes, atualize `README.md` e `docs/ASSUMPTIONS.md`, faça commit e pare.

**Fase 0 — Contrato (sem UI)**
- Entregar: `docs/schema_v1.md` (versão final do formato), `assets/prompt_template.md`, `assets/exercises.json` (≈60 exercícios), fixtures em `app/src/test/resources/`: `valid_full.json`, `valid_min.json`, `invalid_missing_field.json`, `invalid_pace_range.json`, `invalid_week_gap.json`, `wrapped_in_markdown.json`.
- Aceite: validador (módulo Kotlin puro, sem Android) com testes passando em todas as fixtures.

**Fase 1 — Plano e calendário**
- Questionário, geração e cópia do prompt, importação com validação e prévia, normalização de exercícios, calendário (semana/mês), remarcar/pular, exportação do resumo (mesmo sem registros ainda, gera o esqueleto).
- Aceite: importar `valid_full.json` e ver todas as sessões nas datas corretas; erros de validação aparecem em português com o caminho do campo; reabrir o app mantém tudo.

**Fase 2 — Player de musculação**
- Seção 9 completa, histórico por exercício, resumo de musculação no export.
- Aceite: treino completo registrado offline, sobrevive a fechar o app no meio, resumo exportado reflete reps, cargas e falhas.

**Fase 3 — Player de corrida**
- Seção 10 completa, resumo de corridas no export.
- Aceite: corrida de teste de 30 min com tela bloqueada e música tocando; avisos de pace seguem as regras; sem avisos repetitivos; RunLog salvo com parciais por km.

**Fase 4 — (não construir agora)** Frequência cardíaca via Galaxy Watch 4 (Wear OS) ou serviço BLE padrão; chamada de API de IA dentro do app com limite de gasto e chave guardada com segurança; ajuste automático do plano.

## 12. Qualidade e convenções

- Código e identificadores em inglês; textos de UI e voz em pt-BR (strings em `strings.xml`).
- Sem dados pessoais em logs. Dados só no aparelho; sem rede, exceto abrir o compartilhamento do sistema.
- Commits pequenos e descritivos, um por funcionalidade. `README.md` com como compilar, rodar testes e estrutura de pastas.
- Não adicione bibliotecas sem necessidade; justifique cada dependência nova em `docs/ASSUMPTIONS.md`.

## 13. Riscos conhecidos (tenha em mente ao decidir)

1. A IA pode não seguir o schema de forma confiável: por isso o validador, as mensagens de correção e o formato `weeks: [1,2]` para reduzir tamanho.
2. Processo em segundo plano com tela bloqueada varia por fabricante: testar no Poco X4 Pro cedo (Fase 3, primeiro marco: serviço de localização vivo por 30 minutos).
3. Nomes de exercícios inconsistentes: normalização com apelidos e mapeamento manual.
4. Plano gerado por IA pode ser inadequado: o app é ferramenta de organização, não prescrição; incluir aviso na tela de importação para revisar o plano e respeitar limitações.
