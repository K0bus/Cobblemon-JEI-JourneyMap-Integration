import { ref, computed, reactive } from 'vue'
import type { PokemonSummary, SpeciesDetail } from '../types'

const pokemonList = ref<PokemonSummary[]>([])
const isLoading = ref<boolean>(false)
const error = ref<string | null>(null)

const searchQuery = ref<string>('')
const selectedTypes = ref<string[]>([])
const selectedGeneration = ref<number | null>(null)
const selectedStatus = ref<'ALL' | 'CAUGHT' | 'ENCOUNTERED' | 'MISSING'>('ALL')
const sortBy = ref<'DEX' | 'NAME' | 'OWNED'>('DEX')

const selectedSpeciesDetail = ref<SpeciesDetail | null>(null)
const isDetailOpen = ref<boolean>(false)
const isLoadingDetail = ref<boolean>(false)

export function usePokedexStore() {
  async function fetchPokedex(silent = false) {
    if (!silent || pokemonList.value.length === 0) {
      isLoading.value = true
    }
    error.value = null
    try {
      const res = await fetch('/api/pokedex')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      pokemonList.value = await res.json()
    } catch (e: any) {
      error.value = e.message || 'Erreur chargement Pokédex'
    } finally {
      isLoading.value = false
    }
  }

  async function openDetail(identifier: string) {
    isLoadingDetail.value = true
    isDetailOpen.value = true
    try {
      const res = await fetch(`/api/species/${encodeURIComponent(identifier)}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      selectedSpeciesDetail.value = await res.json()
    } catch (e: any) {
      console.error('Erreur chargement détail:', e)
    } finally {
      isLoadingDetail.value = false
    }
  }

  function closeDetail() {
    isDetailOpen.value = false
    selectedSpeciesDetail.value = null
  }

  function toggleTypeFilter(type: string) {
    const idx = selectedTypes.value.indexOf(type)
    if (idx >= 0) {
      selectedTypes.value.splice(idx, 1)
    } else {
      selectedTypes.value.push(type)
    }
  }

  function clearFilters() {
    searchQuery.value = ''
    selectedTypes.value = []
    selectedGeneration.value = null
    selectedStatus.value = 'ALL'
    sortBy.value = 'DEX'
  }

  const filteredPokemon = computed(() => {
    let result = pokemonList.value

    // Recherche texte
    const q = searchQuery.value.trim().toLowerCase()
    if (q) {
      result = result.filter(p => 
        p.displayName.toLowerCase().includes(q) ||
        p.name.toLowerCase().includes(q) ||
        p.nationalDexNumber.toString().includes(q)
      )
    }

    // Filtre Statut
    if (selectedStatus.value !== 'ALL') {
      result = result.filter(p => p.status === selectedStatus.value)
    }

    // Filtre Génération
    if (selectedGeneration.value !== null) {
      result = result.filter(p => p.generation === selectedGeneration.value)
    }

    // Filtre Types
    if (selectedTypes.value.length > 0) {
      result = result.filter(p => {
        const prim = p.primaryType.toLowerCase()
        const sec = p.secondaryType?.toLowerCase()
        return selectedTypes.value.some(t => t.toLowerCase() === prim || t.toLowerCase() === sec)
      })
    }

    // Tri
    result = [...result].sort((a, b) => {
      if (sortBy.value === 'NAME') {
        return a.displayName.localeCompare(b.displayName)
      } else if (sortBy.value === 'OWNED') {
        return b.ownedCount - a.ownedCount || a.nationalDexNumber - b.nationalDexNumber
      }
      return a.nationalDexNumber - b.nationalDexNumber
    })

    return result
  })

  return reactive({
    pokemonList,
    isLoading,
    error,
    searchQuery,
    selectedTypes,
    selectedGeneration,
    selectedStatus,
    sortBy,
    selectedSpeciesDetail,
    isDetailOpen,
    isLoadingDetail,
    filteredPokemon,
    fetchPokedex,
    openDetail,
    closeDetail,
    toggleTypeFilter,
    clearFilters
  })
}
