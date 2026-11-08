package com.ithalo.hybridcoach.core.plan

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.floor

/**
 * Validates a plan JSON (schema v1). Error format: `path: message in plain Portuguese`.
 * Unknown fields only produce warnings; missing required fields produce errors.
 */
object PlanValidator {
    private const val PACE_MIN = 150
    private const val PACE_MAX = 900

    private val DAYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY,
    )

    fun validateText(text: String): ValidationResult =
        when (val extracted = JsonExtractor.extract(text)) {
            is ExtractResult.Found -> validate(extracted.json)
            is ExtractResult.Failed -> ValidationResult(null, listOf("json: ${extracted.message}"), emptyList())
        }

    fun validate(root: JsonObject): ValidationResult {
        val ctx = Ctx()
        val doc = ctx.document(root)
        val errors = ctx.errors.toList()
        return ValidationResult(if (errors.isEmpty()) doc else null, errors, ctx.warnings.toList())
    }

    private class Ctx {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        fun error(path: String, message: String) { errors += "$path: $message" }
        fun warn(path: String, message: String) { warnings += "$path: $message" }

        fun unknownFields(o: JsonObject, known: Set<String>, path: String) {
            for (key in o.keys) if (key !in known) warn(join(path, key), "campo desconhecido, ignorado")
        }

        fun obj(el: JsonElement?, path: String): JsonObject? = when (el) {
            null, JsonNull -> { error(path, "campo obrigatório ausente"); null }
            is JsonObject -> el
            else -> { error(path, "esperado objeto, veio ${show(el)}"); null }
        }

        fun array(o: JsonObject, key: String, path: String): JsonArray? {
            val p = join(path, key)
            return when (val el = o[key]) {
                null, JsonNull -> { error(p, "campo obrigatório ausente"); null }
                is JsonArray -> el
                else -> { error(p, "esperado lista, veio ${show(el)}"); null }
            }
        }

        fun string(o: JsonObject, key: String, path: String, required: Boolean = true): String? {
            val p = join(path, key)
            val el = o[key]
            if (el == null || el is JsonNull) {
                if (required) error(p, "campo obrigatório ausente")
                return null
            }
            if (el !is JsonPrimitive || !el.isString) {
                error(p, "esperado texto, veio ${show(el)}")
                return null
            }
            if (required && el.content.isBlank()) {
                error(p, "esperado texto não vazio")
                return null
            }
            return el.content
        }

        fun bool(o: JsonObject, key: String, path: String): Boolean? {
            val el = o[key]
            if (el == null || el is JsonNull) return null
            if (el is JsonPrimitive && !el.isString && (el.content == "true" || el.content == "false")) {
                return el.content == "true"
            }
            error(join(path, key), "esperado verdadeiro ou falso (true/false), veio ${show(el)}")
            return null
        }

        fun number(o: JsonObject, key: String, path: String, required: Boolean, range: ClosedRange<Double>? = null): Double? {
            val p = join(path, key)
            val el = o[key]
            if (el == null || el is JsonNull) {
                if (required) error(p, "campo obrigatório ausente")
                return null
            }
            val value = (el as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
            if (value == null || value.isNaN() || value.isInfinite() || (range != null && value !in range)) {
                val expected = if (range != null) "número entre ${fmt(range.start)} e ${fmt(range.endInclusive)}" else "número"
                error(p, "esperado $expected, veio ${show(el)}")
                return null
            }
            return value
        }

        fun int(o: JsonObject, key: String, path: String, range: IntRange, required: Boolean = true): Int? {
            val p = join(path, key)
            val el = o[key]
            if (el == null || el is JsonNull) {
                if (required) error(p, "campo obrigatório ausente")
                return null
            }
            val value = (el as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
            if (value == null || value != floor(value) || value < range.first || value > range.last) {
                error(p, "esperado número inteiro entre ${range.first} e ${range.last}, veio ${show(el)}")
                return null
            }
            return value.toInt()
        }

        // --- document ---

        fun document(root: JsonObject): PlanDocument? {
            unknownFields(root, setOf("schema_version", "plan", "weeks"), "")
            val version = int(root, "schema_version", "", 0..Int.MAX_VALUE)
            if (version != null && version != SUPPORTED_SCHEMA_VERSION) {
                error("schema_version", "versão $version não suportada; este app aceita somente a versão $SUPPORTED_SCHEMA_VERSION")
                return null
            }
            if (version == null) return null

            val planObj = obj(root["plan"], "plan")
            val info = planObj?.let { planInfo(it) }
            val weeksArray = array(root, "weeks", "")
            val duration = info?.durationWeeks
            val groups = weeksArray?.mapIndexedNotNull { i, el ->
                obj(el, "weeks[$i]")?.let { weekGroup(it, "weeks[$i]", duration) }
            }
            if (groups != null && weeksArray?.size == groups.size && duration != null) coverage(groups, duration)
            if (info == null || groups == null) return null
            return PlanDocument(version, info, groups)
        }

        fun planInfo(o: JsonObject): PlanInfo? {
            val path = "plan"
            unknownFields(o, setOf("name", "start_date", "duration_weeks", "goal", "notes"), path)
            val name = string(o, "name", path)
            val duration = int(o, "duration_weeks", path, 1..12)
            val date = startDate(o, path)
            val goal = string(o, "goal", path, required = false).orEmpty()
            val notes = string(o, "notes", path, required = false).orEmpty()
            if (name == null || duration == null || date == null) return null
            return PlanInfo(name, date, duration, goal, notes)
        }

        fun startDate(o: JsonObject, path: String): LocalDate? {
            val raw = string(o, "start_date", path) ?: return null
            val date = try {
                if (raw.length != 10) throw DateTimeParseException("len", raw, 0)
                LocalDate.parse(raw)
            } catch (e: DateTimeParseException) {
                error(join(path, "start_date"), "esperado data no formato AAAA-MM-DD, veio \"$raw\"")
                return null
            }
            if (date.dayOfWeek != DayOfWeek.MONDAY) {
                warn(join(path, "start_date"), "$raw não é uma segunda-feira; o app usará a semana do calendário que contém essa data")
            }
            return date
        }

        fun weekGroup(o: JsonObject, path: String, duration: Int?): WeekGroup? {
            unknownFields(o, setOf("weeks", "focus", "is_deload", "sessions"), path)
            val numbers = weekNumbers(o, path, duration)
            val focus = string(o, "focus", path, required = false).orEmpty()
            val deload = bool(o, "is_deload", path) ?: false
            val sessionsArray = array(o, "sessions", path)
            val sessions = sessionsArray?.mapIndexedNotNull { i, el ->
                obj(el, "$path.sessions[$i]")?.let { session(it, "$path.sessions[$i]") }
            }
            if (sessionsArray != null && sessionsArray.isEmpty()) {
                warn("$path.sessions", "grupo de semanas sem nenhuma sessão")
            }
            if (sessions != null) {
                sessions.groupBy { it.dayOfWeek }.forEach { (day, list) ->
                    if (list.size > 2) {
                        error("$path.sessions", "no máximo 2 sessões no mesmo dia, mas ${dayCode(day)} tem ${list.size}")
                    }
                }
            }
            if (numbers == null || sessions == null || sessions.size != sessionsArray?.size) return null
            return WeekGroup(numbers, focus, deload, sessions)
        }

        fun weekNumbers(o: JsonObject, path: String, duration: Int?): List<Int>? {
            val p = join(path, "weeks")
            val arr = array(o, "weeks", path) ?: return null
            if (arr.isEmpty()) { error(p, "esperada ao menos uma semana na lista"); return null }
            val upper = duration ?: 12
            val out = mutableListOf<Int>()
            var ok = true
            arr.forEachIndexed { i, el ->
                val v = (el as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
                if (v == null || v != floor(v) || v < 1 || v > upper) {
                    error("$p[$i]", "esperado número inteiro entre 1 e $upper, veio ${show(el)}")
                    ok = false
                } else {
                    out += v.toInt()
                }
            }
            return if (ok) out else null
        }

        fun coverage(groups: List<WeekGroup>, duration: Int) {
            val seen = mutableMapOf<Int, Int>()
            groups.forEachIndexed { gi, g ->
                g.weeks.forEachIndexed { wi, n ->
                    val first = seen[n]
                    if (first != null) {
                        error("weeks[$gi].weeks[$wi]", "semana $n já aparece em weeks[$first]; cada semana deve aparecer exatamente uma vez")
                    } else {
                        seen[n] = gi
                    }
                }
            }
            val missing = (1..duration).filter { it !in seen }
            if (missing.isNotEmpty()) {
                error("weeks", "faltam as semanas ${missing.joinToString(", ")}; todas de 1 a $duration devem aparecer exatamente uma vez")
            }
        }

        fun session(o: JsonObject, path: String): SessionDef? {
            unknownFields(o, setOf("day_of_week", "type", "title", "estimated_minutes", "optional", "exercises", "home_exercises", "blocks"), path)
            val day = dayOfWeek(o, path)
            val type = string(o, "type", path)
            val title = string(o, "title", path)
            val minutes = int(o, "estimated_minutes", path, 1..600, required = false)
            val optional = optionalFlag(o, path)
            if (type != null && type != "strength" && type != "run") {
                error("$path.type", "esperado \"strength\" ou \"run\", veio \"$type\"")
                return null
            }
            return when (type) {
                "strength" -> {
                    if (o.containsKey("blocks")) warn("$path.blocks", "sessão de musculação não usa blocks, ignorado")
                    val arr = array(o, "exercises", path)
                    val exercises = arr?.mapIndexedNotNull { i, el ->
                        obj(el, "$path.exercises[$i]")?.let { exercise(it, "$path.exercises[$i]") }
                    }
                    if (arr != null && arr.isEmpty()) error("$path.exercises", "esperado ao menos um exercício")
                    val home = homeExercises(o, path)
                    if (day == null || title == null || exercises == null || exercises.size != arr?.size || arr?.isEmpty() != false || home == null) null
                    else StrengthSessionDef(day, title, minutes, exercises, home, optional)
                }
                "run" -> {
                    if (o.containsKey("exercises")) warn("$path.exercises", "sessão de corrida não usa exercises, ignorado")
                    if (o.containsKey("home_exercises")) warn("$path.home_exercises", "sessão de corrida não usa home_exercises, ignorado")
                    val arr = array(o, "blocks", path)
                    val blocks = arr?.mapIndexedNotNull { i, el ->
                        obj(el, "$path.blocks[$i]")?.let { runBlock(it, "$path.blocks[$i]") }
                    }
                    if (arr != null && arr.isEmpty()) error("$path.blocks", "esperado ao menos um bloco")
                    if (day == null || title == null || blocks == null || blocks.size != arr?.size || arr?.isEmpty() != false) null
                    else RunSessionDef(day, title, minutes, blocks, optional)
                }
                else -> null
            }
        }

        /** `optional`: a complementary session. Absent or null is false; anything but true/false is an error. */
        fun optionalFlag(o: JsonObject, path: String): Boolean {
            val el = o["optional"]
            if (el == null || el is JsonNull) return false
            val v = (el as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            if (v == null) error("$path.optional", "esperado true ou false")
            return v == true
        }

        /** Optional `home_exercises`: absent, null or empty means no home version. Null result means errors. */
        fun homeExercises(o: JsonObject, path: String): List<ExerciseDef>? {
            val el = o["home_exercises"]
            if (el == null || el is JsonNull) return emptyList()
            val arr = array(o, "home_exercises", path) ?: return null
            val list = arr.mapIndexedNotNull { i, item ->
                obj(item, "$path.home_exercises[$i]")?.let { exercise(it, "$path.home_exercises[$i]") }
            }
            return if (list.size == arr.size) list else null
        }

        fun dayOfWeek(o: JsonObject, path: String): DayOfWeek? {
            val raw = string(o, "day_of_week", path) ?: return null
            return DAYS[raw] ?: run {
                error("$path.day_of_week", "esperado um entre mon, tue, wed, thu, fri, sat, sun, veio \"$raw\"")
                null
            }
        }

        fun exercise(o: JsonObject, path: String): ExerciseDef? {
            unknownFields(o, setOf("name", "sets", "reps", "load_kg", "rest_seconds", "rpe_target", "notes"), path)
            val name = string(o, "name", path)
            val sets = int(o, "sets", path, 1..10)
            val reps = repsField(o, path)
            val load = number(o, "load_kg", path, required = false, range = 0.0..1000.0)
            val rest = int(o, "rest_seconds", path, 0..600)
            val rpe = int(o, "rpe_target", path, 1..10, required = false)
            val notes = string(o, "notes", path, required = false).orEmpty()
            if (name == null || sets == null || reps == null || rest == null) return null
            if (errorsIn(path)) return null
            return ExerciseDef(name, sets, reps, load, rest, rpe, notes)
        }

        /** reps stays a string ("8", "6-8", "AMRAP", "30s"); a bare number is an error, not coerced. */
        fun repsField(o: JsonObject, path: String): String? {
            val el = o["reps"]
            if (el is JsonPrimitive && !el.isString && el !is JsonNull) {
                error("$path.reps", "esperado texto (ex.: \"8\" ou \"6-8\"), veio ${show(el)}")
                return null
            }
            return string(o, "reps", path)
        }

        fun runBlock(o: JsonObject, path: String): RunBlockDef? {
            unknownFields(o, setOf("kind", "duration_seconds", "distance_m", "target_pace_sec_per_km", "repeat"), path)
            val kindRaw = string(o, "kind", path)
            val kind = kindRaw?.let { raw ->
                RunBlockKind.entries.firstOrNull { it.name.lowercase() == raw } ?: run {
                    error("$path.kind", "esperado um entre warmup, steady, interval, recovery, cooldown, veio \"$raw\"")
                    null
                }
            }
            val duration = int(o, "duration_seconds", path, 1..24 * 3600, required = false)
            val distance = int(o, "distance_m", path, 1..100_000, required = false)
            val hasDuration = o["duration_seconds"].let { it != null && it !is JsonNull }
            val hasDistance = o["distance_m"].let { it != null && it !is JsonNull }
            if (hasDuration == hasDistance) {
                error(path, "informe exatamente um entre duration_seconds e distance_m" + if (hasDuration) ", vieram os dois" else ", veio nenhum")
            }
            val repeat = int(o, "repeat", path, 1..100, required = false) ?: 1
            val pace = pace(o, path)
            if (kind == null || (hasDuration == hasDistance) || errorsIn(path)) return null
            return RunBlockDef(kind, duration, distance, pace.value, repeat)
        }

        class PaceResult(val value: PaceRange?)

        fun pace(o: JsonObject, path: String): PaceResult {
            val key = "target_pace_sec_per_km"
            val p = "$path.$key"
            val el = o[key]
            if (el == null || el is JsonNull) return PaceResult(null)
            if (el !is JsonObject) {
                error(p, "esperado objeto {min, max} ou null, veio ${show(el)}")
                return PaceResult(null)
            }
            unknownFields(el, setOf("min", "max"), p)
            val range = PACE_MIN.toDouble()..PACE_MAX.toDouble()
            val min = number(el, "min", p, required = true, range = range)
            val max = number(el, "max", p, required = true, range = range)
            if (min != null && max != null && min > max) {
                error(p, "min (${fmt(min)}) deve ser menor ou igual a max (${fmt(max)}); min é o pace mais rápido")
                return PaceResult(null)
            }
            return if (min != null && max != null) PaceResult(PaceRange(min.toInt(), max.toInt())) else PaceResult(null)
        }

        /** True if any error was already recorded under [path] (so a partially valid node is not built). */
        fun errorsIn(path: String): Boolean = errors.any { it.startsWith("$path.") || it.startsWith("$path:") }
    }

    private fun join(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"

    private fun fmt(d: Double): String = if (d == floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()

    private fun dayCode(d: DayOfWeek) = DAYS.entries.first { it.value == d }.key

    /** Shows the received value: strings quoted, everything else as written. */
    private fun show(el: JsonElement): String = el.toString().let { if (it.length > 60) it.take(57) + "..." else it }
}
