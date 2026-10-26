package fr.k0bus.cobblemon_jei.domain.model

/**
 * Niveau de confiance et de complétude des données d'apparition d'un Pokémon.
 */
enum class SpawnDataConfidence {
    /** Données officielles complètes issues des fichiers Cobblemon ou de ses APIs */
    EXACT_DATA,

    /** Données partielles (ex: conditions complexes ignorées ou partiellement supportées) */
    PARTIALLY_SUPPORTED,

    /** Données extraites par scanner de fichiers en mode repli */
    FALLBACK_SCANNED,

    /** Données synthétisées ou statut non confirmé */
    UNKNOWN
}
