/**
 * Utilitaire de résolution des sprites de Pokémon (Showdown HD animé, PokeAPI, Official Artwork).
 */
export function usePokemonSprites() {
  const POKEBALL_FALLBACK = "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><circle cx='50' cy='50' r='45' fill='%23ef4444' stroke='%23ffffff' stroke-width='6'/><path d='M5,50 H95 A45,45 0 0,1 5,50' fill='%23ffffff'/><circle cx='50' cy='50' r='14' fill='%231e293b' stroke='%23ffffff' stroke-width='4'/><circle cx='50' cy='50' r='6' fill='%23ffffff'/></svg>"

  /**
   * Normalise le nom de l'espèce pour les URLs Showdown (supprime tirets, parenthèses, accents et espaces superflus).
   */
  function getShowdownId(name: string): string {
    if (!name) return ''
    // Si c'est un surnom avec parenthèses "MonPika (Pikachu)", extraire le nom entre parenthèses
    const match = name.match(/\(([^)]+)\)/)
    const base = match ? match[1] : name

    return base.toLowerCase()
      .normalize('NFD').replace(/[\u0300-\u036f]/g, '') // Supprime accents
      .replace(/[\s\-_]/g, '')
      .replace('♀', 'f')
      .replace('♂', 'm')
      .replace(/[.:'’]/g, '')
  }

  /**
   * Retourne l'URL du sprite animé (GIF Showdown).
   */
  function getSpriteUrl(name: string, dexNumber = 0, isShiny = false): string {
    const clean = getShowdownId(name)
    if (!clean && dexNumber > 0) {
      return getFallbackSpriteUrl(dexNumber, isShiny)
    }
    if (isShiny) {
      return `https://play.pokemonshowdown.com/sprites/ani-shiny/${clean}.gif`
    }
    return `https://play.pokemonshowdown.com/sprites/ani/${clean}.gif`
  }

  /**
   * Sprite de secours statique haute fiabilité (PokeAPI).
   */
  function getFallbackSpriteUrl(dexNumber: number, isShiny = false): string {
    if (!dexNumber || dexNumber <= 0) {
      return POKEBALL_FALLBACK
    }
    if (isShiny) {
      return `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/shiny/${dexNumber}.png`
    }
    return `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${dexNumber}.png`
  }

  /**
   * Artwork officiel HD pour la fiche détaillée.
   */
  function getArtworkUrl(dexNumber: number, isShiny = false): string {
    if (!dexNumber || dexNumber <= 0) {
      return POKEBALL_FALLBACK
    }
    if (isShiny) {
      return `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/shiny/${dexNumber}.png`
    }
    return `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/${dexNumber}.png`
  }

  /**
   * Gestionnaire d'erreur universel pour basculer sur PokeAPI puis Pokéball SVG.
   */
  function handleImgError(event: Event, dexNumber: number, isShiny = false) {
    const img = event.target as HTMLImageElement
    if (!img) return
    const fallbackUrl = getFallbackSpriteUrl(dexNumber, isShiny)
    if (img.src !== fallbackUrl && fallbackUrl !== POKEBALL_FALLBACK) {
      img.src = fallbackUrl
    } else {
      img.src = POKEBALL_FALLBACK
    }
  }

  return {
    getShowdownId,
    getSpriteUrl,
    getFallbackSpriteUrl,
    getArtworkUrl,
    handleImgError,
    POKEBALL_FALLBACK
  }
}
