package com.ithalo.hybridcoach.core.exercises

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer

/**
 * An exercise the app knows. [primary] and [secondary] are pt-BR muscle labels ("peitoral", "tríceps"...);
 * [kind] is "força", "tempo" or "cardio"; [autoTranslated] marks a Portuguese name made by rules, to review.
 */
data class CatalogEntry(
    val id: String,
    val canonicalName: String,
    val aliases: List<String>,
    val nameEn: String? = null,
    val primary: List<String> = emptyList(),
    val secondary: List<String> = emptyList(),
    val equipment: String? = null,
    val kind: String = "força",
    val autoTranslated: Boolean = false,
)

/** A possible match for an unknown name, best first; [score] from 0 to 1. */
data class Suggestion(val entry: CatalogEntry, val score: Double)

/**
 * Matches free-form exercise names: exact name or alias, then the same words in any order (plural, abbreviations
 * and small words ignored), then similarity. Only a similarity of [CERTAIN] or more is taken without asking.
 */
class ExerciseCatalog(val entries: List<CatalogEntry>) {
    private val byName: Map<String, CatalogEntry>
    private val byKey: Map<String, CatalogEntry>
    private val names: List<Pair<String, CatalogEntry>>

    init {
        val exact = LinkedHashMap<String, CatalogEntry>()
        val keyed = LinkedHashMap<String, CatalogEntry>()
        val all = ArrayList<Pair<String, CatalogEntry>>()
        for (entry in entries) {
            for (name in listOf(entry.canonicalName) + entry.aliases + listOfNotNull(entry.nameEn)) {
                exact.putIfAbsent(normalize(name), entry)
                val k = key(name)
                if (k.isNotEmpty()) keyed.putIfAbsent(k, entry)
                all += k to entry
            }
        }
        byName = exact
        byKey = keyed
        names = all
    }

    /** Certain match: the name or an alias, or the same words in another order. */
    fun match(name: String): CatalogEntry? = byName[normalize(name)] ?: byKey[key(name)]

    /** The [limit] most similar exercises, best first, above [MIN_SCORE]. */
    fun suggest(name: String, limit: Int = 3): List<Suggestion> {
        val k = key(name)
        if (k.isEmpty()) return emptyList()
        val best = HashMap<String, Suggestion>()
        for ((candidate, entry) in names) {
            val score = similarity(k, candidate)
            if (score >= MIN_SCORE && (best[entry.id]?.score ?: -1.0) < score) best[entry.id] = Suggestion(entry, score)
        }
        return best.values.sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.entry.autoTranslated }).take(limit)
    }

    /**
     * [match], or the best suggestion when it can be taken without asking: similar enough ([CERTAIN]), or clearly
     * ahead of the next one ([LIKELY] and [MARGIN] over the second).
     */
    fun resolve(name: String): CatalogEntry? {
        match(name)?.let { return it }
        val s = suggest(name, 2)
        val top = s.firstOrNull() ?: return null
        val second = s.getOrNull(1)?.score ?: 0.0
        return top.entry.takeIf { top.score >= CERTAIN || (top.score >= LIKELY && top.score - second >= MARGIN) }
    }

    fun byId(id: String): CatalogEntry? = entries.firstOrNull { it.id == id }

    companion object {
        const val CERTAIN = 0.85
        const val LIKELY = 0.72
        const val MARGIN = 0.12
        const val MIN_SCORE = 0.3

        private val MARKS = Regex("\\p{M}+")
        private val NON_ALNUM = Regex("[^a-z0-9]+")

        /** Small words that do not tell exercises apart. */
        private val STOP = setOf("c", "com", "de", "da", "do", "das", "dos", "na", "no", "nas", "nos", "e", "em", "a", "o", "as", "os", "the", "with", "on", "and", "of")

        /** Abbreviations and spellings that mean the same thing. */
        private val SAME = mapOf(
            "db" to "halter", "dbs" to "halter", "halt" to "halter", "haltere" to "halter", "dumbbell" to "halter",
            "bb" to "barra", "barbell" to "barra", "kb" to "kettlebell", "maq" to "maquina", "machine" to "maquina",
            "cabo" to "polia", "cable" to "polia", "crossover" to "polia", "unilat" to "unilateral", "alt" to "alternado",
            "inc" to "inclinado", "incline" to "inclinado", "dec" to "declinado", "decline" to "declinado",
            "sent" to "sentado", "seated" to "sentado", "pe" to "pe", "standing" to "pe",
        )

        /** Lowercase, no accents, punctuation treated as a space, single spaces. */
        fun normalize(name: String): String =
            Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
                .replace(MARKS, "")
                .replace(NON_ALNUM, " ")
                .trim()

        private fun singular(w: String): String = when {
            w.length > 4 && w.endsWith("oes") -> w.dropLast(3) + "ao"
            w.length > 4 && w.endsWith("res") -> w.dropLast(2)
            w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
            else -> w
        }

        /** The words of a name, without accents, plural, small words and abbreviations, in alphabetical order. */
        fun key(name: String): String =
            normalize(name).split(' ')
                .filter { it.isNotEmpty() && it !in STOP }
                .map { SAME[it] ?: it }
                .map { SAME[singular(it)] ?: singular(it) }
                .distinct()
                .sorted()
                .joinToString(" ")

        private fun trigrams(s: String): Set<String> {
            val t = " $s "
            return if (t.length < 3) setOf(t) else (0..t.length - 3).map { t.substring(it, it + 3) }.toSet()
        }

        /** Half shared words (Jaccard), half shared letter triples (Dice) of two keys: 1 is identical. */
        fun similarity(keyA: String, keyB: String): Double {
            if (keyA.isEmpty() || keyB.isEmpty()) return 0.0
            if (keyA == keyB) return 1.0
            val wa = keyA.split(' ').toSet()
            val wb = keyB.split(' ').toSet()
            val words = (wa intersect wb).size.toDouble() / (wa union wb).size
            val ta = trigrams(keyA)
            val tb = trigrams(keyB)
            val letters = 2.0 * (ta intersect tb).size / (ta.size + tb.size)
            return 0.5 * words + 0.5 * letters
        }

        fun newId(name: String): String = "custom-" + normalize(name).replace(' ', '-')

        /** The app's own list, assets/exercises.json. */
        fun parse(json: String): List<CatalogEntry> =
            Json.parseToJsonElement(json).jsonObject.getValue("exercises").jsonArray.map {
                val o = it.jsonObject
                CatalogEntry(
                    id = o.getValue("id").jsonPrimitive.content,
                    canonicalName = o.getValue("canonical_name").jsonPrimitive.content,
                    aliases = o.getValue("aliases").jsonArray.map { a -> a.jsonPrimitive.content },
                )
            }

        /** The full catalog with muscles, assets/exercises_catalog.json (tools/exercises/build_catalog.py). */
        fun parseCatalog(json: String): List<CatalogEntry> =
            Json.parseToJsonElement(json).jsonObject.getValue("exercises").jsonArray.map {
                val o = it.jsonObject
                fun list(k: String) = o[k]?.jsonArray?.map { a -> a.jsonPrimitive.content }.orEmpty()
                CatalogEntry(
                    id = o.str("id")!!,
                    canonicalName = o.str("name_pt")!!,
                    aliases = list("aliases"),
                    nameEn = o.str("name_en"),
                    primary = list("primary"),
                    secondary = list("secondary"),
                    equipment = o.str("equipment"),
                    kind = o.str("kind") ?: "força",
                    autoTranslated = o["auto_translated"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }

        private fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull
    }
}
