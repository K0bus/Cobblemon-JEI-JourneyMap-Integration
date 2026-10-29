import { ref, onMounted, onUnmounted } from 'vue'
import type { PlayerStatus, ToastMessage } from '../types'
import { usePokedexStore } from '../stores/pokedexStore'
import { useEvolutionStore } from '../stores/evolutionStore'
import { usePartyPcStore } from '../stores/partyPcStore'
import { useRadarStore } from '../stores/radarStore'

export type ConnectionStatus = 'CONNECTED' | 'CONNECTING' | 'DISCONNECTED'

const connectionStatus = ref<ConnectionStatus>('CONNECTING')
const playerStatus = ref<PlayerStatus | null>(null)
const toasts = ref<ToastMessage[]>([])

let eventSource: EventSource | null = null
let statusPollInterval: any = null

export function useMinecraftLive() {
  const pokedexStore = usePokedexStore()
  const evolutionStore = useEvolutionStore()
  const partyPcStore = usePartyPcStore()
  const radarStore = useRadarStore()

  function addToast(title: string, subtitle: string, type: 'capture' | 'evolution' | 'info' = 'info') {
    const id = Math.random().toString(36).substring(2, 9)
    toasts.value.unshift({
      id,
      title,
      subtitle,
      type,
      timestamp: Date.now()
    })
    setTimeout(() => {
      removeToast(id)
    }, 6000)
  }

  function removeToast(id: string) {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }

  async function fetchStatus() {
    try {
      const res = await fetch('/api/status')
      if (res.ok) {
        const prevData = playerStatus.value
        const newData = await res.json()
        playerStatus.value = newData
        if (connectionStatus.value !== 'CONNECTED') {
          connectionStatus.value = 'CONNECTED'
        }

        // Si le nombre de Pokémon capturés ou rencontrés a changé, forcer le rafraîchissement
        if (prevData && (newData.pokedexCaught !== prevData.pokedexCaught || newData.pokedexEncountered !== prevData.pokedexEncountered)) {
          pokedexStore.fetchPokedex()
          evolutionStore.fetchAllEvolutions()
          partyPcStore.fetchAll()
        }
      }
    } catch {
      // Inactif ou déconnecté
    }
  }

  function connectSse() {
    if (eventSource) {
      eventSource.close()
    }

    connectionStatus.value = 'CONNECTING'
    eventSource = new EventSource('/api/events')

    eventSource.onopen = () => {
      connectionStatus.value = 'CONNECTED'
    }

    eventSource.onerror = () => {
      connectionStatus.value = 'DISCONNECTED'
    }

    eventSource.addEventListener('pokemon_captured', (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data)
        addToast(
          `🎉 Capturé !`,
          `${data.speciesName || 'Nouveau Pokémon'} a été enregistré au Pokédex !`,
          'capture'
        )
        pokedexStore.fetchPokedex()
        evolutionStore.fetchAllEvolutions()
        partyPcStore.fetchAll()
        fetchStatus()
      } catch (err) {
        console.error('Erreur event capture:', err)
      }
    })

    eventSource.addEventListener('evolution_complete', (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data)
        addToast(
          `✨ Évolution réussie !`,
          `${data.speciesName || 'Votre Pokémon'} a évolué !`,
          'evolution'
        )
        evolutionStore.fetchAllEvolutions()
        pokedexStore.fetchPokedex()
        partyPcStore.fetchAll()
        fetchStatus()
      } catch (err) {
        console.error('Erreur event evolution:', err)
      }
    })

    eventSource.addEventListener('pokedex_updated', (e: MessageEvent) => {
      pokedexStore.fetchPokedex()
      evolutionStore.fetchAllEvolutions()
      partyPcStore.fetchParty()
      fetchStatus()
    })

    eventSource.addEventListener('party_updated', () => {
      partyPcStore.fetchParty()
    })

    eventSource.addEventListener('pc_updated', () => {
      partyPcStore.fetchPcBoxes()
    })

    eventSource.addEventListener('radar_updated', () => {
      radarStore.fetchRadar()
    })
  }

  function startLiveSync() {
    connectSse()
    fetchStatus()
    statusPollInterval = setInterval(fetchStatus, 5000)
  }

  function stopLiveSync() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    if (statusPollInterval) {
      clearInterval(statusPollInterval)
      statusPollInterval = null
    }
  }

  return {
    connectionStatus,
    playerStatus,
    toasts,
    addToast,
    removeToast,
    fetchStatus,
    startLiveSync,
    stopLiveSync
  }
}
