# Formato do plano — schema v1

Contrato entre a IA (que gera o plano) e o app (que valida e importa). O app **rejeita** `schema_version` diferente de `1`. Implementação de referência: `core/src/main/kotlin/com/ithalo/hybridcoach/core/plan/PlanValidator.kt`. Exemplo completo: `app/src/test/resources/valid_full.json`.

## Estrutura

```
{ schema_version, plan{...}, weeks[ { weeks[], focus, is_deload, sessions[ strength | run ] } ] }
```

### `plan`
| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `name` | texto | sim | não vazio |
| `start_date` | texto | sim | `AAAA-MM-DD`; se não for segunda-feira, **aviso** |
| `duration_weeks` | inteiro | sim | 1 a 12 |
| `goal`, `notes` | texto | não | padrão `""` |

### `weeks[]` (grupo de semanas)
| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `weeks` | lista de inteiros | sim | cada um em 1..`duration_weeks`; toda semana de 1 a `duration_weeks` aparece **exatamente uma vez** em todos os grupos |
| `sessions` | lista | sim | no máximo 2 sessões por `day_of_week`; lista vazia gera aviso |
| `focus` | texto | não | padrão `""` |
| `is_deload` | booleano | não | padrão `false` |

### `sessions[]`
| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `day_of_week` | texto | sim | `mon tue wed thu fri sat sun` |
| `type` | texto | sim | `strength` ou `run` |
| `title` | texto | sim | não vazio |
| `estimated_minutes` | inteiro | não | 1 a 600 |
| `optional` | true/false | não | padrão `false`; `true` numa sessão complementar (bike em casa, corrida extra, segundo treino leve): não fazer não conta como falta |
| `exercises` | lista | sim se `strength` | ao menos 1 |
| `home_exercises` | lista | não (só `strength`) | versão em casa da mesma sessão; mesmo formato de `exercises`; ausente, `null` ou vazia = sem versão em casa |
| `blocks` | lista | sim se `run` | ao menos 1 |

### `exercises[]`
| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `name` | texto | sim | casado com `assets/exercises.json` na importação |
| `sets` | inteiro | sim | 1 a 10 |
| `reps` | **texto** | sim | `"8"`, `"6-8"`, `"AMRAP"`, `"30s"`; número puro é erro (não é convertido) |
| `rest_seconds` | inteiro | sim | 0 a 600 |
| `load_kg` | número ou `null` | não | 0 a 1000; ausente = `null` (usuário escolhe na hora) |
| `rpe_target` | inteiro ou `null` | não | 1 a 10 |
| `notes` | texto | não | padrão `""` |

### `blocks[]` (corrida)
| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `kind` | texto | sim | `warmup steady interval recovery cooldown` |
| `duration_seconds` / `distance_m` | inteiro | **exatamente um** | |
| `target_pace_sec_per_km` | `{min,max}` ou `null` | não | segundos/km, 150 a 900, `min <= max` (`min` = mais rápido); ausente/`null` = sem aviso de pace |
| `repeat` | inteiro | não | 1 a 100, padrão 1 |

`repeat` repete o bloco isolado. Um bloco repetido seguido de um `recovery` com o mesmo `repeat` é intercalado (`interval`+`recovery` no intervalado, `steady`+`recovery` na corrida/caminhada): corrida, recuperação, corrida...

## Campos desconhecidos e obrigatórios
- Desconhecidos: **ignorados com aviso** (`caminho: campo desconhecido, ignorado`).
- Obrigatórios ausentes ou de tipo errado: **erro**, formato `caminho: mensagem`, p. ex. `weeks[1].sessions[0].exercises[2].sets: esperado número inteiro entre 1 e 10, veio "três"`.
- Números devem ser números JSON, não texto (`"4"` é erro); inteiros não aceitam fração.

## Extração da resposta da IA
Texto em volta e cercas ``` ou ```json são aceitos: extrai-se o objeto `{...}` de nível superior. Erros possíveis: nenhum objeto, objeto incompleto (resposta cortada), JSON malformado (p. ex. vírgula sobrando), ou mais de um objeto válido (ambíguo).

## Correção
Com erros, o app oferece o pedido: *"O JSON que você enviou falhou na validação com estes erros: [lista]. Corrija e responda somente com o JSON completo e válido, sem texto antes ou depois."*
