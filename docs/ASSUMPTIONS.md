# Suposições e decisões

## Fase 0
- **Módulo `core`** (Kotlin/JVM puro, sem Android) contém o validador, para rodar testes sem SDK. O módulo `app` (Android) entra na Fase 1; as fixtures já ficam em `app/src/test/resources/` e os assets em `app/src/main/assets/`, e `core` os usa como recursos de teste.
- **`assets/`** do SPEC = `app/src/main/assets/` (`exercises.json`, `prompt_template.md`).
- **Campos obrigatórios** (SPEC não listou): `plan.{name,start_date,duration_weeks}`, `weeks[].{weeks,sessions}`, `session.{day_of_week,type,title}` + `exercises`/`blocks`, `exercise.{name,sets,reps,rest_seconds}`, `block.kind` + um entre duração/distância. O resto tem padrão (ver `docs/schema_v1.md`).
- **`repeat` em corrida** repete o bloco isolado; o intervalado usa o mesmo `repeat` no `interval` e no `recovery` seguinte (as fixtures seguem isso).
- **Ordem de semanas**: grupos podem aparecer em qualquer ordem em `weeks[]`.
- **Template do prompt**: marcadores `{{CHAVE}}` e seções condicionais `{{#CHAVE}}...{{/CHAVE}}` (omitidas quando vazias); o gerador (Fase 1) substitui `{{EXAMPLE_JSON}}` pelo conteúdo de `valid_full.json`.
- **Dependências**: `kotlinx-serialization-json` (JSON, exigido no SPEC) e `kotlin-test` (testes). Kotlin 2.0.21, Gradle 8.9 (wrapper). Sem toolchain fixa; compila com o JDK 21 local.
- **Normalização de nomes** (acentos/caixa/espaços) só é verificada em `ExercisesAssetTest` para garantir que nenhum apelido aponta para dois exercícios; a classe de normalização de produção fica para a Fase 1.
