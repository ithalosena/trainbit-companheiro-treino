# Hybrid Coach

App Android de coach de treino híbrido (musculação + corrida). Especificação completa em [SPEC.md](SPEC.md).

## Status
Fase 0 (contrato) concluída: formato do plano, validador, assets e fixtures. Sem UI ainda.

## Como rodar os testes
Requer JDK 17+ (testado com 21).

```
./gradlew :core:test
```

## Estrutura
- `core/` — módulo Kotlin puro: modelo do plano, extração de JSON e `PlanValidator`.
- `app/src/test/resources/` — fixtures (`valid_full`, `valid_min`, `invalid_*`, `wrapped_in_markdown`).
- `app/src/main/assets/` — `exercises.json` (60 exercícios + apelidos) e `prompt_template.md`.
- `docs/schema_v1.md` — formato final do JSON; `docs/ASSUMPTIONS.md` — decisões e suposições.
