import { ref, reactive } from 'vue'
import type { RadarEntity } from '../types'

const entities = ref<RadarEntity[]>([])
const isLoading = ref<boolean>(false)

export function useRadarStore() {
  async function fetchRadar() {
    try {
      const res = await fetch('/api/radar')
      if (res.ok) {
        entities.value = await res.json()
      }
    } catch (e) {
      console.error('Erreur lecture radar:', e)
    }
  }

  async function trackSpecies(speciesName: string) {
    try {
      const res = await fetch('/api/action/track', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ species: speciesName })
      })
      return res.ok
    } catch (e) {
      console.error('Erreur déclenchement tracking:', e)
      return false
    }
  }

  return reactive({
    entities,
    isLoading,
    fetchRadar,
    trackSpecies
  })
}
