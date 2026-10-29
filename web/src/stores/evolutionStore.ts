import { ref, reactive } from 'vue'
import type { EvolutionGoal, BreedingOrigin, FossilGoal, LegendaryGoal } from '../types'

export type EvolutionSubTab = 'PENDING' | 'ITEMS' | 'ORIGINS' | 'FOSSILS' | 'LEGENDARIES'

const activeSubTab = ref<EvolutionSubTab>('PENDING')
const pendingGoals = ref<EvolutionGoal[]>([])
const itemGoals = ref<EvolutionGoal[]>([])
const originGoals = ref<BreedingOrigin[]>([])
const fossilGoals = ref<FossilGoal[]>([])
const legendaryGoals = ref<LegendaryGoal[]>([])

const isLoading = ref<boolean>(false)
const onlyPcFeasible = ref<boolean>(false)

export function useEvolutionStore() {
  async function fetchAllEvolutions() {
    isLoading.value = true
    try {
      const [pendingRes, itemsRes, originsRes, fossilsRes, legRes] = await Promise.all([
        fetch('/api/evolutions/pending').then(r => r.json()).catch(() => []),
        fetch('/api/evolutions/items').then(r => r.json()).catch(() => []),
        fetch('/api/evolutions/origins').then(r => r.json()).catch(() => []),
        fetch('/api/evolutions/fossils').then(r => r.json()).catch(() => []),
        fetch('/api/evolutions/legendaries').then(r => r.json()).catch(() => [])
      ])
      pendingGoals.value = pendingRes
      itemGoals.value = itemsRes
      originGoals.value = originsRes
      fossilGoals.value = fossilsRes
      legendaryGoals.value = legRes
    } catch (e) {
      console.error('Erreur chargement évolutions:', e)
    } finally {
      isLoading.value = false
    }
  }

  return reactive({
    activeSubTab,
    pendingGoals,
    itemGoals,
    originGoals,
    fossilGoals,
    legendaryGoals,
    isLoading,
    onlyPcFeasible,
    fetchAllEvolutions
  })
}
