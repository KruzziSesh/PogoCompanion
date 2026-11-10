package com.kruzzi.pogocompanion

import kotlin.math.floor
import kotlin.math.sqrt

data class SpeciesStats(val name: String, val attack: Int, val defense: Int, val stamina: Int)
data class EncounterAnalysis(val species: String, val cp: Int, val candidates: Int, val perfect: Int, val minPercent: Int, val maxPercent: Int, val detail: String)

object BattleBrain {
    // Pokémon GO base stats. Extendable database; only recognized entries are analyzed.
    private val species = listOf(
        SpeciesStats("Bulbasaur",118,111,128), SpeciesStats("Ivysaur",151,143,155),
        SpeciesStats("Venusaur",198,189,190), SpeciesStats("Charmander",116,93,118),
        SpeciesStats("Charmeleon",158,126,151), SpeciesStats("Charizard",223,173,186),
        SpeciesStats("Squirtle",94,121,127), SpeciesStats("Wartortle",126,155,153),
        SpeciesStats("Blastoise",171,207,188), SpeciesStats("Pikachu",112,96,111),
        SpeciesStats("Raichu",193,151,155), SpeciesStats("Eevee",104,114,146),
        SpeciesStats("Vaporeon",205,161,277), SpeciesStats("Jolteon",232,182,163),
        SpeciesStats("Flareon",246,179,163), SpeciesStats("Snorlax",190,169,330),
        SpeciesStats("Dragonite",263,198,209), SpeciesStats("Dratini",119,91,121),
        SpeciesStats("Dragonair",163,135,156), SpeciesStats("Magikarp",29,85,85),
        SpeciesStats("Gyarados",237,186,216), SpeciesStats("Machop",137,82,172),
        SpeciesStats("Machoke",177,125,190), SpeciesStats("Machamp",234,159,207),
        SpeciesStats("Gastly",186,67,102), SpeciesStats("Haunter",223,107,128),
        SpeciesStats("Gengar",261,149,155), SpeciesStats("Ralts",79,59,99),
        SpeciesStats("Kirlia",117,90,116), SpeciesStats("Gardevoir",237,195,169),
        SpeciesStats("Beldum",96,132,120), SpeciesStats("Metang",138,176,155),
        SpeciesStats("Metagross",257,228,190), SpeciesStats("Gible",124,84,151),
        SpeciesStats("Gabite",172,125,169), SpeciesStats("Garchomp",261,193,239),
        SpeciesStats("Swablu",76,132,128), SpeciesStats("Altaria",141,201,181),
        SpeciesStats("Marill",37,93,172), SpeciesStats("Azumarill",112,152,225),
        SpeciesStats("Skarmory",148,226,163), SpeciesStats("Clodsire",127,151,277),
        SpeciesStats("Dunsparce",131,128,225), SpeciesStats("Feraligatr",205,188,198),
        SpeciesStats("Totodile",117,109,137), SpeciesStats("Croconaw",150,142,163),
        SpeciesStats("Wooper",75,66,146), SpeciesStats("Quagsire",152,143,216),
        SpeciesStats("Mudkip",126,93,137), SpeciesStats("Marshtomp",156,133,172),
        SpeciesStats("Swampert",208,175,225), SpeciesStats("Chikorita",92,122,128),
        SpeciesStats("Bayleef",122,155,155), SpeciesStats("Meganium",168,202,190),
        SpeciesStats("Cyndaquil",116,93,118), SpeciesStats("Quilava",158,126,151),
        SpeciesStats("Typhlosion",223,173,186), SpeciesStats("Pidgey",85,73,120),
        SpeciesStats("Pidgeotto",117,105,160), SpeciesStats("Pidgeot",166,154,195),
        SpeciesStats("Mankey",148,82,120), SpeciesStats("Primeape",207,138,163),
        SpeciesStats("Annihilape",220,178,242), SpeciesStats("Froakie",122,84,121),
        SpeciesStats("Frogadier",168,114,144), SpeciesStats("Greninja",223,152,176),
        SpeciesStats("Fletchling",95,80,128), SpeciesStats("Fletchinder",145,110,158),
        SpeciesStats("Talonflame",176,155,186), SpeciesStats("Sableye",141,136,137),
        SpeciesStats("Umbreon",126,240,216), SpeciesStats("Espeon",261,175,163),
        SpeciesStats("Lanturn",146,137,268), SpeciesStats("Chinchou",106,97,181),
        SpeciesStats("Trevenant",201,154,198), SpeciesStats("Phantump",125,103,125),
        SpeciesStats("Noctowl",145,156,225), SpeciesStats("Hoothoot",67,88,155),
        SpeciesStats("Stunfisk",144,171,240), SpeciesStats("Bastiodon",94,286,155),
        SpeciesStats("Shieldon",76,195,102), SpeciesStats("Mareanie",98,110,137),
        SpeciesStats("Toxapex",114,273,137), SpeciesStats("Carbink",95,285,137)
    )
    private val cpMultipliers = doubleArrayOf(
        0.094,0.135137432,0.16639787,0.192650919,0.21573247,0.236572661,
        0.25572005,0.273530381,0.29024988,0.306057377,0.3210876,0.335445036,
        0.34921268,0.362457751,0.37523559,0.387592406,0.39956728,0.411193551,
        0.42250001,0.432926419,0.44310755,0.4530599578,0.46279839,0.472336083,
        0.48168495,0.4908558,0.49985844,0.508701765,0.51739395,0.525942511,
        0.53435433,0.542635767,0.55079269,0.558830576,0.56675452,0.574569153,
        0.58227891,0.589887917,0.59740001,0.604818814,0.61215729,0.619404122,
        0.62656713,0.633649143,0.64065295,0.647580966,0.65443563,0.661219252,
        0.667934,0.674581896,0.68116492,0.687684904,0.69414365,0.70054287,
        0.70688421,0.713169109,0.71939909,0.725575614,0.7317,0.734741009,
        0.73776948,0.740785574,0.74378943,0.746781211,0.74976104,0.752729087,
        0.75568551,0.758630378,0.76156384,0.764486065,0.76739717,0.770297266,
        0.7731865,0.776064962,0.77893275,0.781790055,0.78463697,0.787473578,
        0.79030001
    )
    private val names = species.sortedByDescending { it.name.length }
    fun analyze(text: String): EncounterAnalysis? {
        val cp = Regex("""(?i)\bCP\s*[: ]?\s*([0-9]{1,5})\b""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val name = names.firstOrNull { Regex("""(?i)(?<![A-Za-z])${Regex.escape(it.name)}(?![A-Za-z])""").containsMatchIn(text) } ?: return null
        var count = 0; var perfect = 0; var min = 100; var max = 0
        // Wild encounter range 1–35; level unknown. Weather boost and special encounter floors are not assumed.
        for (idx in 0..68) {
            val mult = cpMultipliers[idx]
            for (a in 0..15) for (d in 0..15) for (s in 0..15) {
                val calculated = floor((name.attack+a) * sqrt((name.defense+d).toDouble()) *
                    sqrt((name.stamina+s).toDouble()) * mult * mult / 10.0).toInt().coerceAtLeast(10)
                if (calculated == cp) {
                    count++
                    val total = a+d+s
                    if (total == 45) perfect++
                    val percent = (total*100.0/45).toInt()
                    min = min.coerceAtMost(percent)
                    max = max.coerceAtLeast(percent)
                }
            }
        }
        return EncounterAnalysis(name.name, cp, count, perfect, min, max,
            when {
                count == 0 -> "Δεν βρέθηκε συνδυασμός για wild level 1–35. Ίσως είναι raid/research, boosted level ή λάθος OCR."
                count == 1 && perfect == 1 -> "ΒΕΒΑΙΟ 100% IV (με βάση το τυπικό CP μοντέλο)"
                perfect > 0 -> "100% IV είναι ΔΥΝΑΤΟ, όχι βέβαιο · $perfect / $count συνδυασμοί"
                else -> "100% IV αποκλείεται για τα επίπεδα 1–35"
            })
    }
    fun pvpAdvice(text: String): String {
        val t = text.lowercase()
        return when {
            "attack incoming" in t || "incoming attack" in t || "επίθεση" in t && "έρχεται" in t ->
                "ΕΡΧΕΤΑΙ CHARGED ATTACK · Shield μόνο αν χρειάζεσαι το Pokémon για το endgame ή απειλείται από KO."
            "super effective" in t || "πολύ αποτελεσματ" in t ->
                "SUPER EFFECTIVE · Κράτα την πίεση. Πρόσεξε αν το μήνυμα αφορά επίθεση του αντιπάλου."
            "not very effective" in t || "όχι πολύ αποτελεσματ" in t ->
                "ΧΑΜΗΛΗ ΑΠΟΤΕΛΕΣΜΑΤΙΚΟΤΗΤΑ · Εξέτασε αλλαγή σε καλύτερο matchup, αν έχεις διαθέσιμο switch."
            "get ready" in t || "ετοιμάσου" in t ->
                "CHARGED MOVE · Ολοκλήρωσε τα bubbles για τη μέγιστη διαθέσιμη ζημιά."
            "battle" in t || "μάχη" in t || "great league" in t ->
                "PvP MODE · Μέτρα τα fast moves του αντιπάλου. Κράτα shields για το win condition σου."
            else -> "PvP MODE · Αναμονή για αναγνωρίσιμα στοιχεία μάχης. Δεν εκτιμώ ενέργεια/shields χωρίς αξιόπιστα δεδομένα."
        }
    }
}
