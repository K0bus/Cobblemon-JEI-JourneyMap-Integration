import { ref, computed, reactive } from 'vue'
import type { OwnedPokemon, PcBox } from '../types'

const party = ref<(OwnedPokemon | null)[]>([null, null, null, null, null, null])
const pcBoxes = ref<PcBox[]>([])
const selectedBoxIndex = ref<number>(0)
const inspectedPokemon = ref<OwnedPokemon | null>(null)
const activeMode = ref<'PARTY' | 'PC'>('PARTY')
const isLoading = ref<boolean>(false)

export function usePartyPcStore() {
  async function fetchParty() {
    try {
      const res = await fetch('/api/party')
      if (res.ok) {
        party.value = await res.json()
      }
    } catch (e) {
      console.error('Erreur lecture équipe:', e)
    }
  }

  async function fetchPcBoxes() {
    try {
      const res = await fetch('/api/pc')
      if (res.ok) {
        pcBoxes.value = await res.json()
      }
    } catch (e) {
      console.error('Erreur lecture PC:', e)
    }
  }

  async function fetchAll(silent = false) {
    if (!silent || pcBoxes.value.length === 0) {
      isLoading.value = true
    }
    try {
      await Promise.all([fetchParty(), fetchPcBoxes()])
    } finally {
      isLoading.value = false
    }
  }

  const currentBox = computed(() => {
    return pcBoxes.value.find(b => b.boxIndex === selectedBoxIndex.value) || pcBoxes.value[0] || null
  })

  function inspectPokemon(pokemon: OwnedPokemon | null) {
    inspectedPokemon.value = pokemon
  }

  function closeInspect() {
    inspectedPokemon.value = null
  }

  return reactive({
    party,
    pcBoxes,
    selectedBoxIndex,
    inspectedPokemon,
    activeMode,
    isLoading,
    currentBox,
    fetchParty,
    fetchPcBoxes,
    fetchAll,
    inspectPokemon,
    closeInspect
  })
}
