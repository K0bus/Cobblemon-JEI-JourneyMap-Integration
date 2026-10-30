<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useEvolutionStore, type EvolutionSubTab } from '../../stores/evolutionStore'
import { usePokedexStore } from '../../stores/pokedexStore'
import { usePokemonSprites } from '../../composables/usePokemonSprites'
import type { EvolutionGoal, FossilGoal, BreedingOrigin, LegendaryGoal } from '../../types'

const evoStore = useEvolutionStore()
const pokedexStore = usePokedexStore()
const { getSpriteUrl, getFallbackSpriteUrl, handleImgError } = usePokemonSprites()

// Filtres globaux
const searchQuery = ref('')
const statusFilter = ref<'ALL' | 'CAUGHT' | 'MISSING'>('MISSING') // Manquants par défaut !

// Sous-filtres contextuels
const pendingFilter = ref<'ALL' | 'READY' | 'PC'>('ALL')
export type EvolutionMethodFilter = 
  | 'ALL'
  | 'LEVEL_ONLY'
  | 'ITEM'
  | 'TRADE'
  | 'MECHANIC'
  | 'FRIENDSHIP'
  | 'MOVE'
  | 'WEATHER_TIME'
  | 'ACTION'

const methodFilter = ref<EvolutionMethodFilter>('ALL')
const itemInventoryFilter = ref<'ALL' | 'OWNED' | 'MISSING'>('ALL')
const originFilter = ref<'ALL' | 'READY'>('ALL')
const fossilFilter = ref<'ALL' | 'READY'>('ALL')
const legendaryFilter = ref<'ALL' | 'MONUMENT'>('ALL')

// Fonctions d'identification du type / de la méthode d'évolution
function isTradeEvolution(g: EvolutionGoal): boolean {
  if (g.methods && g.methods.length > 0) return g.methods.includes('TRADE')
  const req = (g.requirementsSummary || '').toLowerCase()
  const variant = (g.evolutionVariant || '').toLowerCase()
  const conds = (g.additionalConditions || []).map(c => c.toLowerCase())
  return variant.includes('échange') || req.includes('échange') || conds.some(c => c.includes('échange')) ||
    g.requiredItems.some(it => it.toLowerCase().includes('câble de liaison'))
}

function isItemEvolution(g: EvolutionGoal): boolean {
  if (g.methods && g.methods.length > 0) return g.methods.includes('ITEM')
  const variant = (g.evolutionVariant || '').toLowerCase()
  const conds = (g.additionalConditions || []).map(c => c.toLowerCase())
  const hasNonCableItem = g.requiredItems.some(it => !it.toLowerCase().includes('câble de liaison'))
  return variant.includes('objet') || hasNonCableItem ||
    conds.some(c => c.includes('objet tenu') || c.includes('pierre'))
}

function isFriendshipEvolution(g: EvolutionGoal): boolean {
  if (g.methods && g.methods.length > 0) return g.methods.includes('FRIENDSHIP')
  const conds = (g.additionalConditions || []).map(c => c.toLowerCase())
  return (g.minFriendship !== undefined && g.minFriendship !== null && g.minFriendship > 0) ||
    conds.some(c => c.includes('bonheur')) ||
    (g.requirementsSummary || '').toLowerCase().includes('bonheur')
}

function isMoveEvolution(g: EvolutionGoal): boolean {
  if (g.methods && g.methods.length > 0) return g.methods.includes('MOVE')
  const conds = (g.additionalConditions || []).map(c => c.toLowerCase())
  return Boolean(g.requiredMove || g.requiredMoveType) ||
    conds.some(c => c.includes('capacité') || c.includes('utiliser'))
}

function isWeatherOrTimeEvolution(g: EvolutionGoal): boolean {
  if (g.methods && g.methods.length > 0) return g.methods.includes('WEATHER_TIME')
  const conds = (g.additionalConditions || []).map(c => c.toLowerCase())
  return conds.some(c =>
    c.includes('période') || c.includes('jour') || c.includes('nuit') || c.includes('crépuscule') ||
    c.includes('météo') || c.includes('pluie') || c.includes('orage') || c.includes('lune') ||
    c.includes('biome') || c.includes('lieu') || c.includes('structure')
  )
}

function isActionEvolution(g: EvolutionGoal): boolean {
  if (g.methods && g.methods.length > 0) return g.methods.includes('ACTION')
  const conds = (g.additionalConditions || []).map(c => c.toLowerCase())
  return Boolean(g.specialActionSummary) ||
    conds.some(c =>
      c.includes('marcher') || c.includes('critique') || c.includes('dégât') ||
      c.includes('vaincre') || c.includes('recul')
    )
}

function isSpecialEvolution(g: EvolutionGoal): boolean {
  if (g.methods && g.methods.length > 0) return g.methods.includes('SPECIAL')
  const conds = (g.additionalConditions || []).map(c => c.toLowerCase())
  return Boolean(g.requiredGender) ||
    conds.some(c =>
      c.includes('sexe') || c.includes('nature') || c.includes('pièces') ||
      c.includes('équipe') || c.includes('statistique')
    )
}

function isMechanicEvolution(g: EvolutionGoal): boolean {
  if (g.methods && g.methods.length > 0) return g.methods.includes('MECHANIC')
  return isFriendshipEvolution(g) || isMoveEvolution(g) || isWeatherOrTimeEvolution(g) ||
    isActionEvolution(g) || isSpecialEvolution(g)
}

function isLevelOnlyEvolution(g: EvolutionGoal): boolean {
  if (g.methods && g.methods.length > 0) return g.methods.includes('LEVEL_ONLY')
  return !isTradeEvolution(g) && !isItemEvolution(g) && !isMechanicEvolution(g)
}

onMounted(() => {
  evoStore.fetchAllEvolutions()
})

// Compteurs globaux pour les pilules du statut Pokédex
const currentTabTotalCount = computed(() => {
  switch (evoStore.activeSubTab) {
    case 'PENDING': return evoStore.pendingGoals.length
    case 'ITEMS': return evoStore.itemGoals.length
    case 'ORIGINS': return evoStore.originGoals.length
    case 'FOSSILS': return evoStore.fossilGoals.length
    case 'LEGENDARIES': return evoStore.legendaryGoals.length
    default: return 0
  }
})

const currentTabCaughtCount = computed(() => {
  switch (evoStore.activeSubTab) {
    case 'PENDING': return evoStore.pendingGoals.filter(g => g.targetStatus === 'CAUGHT').length
    case 'ITEMS': return evoStore.itemGoals.filter(g => g.targetStatus === 'CAUGHT').length
    case 'ORIGINS': return evoStore.originGoals.filter(g => g.targetStatus === 'CAUGHT').length
    case 'FOSSILS': return evoStore.fossilGoals.filter(f => f.isCaught).length
    case 'LEGENDARIES': return evoStore.legendaryGoals.filter(l => l.status === 'CAUGHT').length
    default: return 0
  }
})

const currentTabMissingCount = computed(() => {
  switch (evoStore.activeSubTab) {
    case 'PENDING': return evoStore.pendingGoals.filter(g => g.targetStatus !== 'CAUGHT').length
    case 'ITEMS': return evoStore.itemGoals.filter(g => g.targetStatus !== 'CAUGHT').length
    case 'ORIGINS': return evoStore.originGoals.filter(g => g.targetStatus !== 'CAUGHT').length
    case 'FOSSILS': return evoStore.fossilGoals.filter(f => !f.isCaught).length
    case 'LEGENDARIES': return evoStore.legendaryGoals.filter(l => l.status !== 'CAUGHT').length
    default: return 0
  }
})

// Fonctions de comptage pour sous-filtres avec prise en compte du statut cible
function countPendingByFeasibility(feas: 'ALL' | 'READY' | 'PC') {
  let list = evoStore.pendingGoals as EvolutionGoal[]
  if (statusFilter.value === 'CAUGHT') list = list.filter(g => g.targetStatus === 'CAUGHT')
  else if (statusFilter.value === 'MISSING') list = list.filter(g => g.targetStatus !== 'CAUGHT')

  if (methodFilter.value === 'LEVEL_ONLY') list = list.filter(g => isLevelOnlyEvolution(g))
  else if (methodFilter.value === 'ITEM') list = list.filter(g => isItemEvolution(g))
  else if (methodFilter.value === 'TRADE') list = list.filter(g => isTradeEvolution(g))
  else if (methodFilter.value === 'MECHANIC') list = list.filter(g => isMechanicEvolution(g))
  else if (methodFilter.value === 'FRIENDSHIP') list = list.filter(g => isFriendshipEvolution(g))
  else if (methodFilter.value === 'MOVE') list = list.filter(g => isMoveEvolution(g))
  else if (methodFilter.value === 'WEATHER_TIME') list = list.filter(g => isWeatherOrTimeEvolution(g))
  else if (methodFilter.value === 'ACTION') list = list.filter(g => isActionEvolution(g))

  if (feas === 'READY') return list.filter(g => g.feasibility === 'READY_TO_EVOLVE').length
  if (feas === 'PC') return list.filter(g => g.feasibility === 'CAN_EVOLVE_WITH_PC').length
  return list.length
}

function countPendingByMethod(method: EvolutionMethodFilter) {
  let list = evoStore.pendingGoals as EvolutionGoal[]
  if (statusFilter.value === 'CAUGHT') list = list.filter(g => g.targetStatus === 'CAUGHT')
  else if (statusFilter.value === 'MISSING') list = list.filter(g => g.targetStatus !== 'CAUGHT')

  if (pendingFilter.value === 'READY') list = list.filter(g => g.feasibility === 'READY_TO_EVOLVE')
  else if (pendingFilter.value === 'PC') list = list.filter(g => g.feasibility === 'CAN_EVOLVE_WITH_PC')

  if (method === 'LEVEL_ONLY') return list.filter(g => isLevelOnlyEvolution(g)).length
  if (method === 'ITEM') return list.filter(g => isItemEvolution(g)).length
  if (method === 'TRADE') return list.filter(g => isTradeEvolution(g)).length
  if (method === 'MECHANIC') return list.filter(g => isMechanicEvolution(g)).length
  if (method === 'FRIENDSHIP') return list.filter(g => isFriendshipEvolution(g)).length
  if (method === 'MOVE') return list.filter(g => isMoveEvolution(g)).length
  if (method === 'WEATHER_TIME') return list.filter(g => isWeatherOrTimeEvolution(g)).length
  if (method === 'ACTION') return list.filter(g => isActionEvolution(g)).length
  return list.length
}

function countItemByInventory(inv: 'ALL' | 'OWNED' | 'MISSING') {
  let list = evoStore.itemGoals as EvolutionGoal[]
  if (statusFilter.value === 'CAUGHT') list = list.filter(g => g.targetStatus === 'CAUGHT')
  else if (statusFilter.value === 'MISSING') list = list.filter(g => g.targetStatus !== 'CAUGHT')

  if (inv === 'OWNED') return list.filter(g => g.isItemOwned).length
  if (inv === 'MISSING') return list.filter(g => !g.isItemOwned).length
  return list.length
}

function countOriginByBreeding(b: 'ALL' | 'READY') {
  let list = evoStore.originGoals as BreedingOrigin[]
  if (statusFilter.value === 'CAUGHT') list = list.filter(o => o.targetStatus === 'CAUGHT')
  else if (statusFilter.value === 'MISSING') list = list.filter(o => o.targetStatus !== 'CAUGHT')

  if (b === 'READY') return list.filter(o => o.isReadyToBreed).length
  return list.length
}

function countFossilByReadiness(r: 'ALL' | 'READY') {
  let list = evoStore.fossilGoals as FossilGoal[]
  if (statusFilter.value === 'CAUGHT') list = list.filter(f => f.isCaught)
  else if (statusFilter.value === 'MISSING') list = list.filter(f => !f.isCaught)

  if (r === 'READY') return list.filter(f => f.isReadyToRevive).length
  return list.length
}

function countLegendaryByMonument(m: 'ALL' | 'MONUMENT') {
  let list = evoStore.legendaryGoals as LegendaryGoal[]
  if (statusFilter.value === 'CAUGHT') list = list.filter(l => l.status === 'CAUGHT')
  else if (statusFilter.value === 'MISSING') list = list.filter(l => l.status !== 'CAUGHT')

  if (m === 'MONUMENT') return list.filter(l => l.isCoveredByMonuments).length
  return list.length
}

// 1. Évolutions à faire
const filteredPending = computed(() => {
  let list = evoStore.pendingGoals as EvolutionGoal[]

  if (statusFilter.value === 'CAUGHT') {
    list = list.filter(g => g.targetStatus === 'CAUGHT')
  } else if (statusFilter.value === 'MISSING') {
    list = list.filter(g => g.targetStatus !== 'CAUGHT')
  }

  if (pendingFilter.value === 'READY') {
    list = list.filter(g => g.feasibility === 'READY_TO_EVOLVE')
  } else if (pendingFilter.value === 'PC') {
    list = list.filter(g => g.feasibility === 'CAN_EVOLVE_WITH_PC')
  }

  if (methodFilter.value === 'LEVEL_ONLY') {
    list = list.filter(g => isLevelOnlyEvolution(g))
  } else if (methodFilter.value === 'ITEM') {
    list = list.filter(g => isItemEvolution(g))
  } else if (methodFilter.value === 'TRADE') {
    list = list.filter(g => isTradeEvolution(g))
  } else if (methodFilter.value === 'MECHANIC') {
    list = list.filter(g => isMechanicEvolution(g))
  } else if (methodFilter.value === 'FRIENDSHIP') {
    list = list.filter(g => isFriendshipEvolution(g))
  } else if (methodFilter.value === 'MOVE') {
    list = list.filter(g => isMoveEvolution(g))
  } else if (methodFilter.value === 'WEATHER_TIME') {
    list = list.filter(g => isWeatherOrTimeEvolution(g))
  } else if (methodFilter.value === 'ACTION') {
    list = list.filter(g => isActionEvolution(g))
  }

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase().trim()
    list = list.filter(g =>
      g.sourceName.toLowerCase().includes(q) ||
      g.targetName.toLowerCase().includes(q) ||
      (g.sourceSpeciesName && g.sourceSpeciesName.toLowerCase().includes(q)) ||
      (g.targetSpeciesName && g.targetSpeciesName.toLowerCase().includes(q)) ||
      g.requirementsSummary.toLowerCase().includes(q) ||
      g.requiredItems.some(it => it.toLowerCase().includes(q)) ||
      String(g.sourceDexNumber).includes(q) ||
      String(g.targetDexNumber).includes(q)
    )
  }

  return list
})

// 2. Évolutions par pierre / objet
const filteredItemGoals = computed(() => {
  let list = evoStore.itemGoals as EvolutionGoal[]

  if (statusFilter.value === 'CAUGHT') {
    list = list.filter(g => g.targetStatus === 'CAUGHT')
  } else if (statusFilter.value === 'MISSING') {
    list = list.filter(g => g.targetStatus !== 'CAUGHT')
  }

  if (itemInventoryFilter.value === 'OWNED') {
    list = list.filter(g => g.isItemOwned)
  } else if (itemInventoryFilter.value === 'MISSING') {
    list = list.filter(g => !g.isItemOwned)
  }

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase().trim()
    list = list.filter(g =>
      g.sourceName.toLowerCase().includes(q) ||
      g.targetName.toLowerCase().includes(q) ||
      (g.sourceSpeciesName && g.sourceSpeciesName.toLowerCase().includes(q)) ||
      (g.targetSpeciesName && g.targetSpeciesName.toLowerCase().includes(q)) ||
      g.requiredItems.some(it => it.toLowerCase().includes(q)) ||
      String(g.sourceDexNumber).includes(q) ||
      String(g.targetDexNumber).includes(q)
    )
  }

  return list
})

// 3. Bébés & origines
const filteredOrigins = computed(() => {
  let list = evoStore.originGoals as BreedingOrigin[]

  if (statusFilter.value === 'CAUGHT') {
    list = list.filter(o => o.targetStatus === 'CAUGHT')
  } else if (statusFilter.value === 'MISSING') {
    list = list.filter(o => o.targetStatus !== 'CAUGHT')
  }

  if (originFilter.value === 'READY') {
    list = list.filter(o => o.isReadyToBreed)
  }

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase().trim()
    list = list.filter(o =>
      o.baseName.toLowerCase().includes(q) ||
      (o.baseSpeciesName && o.baseSpeciesName.toLowerCase().includes(q)) ||
      o.candidateParents.some(p => p.toLowerCase().includes(q)) ||
      (o.requiredIncense && o.requiredIncense.toLowerCase().includes(q)) ||
      String(o.baseDexNumber).includes(q)
    )
  }

  return list
})

// 4. Fossiles
const filteredFossils = computed(() => {
  let list = evoStore.fossilGoals as FossilGoal[]

  if (statusFilter.value === 'CAUGHT') {
    list = list.filter(f => f.isCaught)
  } else if (statusFilter.value === 'MISSING') {
    list = list.filter(f => !f.isCaught)
  }

  if (fossilFilter.value === 'READY') {
    list = list.filter(f => f.isReadyToRevive)
  }

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase().trim()
    list = list.filter(f =>
      f.speciesName.toLowerCase().includes(q) ||
      f.fossilName.toLowerCase().includes(q) ||
      String(f.nationalDexNumber).includes(q)
    )
  }

  return list
})

// 5. Légendaires
const filteredLegendaries = computed(() => {
  let list = evoStore.legendaryGoals as LegendaryGoal[]

  if (statusFilter.value === 'CAUGHT') {
    list = list.filter(l => l.status === 'CAUGHT')
  } else if (statusFilter.value === 'MISSING') {
    list = list.filter(l => l.status !== 'CAUGHT')
  }

  if (legendaryFilter.value === 'MONUMENT') {
    list = list.filter(l => l.isCoveredByMonuments)
  }

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.toLowerCase().trim()
    list = list.filter(l =>
      l.speciesName.toLowerCase().includes(q) ||
      String(l.nationalDexNumber).includes(q) ||
      (l.monumentStructure && l.monumentStructure.toLowerCase().includes(q))
    )
  }

  return list
})
</script>

<template>
  <div class="evolutions-view">
    <!-- Sub-tab Navigation Header -->
    <div class="glass-panel nav-bar">
      <div class="subtabs-list">
        <button 
          class="subtab-btn" 
          :class="{ active: evoStore.activeSubTab === 'PENDING' }"
          @click="evoStore.activeSubTab = 'PENDING'"
        >
          <span class="subtab-icon">⚡</span>
          <div class="subtab-text">
            <span class="title">Évolutions à faire</span>
            <span class="count-badge">{{ evoStore.pendingGoals.filter(g => g.targetStatus !== 'CAUGHT').length }}</span>
          </div>
        </button>

        <button 
          class="subtab-btn" 
          :class="{ active: evoStore.activeSubTab === 'ITEMS' }"
          @click="evoStore.activeSubTab = 'ITEMS'"
        >
          <span class="subtab-icon">💎</span>
          <div class="subtab-text">
            <span class="title">Par Pierre & Objet</span>
            <span class="count-badge">{{ evoStore.itemGoals.filter(g => g.targetStatus !== 'CAUGHT').length }}</span>
          </div>
        </button>

        <button 
          class="subtab-btn" 
          :class="{ active: evoStore.activeSubTab === 'ORIGINS' }"
          @click="evoStore.activeSubTab = 'ORIGINS'"
        >
          <span class="subtab-icon">🥚</span>
          <div class="subtab-text">
            <span class="title">Bébés & Origines</span>
            <span class="count-badge">{{ evoStore.originGoals.filter(g => g.targetStatus !== 'CAUGHT').length }}</span>
          </div>
        </button>

        <button 
          class="subtab-btn" 
          :class="{ active: evoStore.activeSubTab === 'FOSSILS' }"
          @click="evoStore.activeSubTab = 'FOSSILS'"
        >
          <span class="subtab-icon">🦴</span>
          <div class="subtab-text">
            <span class="title">Fossiles</span>
            <span class="count-badge ready" v-if="evoStore.fossilGoals.filter(f => f.isReadyToRevive).length > 0">
              {{ evoStore.fossilGoals.filter(f => f.isReadyToRevive).length }} prêt(s)
            </span>
            <span class="count-badge" v-else>{{ evoStore.fossilGoals.filter(f => !f.isCaught).length }}</span>
          </div>
        </button>

        <button 
          class="subtab-btn" 
          :class="{ active: evoStore.activeSubTab === 'LEGENDARIES' }"
          @click="evoStore.activeSubTab = 'LEGENDARIES'"
        >
          <span class="subtab-icon">👑</span>
          <div class="subtab-text">
            <span class="title">Légendaires & Sanctuaires</span>
            <span class="count-badge">{{ evoStore.legendaryGoals.filter(l => l.status !== 'CAUGHT').length }}</span>
          </div>
        </button>
      </div>

      <button class="glass-btn refresh-btn" @click="evoStore.fetchAllEvolutions()" :disabled="evoStore.isLoading">
        🔄 Actualiser
      </button>
    </div>

    <!-- Filter Panel (Same search bar and status pills as Pokédex, defaulted to MISSING) -->
    <div class="glass-panel filter-panel">
      <!-- Search Row + Status Filters -->
      <div class="search-row">
        <div class="search-input-wrap">
          <span class="search-icon">🔍</span>
          <input 
            v-model="searchQuery" 
            class="glass-input search-box" 
            placeholder="Rechercher par nom (Pikachu), numéro (#25), objet requis..." 
          />
          <button 
            v-if="searchQuery" 
            class="clear-btn" 
            @click="searchQuery = ''"
          >
            ✕
          </button>
        </div>

        <!-- Status Filter Pills -->
        <div class="status-filters">
          <button 
            class="glass-btn" 
            :class="{ active: statusFilter === 'ALL' }"
            @click="statusFilter = 'ALL'"
          >
            Tous ({{ currentTabTotalCount }})
          </button>
          <button 
            class="glass-btn caught-btn" 
            :class="{ active: statusFilter === 'CAUGHT' }"
            @click="statusFilter = 'CAUGHT'"
          >
            ✔ Capturés ({{ currentTabCaughtCount }})
          </button>
          <button 
            class="glass-btn missing-btn" 
            :class="{ active: statusFilter === 'MISSING' }"
            @click="statusFilter = 'MISSING'"
          >
            ✕ Manquants ({{ currentTabMissingCount }})
          </button>
        </div>
      </div>

      <!-- Secondary Contextual Sub-Pills -->
      <div v-if="evoStore.activeSubTab === 'PENDING'" class="secondary-pills-row">
        <span class="pills-label">Disponibilité :</span>
        <div class="pills-list">
          <button 
            class="glass-btn filter-pill" 
            :class="{ active: pendingFilter === 'ALL' }"
            @click="pendingFilter = 'ALL'"
          >
            Toutes ({{ countPendingByFeasibility('ALL') }})
          </button>
          <button 
            class="glass-btn filter-pill ready-pill" 
            :class="{ active: pendingFilter === 'READY' }"
            @click="pendingFilter = 'READY'"
          >
            🔥 Prêtes dans l'équipe ({{ countPendingByFeasibility('READY') }})
          </button>
          <button 
            class="glass-btn filter-pill pc-pill" 
            :class="{ active: pendingFilter === 'PC' }"
            @click="pendingFilter = 'PC'"
          >
            📦 Faisables via PC ({{ countPendingByFeasibility('PC') }})
          </button>
        </div>
      </div>

      <!-- Evolution Method Sub-Pills -->
      <div v-if="evoStore.activeSubTab === 'PENDING'" class="secondary-pills-row method-row">
        <span class="pills-label">Méthode :</span>
        <div class="pills-list">
          <button 
            class="glass-btn filter-pill" 
            :class="{ active: methodFilter === 'ALL' }"
            @click="methodFilter = 'ALL'"
          >
            Toutes ({{ countPendingByMethod('ALL') }})
          </button>
          <button 
            class="glass-btn filter-pill level-pill" 
            :class="{ active: methodFilter === 'LEVEL_ONLY' }"
            @click="methodFilter = 'LEVEL_ONLY'"
          >
            📈 Niveau uniquement ({{ countPendingByMethod('LEVEL_ONLY') }})
          </button>
          <button 
            class="glass-btn filter-pill item-pill" 
            :class="{ active: methodFilter === 'ITEM' }"
            @click="methodFilter = 'ITEM'"
          >
            💎 Objets ({{ countPendingByMethod('ITEM') }})
          </button>
          <button 
            class="glass-btn filter-pill trade-pill" 
            :class="{ active: methodFilter === 'TRADE' }"
            @click="methodFilter = 'TRADE'"
          >
            🔄 Échange ({{ countPendingByMethod('TRADE') }})
          </button>
          <button 
            class="glass-btn filter-pill mechanic-pill" 
            :class="{ active: methodFilter === 'MECHANIC' }"
            @click="methodFilter = 'MECHANIC'"
          >
            ⚙️ Mécanique ({{ countPendingByMethod('MECHANIC') }})
          </button>
          <button 
            class="glass-btn filter-pill friendship-pill" 
            :class="{ active: methodFilter === 'FRIENDSHIP' }"
            @click="methodFilter = 'FRIENDSHIP'"
          >
            💖 Bonheur ({{ countPendingByMethod('FRIENDSHIP') }})
          </button>
          <button 
            class="glass-btn filter-pill move-pill" 
            :class="{ active: methodFilter === 'MOVE' }"
            @click="methodFilter = 'MOVE'"
          >
            ⚔️ Capacités ({{ countPendingByMethod('MOVE') }})
          </button>
          <button 
            class="glass-btn filter-pill weather-pill" 
            :class="{ active: methodFilter === 'WEATHER_TIME' }"
            @click="methodFilter = 'WEATHER_TIME'"
          >
            ☀️ Météo & Heure ({{ countPendingByMethod('WEATHER_TIME') }})
          </button>
          <button 
            class="glass-btn filter-pill action-pill" 
            :class="{ active: methodFilter === 'ACTION' }"
            @click="methodFilter = 'ACTION'"
          >
            🏃 Actions ({{ countPendingByMethod('ACTION') }})
          </button>
        </div>
      </div>

      <div v-else-if="evoStore.activeSubTab === 'ITEMS'" class="secondary-pills-row">
        <span class="pills-label">Inventaire :</span>
        <div class="pills-list">
          <button 
            class="glass-btn filter-pill" 
            :class="{ active: itemInventoryFilter === 'ALL' }"
            @click="itemInventoryFilter = 'ALL'"
          >
            Tous les objets ({{ countItemByInventory('ALL') }})
          </button>
          <button 
            class="glass-btn filter-pill ready-pill" 
            :class="{ active: itemInventoryFilter === 'OWNED' }"
            @click="itemInventoryFilter = 'OWNED'"
          >
            ✔ Objets possédés ({{ countItemByInventory('OWNED') }})
          </button>
          <button 
            class="glass-btn filter-pill missing-pill" 
            :class="{ active: itemInventoryFilter === 'MISSING' }"
            @click="itemInventoryFilter = 'MISSING'"
          >
            ✕ Objets manquants ({{ countItemByInventory('MISSING') }})
          </button>
        </div>
      </div>

      <div v-else-if="evoStore.activeSubTab === 'ORIGINS'" class="secondary-pills-row">
        <span class="pills-label">Reproduction :</span>
        <div class="pills-list">
          <button 
            class="glass-btn filter-pill" 
            :class="{ active: originFilter === 'ALL' }"
            @click="originFilter = 'ALL'"
          >
            Toutes les origines ({{ countOriginByBreeding('ALL') }})
          </button>
          <button 
            class="glass-btn filter-pill ready-pill" 
            :class="{ active: originFilter === 'READY' }"
            @click="originFilter = 'READY'"
          >
            🥚 Prêts à reproduire ({{ countOriginByBreeding('READY') }})
          </button>
        </div>
      </div>

      <div v-else-if="evoStore.activeSubTab === 'FOSSILS'" class="secondary-pills-row">
        <span class="pills-label">État fossile :</span>
        <div class="pills-list">
          <button 
            class="glass-btn filter-pill" 
            :class="{ active: fossilFilter === 'ALL' }"
            @click="fossilFilter = 'ALL'"
          >
            Tous les fossiles ({{ countFossilByReadiness('ALL') }})
          </button>
          <button 
            class="glass-btn filter-pill ready-pill" 
            :class="{ active: fossilFilter === 'READY' }"
            @click="fossilFilter = 'READY'"
          >
            🦴 Prêts à ressusciter ({{ countFossilByReadiness('READY') }})
          </button>
        </div>
      </div>

      <div v-else-if="evoStore.activeSubTab === 'LEGENDARIES'" class="secondary-pills-row">
        <span class="pills-label">Sanctuaires :</span>
        <div class="pills-list">
          <button 
            class="glass-btn filter-pill" 
            :class="{ active: legendaryFilter === 'ALL' }"
            @click="legendaryFilter = 'ALL'"
          >
            Tous les légendaires ({{ countLegendaryByMonument('ALL') }})
          </button>
          <button 
            class="glass-btn filter-pill ready-pill" 
            :class="{ active: legendaryFilter === 'MONUMENT' }"
            @click="legendaryFilter = 'MONUMENT'"
          >
            🏛️ Avec sanctuaires ({{ countLegendaryByMonument('MONUMENT') }})
          </button>
        </div>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="evoStore.isLoading" class="loading-state">
      <div class="spinner"></div>
      <p>Analyse des évolutions et de la boîte PC en direct...</p>
    </div>

    <!-- 1. PENDING EVOLUTIONS SUBTAB -->
    <div v-else-if="evoStore.activeSubTab === 'PENDING'" class="tab-content">
      <div v-if="filteredPending.length === 0" class="empty-state glass-panel">
        <span class="empty-icon">✨</span>
        <h3>Aucune évolution en attente trouvée</h3>
        <p>Aucun Pokémon ne correspond aux critères de filtre ou de recherche sélectionnés.</p>
      </div>

      <div v-else class="evo-grid">
        <div 
          v-for="(g, idx) in filteredPending" 
          :key="idx"
          class="evo-card glass-card"
          :class="{ 'ready-card': g.feasibility === 'READY_TO_EVOLVE', 'pc-card': g.feasibility === 'CAN_EVOLVE_WITH_PC' }"
        >
          <!-- Feasibility & Method Badges -->
          <div class="card-status-strip">
            <span v-if="g.feasibility === 'READY_TO_EVOLVE'" class="badge ready-tag">
              ⚡ PRÊT DANS L'ÉQUIPE
            </span>
            <span v-else class="badge pc-tag">
              📦 EN BOÎTE PC ({{ g.ownedLocationSummary || 'PC' }})
            </span>

            <div class="method-badges">
              <span v-if="isLevelOnlyEvolution(g)" class="badge method-tag level-method-tag">
                📈 {{ g.minLevel ? `Niv. ${g.minLevel}` : 'Niveau' }}
              </span>
              <span v-if="isItemEvolution(g)" class="badge method-tag item-method-tag">
                💎 Objet
              </span>
              <span v-if="isTradeEvolution(g)" class="badge method-tag trade-method-tag">
                🔄 Échange
              </span>
              <span v-if="isFriendshipEvolution(g)" class="badge method-tag friendship-method-tag">
                💖 Bonheur
              </span>
              <span v-if="isMoveEvolution(g)" class="badge method-tag move-method-tag">
                ⚔️ Capacité
              </span>
              <span v-if="isWeatherOrTimeEvolution(g)" class="badge method-tag weather-method-tag">
                ☀️ Moment
              </span>
              <span v-if="isActionEvolution(g)" class="badge method-tag action-method-tag">
                🏃 Action
              </span>
              <span v-if="isSpecialEvolution(g)" class="badge method-tag special-method-tag">
                🧬 Spécial
              </span>
            </div>
          </div>

          <!-- Flow: Source -> Arrow -> Target -->
          <div class="evo-flow">
            <!-- Source Pokemon -->
            <div class="flow-mon" @click="pokedexStore.openDetail(g.sourceSpeciesId)">
              <div class="mon-sprite-box">
                <img 
                  :src="getSpriteUrl(g.sourceSpeciesName || g.sourceName, g.sourceDexNumber)" 
                  class="flow-sprite"
                  @error="handleImgError($event, g.sourceDexNumber)"
                />
              </div>
              <div class="mon-name">{{ g.sourceName }}</div>
              <div class="mon-dex font-mono">#{{ String(g.sourceDexNumber).padStart(4, '0') }}</div>
            </div>

            <!-- Evolution Arrow & Condition -->
            <div class="flow-arrow-box">
              <div class="flow-arrow">➔</div>
              <div class="flow-req">{{ g.requirementsSummary || 'Conditions requises' }}</div>
              <div v-if="g.additionalConditions && g.additionalConditions.length > 0" class="flow-conditions">
                <span v-for="c in g.additionalConditions" :key="c" class="cond-chip">
                  {{ c }}
                </span>
              </div>
              <div v-if="g.requiredItems.length > 0" class="flow-items">
                <span 
                  v-for="it in g.requiredItems" 
                  :key="it" 
                  class="item-chip"
                  :class="{ owned: g.isItemOwned }"
                >
                  {{ it }}
                </span>
              </div>
            </div>

            <!-- Target Pokemon -->
            <div class="flow-mon" @click="pokedexStore.openDetail(g.targetSpeciesId)">
              <div class="mon-sprite-box target-box">
                <img 
                  :src="getSpriteUrl(g.targetSpeciesName || g.targetName, g.targetDexNumber)" 
                  class="flow-sprite"
                  @error="handleImgError($event, g.targetDexNumber)"
                />
              </div>
              <div class="mon-name target-name">{{ g.targetName }}</div>
              <div class="mon-dex font-mono">#{{ String(g.targetDexNumber).padStart(4, '0') }}</div>
            </div>
          </div>

          <!-- Details Footer -->
          <div class="evo-footer">
            <span class="location-text">
              📍 Emplacement : <strong>{{ g.ownedLocationSummary || 'Boîte PC' }}</strong>
            </span>
            <span v-if="g.ownedInstancesCount > 1" class="multi-count">
              ({{ g.ownedInstancesCount }} spécimens éligibles)
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. ITEM EVOLUTIONS SUBTAB -->
    <div v-else-if="evoStore.activeSubTab === 'ITEMS'" class="tab-content">
      <div v-if="filteredItemGoals.length === 0" class="empty-state glass-panel">
        <span class="empty-icon">💎</span>
        <h3>Aucune évolution par pierre ou objet trouvée</h3>
        <p>Aucun Pokémon ne correspond aux critères de filtre ou de recherche sélectionnés.</p>
      </div>

      <div v-else class="evo-grid">
        <div 
          v-for="(g, idx) in filteredItemGoals" 
          :key="idx"
          class="evo-card glass-card"
          :class="{ 'item-owned-card': g.isItemOwned }"
        >
          <div class="card-status-strip">
            <span v-if="g.isItemOwned" class="badge item-owned-tag">
              ✔ OBJET EN INVENTAIRE
            </span>
            <span v-else class="badge item-missing-tag">
              ✕ OBJET MANQUANT
            </span>
          </div>

          <div class="evo-flow">
            <div class="flow-mon" @click="pokedexStore.openDetail(g.sourceSpeciesId)">
              <div class="mon-sprite-box">
                <img 
                  :src="getSpriteUrl(g.sourceSpeciesName || g.sourceName, g.sourceDexNumber)" 
                  class="flow-sprite"
                  @error="handleImgError($event, g.sourceDexNumber)"
                />
              </div>
              <div class="mon-name">{{ g.sourceName }}</div>
              <div class="mon-dex font-mono">#{{ String(g.sourceDexNumber).padStart(4, '0') }}</div>
            </div>

            <div class="flow-arrow-box">
              <div class="flow-arrow">➔</div>
              <div class="required-items-list">
                <span 
                  v-for="item in g.requiredItems" 
                  :key="item"
                  class="stone-badge"
                  :class="{ owned: g.isItemOwned }"
                >
                  💎 {{ item }}
                </span>
              </div>
            </div>

            <div class="flow-mon" @click="pokedexStore.openDetail(g.targetSpeciesId)">
              <div class="mon-sprite-box target-box">
                <img 
                  :src="getSpriteUrl(g.targetSpeciesName || g.targetName, g.targetDexNumber)" 
                  class="flow-sprite"
                  @error="handleImgError($event, g.targetDexNumber)"
                />
              </div>
              <div class="mon-name target-name">{{ g.targetName }}</div>
              <div class="mon-dex font-mono">#{{ String(g.targetDexNumber).padStart(4, '0') }}</div>
            </div>
          </div>

          <div class="evo-footer">
            <span v-if="g.ownedInstancesCount > 0" class="owned-info has-specimen">
              🐾 Vous possédez {{ g.ownedInstancesCount }} spécimen(s) éligible(s) ({{ g.ownedLocationSummary || 'PC' }})
            </span>
            <span v-else class="owned-info no-specimen">
              ✕ Vous ne possédez aucun spécimen de base
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. MISSING BABY & ORIGINS SUBTAB -->
    <div v-else-if="evoStore.activeSubTab === 'ORIGINS'" class="tab-content">
      <div class="info-banner glass-panel">
        <span class="info-icon">💡</span>
        <div>
          <strong>Génération Inférieure & Bébés :</strong>
          Ces formes de base sont actuellement manquantes dans votre Pokédex mais vous possédez les parents nécessaires pour les faire éclore !
        </div>
      </div>

      <div v-if="filteredOrigins.length === 0" class="empty-state glass-panel">
        <span class="empty-icon">🎉</span>
        <h3>Aucune origine correspondante</h3>
        <p>Aucune forme de base ou bébé ne correspond aux critères de filtre sélectionnés.</p>
      </div>

      <div v-else class="origins-grid">
        <div 
          v-for="ori in filteredOrigins" 
          :key="ori.baseSpeciesId"
          class="origin-card glass-card"
          :class="{ 'ready-breed': ori.isReadyToBreed }"
        >
          <div class="card-status-strip">
            <span v-if="ori.isReadyToBreed" class="badge ready-tag">
              🥚 PRÊT À REPRODUIRE !
            </span>
            <span v-else class="badge neutral-tag">
              PARENTS REQUIS
            </span>
          </div>

          <div class="origin-body" @click="pokedexStore.openDetail(ori.baseSpeciesId)">
            <div class="mon-sprite-box baby-box">
              <img 
                :src="getSpriteUrl(ori.baseSpeciesName || ori.baseName, ori.baseDexNumber)" 
                class="flow-sprite" 
                @error="handleImgError($event, ori.baseDexNumber)"
              />
            </div>
            <div class="origin-details">
              <div class="baby-badge" v-if="ori.isBaby">👶 Bébé Pokémon</div>
              <h3 class="origin-name">{{ ori.baseName }}</h3>
              <div class="font-mono dex-sub">#{{ String(ori.baseDexNumber).padStart(4, '0') }}</div>

              <div class="parents-box">
                <span class="label">Parents compatibles :</span>
                <div class="parents-list">
                  <span v-for="p in ori.candidateParents" :key="p" class="parent-pill">{{ p }}</span>
                </div>
              </div>

              <div v-if="ori.requiredIncense" class="incense-box">
                🌸 <strong>Encens requis :</strong> {{ ori.requiredIncense }}
              </div>

              <div class="parents-status">
                📍 Spécimens possédés : <strong>{{ ori.ownedParentsSummary || 'Aucun' }}</strong>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 4. FOSSILS & REVIVAL SUBTAB -->
    <div v-else-if="evoStore.activeSubTab === 'FOSSILS'" class="tab-content">
      <div v-if="filteredFossils.length === 0" class="empty-state glass-panel">
        <span class="empty-icon">🦴</span>
        <h3>Aucun fossile correspondant</h3>
        <p>Aucun fossile ne correspond aux filtres ou à la recherche actuels.</p>
      </div>

      <div v-else class="fossils-grid">
        <div 
          v-for="f in filteredFossils" 
          :key="f.speciesId"
          class="fossil-card glass-card"
          :class="{ 'ready-revive': f.isReadyToRevive, 'is-caught': f.isCaught }"
          @click="pokedexStore.openDetail(f.speciesId)"
        >
          <div class="card-status-strip">
            <span v-if="f.isCaught" class="badge caught-tag">
              ✔ CAPTURÉ
            </span>
            <span v-else-if="f.isReadyToRevive" class="badge ready-tag glow-pulse">
              ✨ PRÊT À RESSUSCITER ! (x{{ f.heldFossilCount }})
            </span>
            <span v-else class="badge missing-tag">
              ✕ FOSSILE MANQUANT
            </span>
          </div>

          <div class="fossil-sprite-box">
            <img 
              :src="getSpriteUrl(f.speciesName, f.nationalDexNumber)" 
              class="fossil-sprite"
              @error="handleImgError($event, f.nationalDexNumber)"
            />
          </div>

          <div class="fossil-name">{{ f.speciesName }}</div>
          <div class="fossil-dex font-mono">#{{ String(f.nationalDexNumber).padStart(4, '0') }}</div>

          <div class="fossil-item-badge">
            <span class="bone-icon">🦴</span> {{ f.fossilName }}
          </div>

          <div class="fossil-inventory">
            Dans votre inventaire : <strong>{{ f.heldFossilCount }}</strong>
          </div>

          <div v-if="f.associatedStructures.length > 0" class="structures-list">
            <span class="struct-title">Sites archéologiques :</span>
            <span v-for="s in f.associatedStructures" :key="s" class="struct-tag">{{ s }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 5. LEGENDARIES & SANCTUARIES SUBTAB -->
    <div v-else-if="evoStore.activeSubTab === 'LEGENDARIES'" class="tab-content">
      <div class="info-banner glass-panel">
        <span class="info-icon">👑</span>
        <div>
          <strong>Légendaires, Fabuleux & Ultra-Chimères :</strong>
          Suivez l'état de capture des entités d'exception et la localisation de leurs sanctuaires ou monuments ancestraux.
        </div>
      </div>

      <div v-if="filteredLegendaries.length === 0" class="empty-state glass-panel">
        <span class="empty-icon">👑</span>
        <h3>Aucun légendaire correspondant</h3>
        <p>Aucun Pokémon légendaire ne correspond aux critères sélectionnés.</p>
      </div>

      <div v-else class="legendaries-grid">
        <div 
          v-for="leg in filteredLegendaries" 
          :key="leg.speciesId"
          class="legendary-card glass-card"
          :class="[
            `status-${leg.status.toLowerCase()}`,
            { 'has-monument': leg.isCoveredByMonuments }
          ]"
          @click="pokedexStore.openDetail(leg.speciesId)"
        >
          <div class="card-status-strip">
            <span v-if="leg.status === 'CAUGHT'" class="badge caught-tag">✔ CAPTURÉ</span>
            <span v-else-if="leg.status === 'ENCOUNTERED'" class="badge seen-tag">👁 VU</span>
            <span v-else class="badge missing-tag">✕ NON RENCONTRÉ</span>
          </div>

          <div class="legendary-sprite-box">
            <img 
              :src="getSpriteUrl(leg.speciesName, leg.nationalDexNumber)" 
              class="leg-sprite"
              @error="handleImgError($event, leg.nationalDexNumber)"
            />
          </div>

          <div class="leg-name">{{ leg.speciesName }}</div>
          <div class="leg-dex font-mono">#{{ String(leg.nationalDexNumber).padStart(4, '0') }}</div>

          <div class="monument-info" v-if="leg.isCoveredByMonuments">
            <span class="monument-icon">🏛</span>
            <span class="monument-name">{{ leg.monumentStructure || 'Sanctuaire Mythique' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.evolutions-view {
  padding: 0 16px 32px 16px;
}

.nav-bar {
  padding: 12px 16px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.subtabs-list {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.subtab-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: var(--text-dim);
  cursor: pointer;
  transition: all 0.2s ease;
}

.subtab-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-main);
  border-color: rgba(255, 255, 255, 0.2);
}

.subtab-btn.active {
  background: rgba(99, 102, 241, 0.2);
  border-color: var(--accent-indigo);
  color: #FFFFFF;
  box-shadow: 0 0 16px rgba(99, 102, 241, 0.25);
}

.subtab-icon {
  font-size: 1.1rem;
}

.subtab-text {
  display: flex;
  align-items: center;
  gap: 6px;
}

.subtab-text .title {
  font-size: 0.85rem;
  font-weight: 700;
}

.count-badge {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-main);
}

.count-badge.ready {
  background: rgba(34, 197, 94, 0.25);
  color: #4ade80;
  border: 1px solid #22c55e;
}

.refresh-btn {
  padding: 8px 14px;
  font-size: 0.82rem;
}

.filter-panel {
  padding: 16px 20px;
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.search-input-wrap {
  position: relative;
  flex: 1;
  min-width: 260px;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-dim);
  font-size: 14px;
}

.search-box {
  padding-left: 36px;
  padding-right: 36px;
}

.clear-btn {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  background: transparent;
  border: none;
  color: var(--text-dim);
  cursor: pointer;
}

.clear-btn:hover {
  color: var(--text-main);
}

.status-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.caught-btn.active {
  background: rgba(34, 197, 94, 0.2);
  border-color: #22c55e;
  color: #4ade80;
}

.seen-btn.active {
  background: rgba(56, 189, 248, 0.2);
  border-color: #38bdf8;
  color: #38bdf8;
}

.missing-btn.active {
  background: rgba(244, 63, 94, 0.2);
  border-color: #f43f5e;
  color: #fb7185;
}

.secondary-pills-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.pills-label {
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--text-dim);
  min-width: 110px;
}

.pills-list {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-pill {
  padding: 6px 12px;
  font-size: 0.8rem;
}

.filter-pill.active {
  background: rgba(255, 255, 255, 0.15);
  border-color: #FFFFFF;
}

.ready-pill.active {
  background: rgba(34, 197, 94, 0.25);
  border-color: #22c55e;
  color: #4ade80;
}

.pc-pill.active {
  background: rgba(56, 189, 248, 0.25);
  border-color: #38bdf8;
  color: #38bdf8;
}

.missing-pill.active {
  background: rgba(244, 63, 94, 0.25);
  border-color: #f43f5e;
  color: #fb7185;
}

.level-pill.active {
  background: rgba(16, 185, 129, 0.25);
  border-color: #10b981;
  color: #34d399;
}

.item-pill.active {
  background: rgba(168, 85, 247, 0.25);
  border-color: #a855f7;
  color: #c084fc;
}

.trade-pill.active {
  background: rgba(245, 158, 11, 0.25);
  border-color: #f59e0b;
  color: #fbbf24;
}

.mechanic-pill.active {
  background: rgba(99, 102, 241, 0.25);
  border-color: #6366f1;
  color: #818cf8;
}

.friendship-pill.active {
  background: rgba(236, 72, 153, 0.25);
  border-color: #ec4899;
  color: #f472b6;
}

.move-pill.active {
  background: rgba(239, 68, 68, 0.25);
  border-color: #ef4444;
  color: #f87171;
}

.weather-pill.active {
  background: rgba(6, 182, 212, 0.25);
  border-color: #06b6d4;
  color: #22d3ee;
}

.action-pill.active {
  background: rgba(249, 115, 22, 0.25);
  border-color: #f97316;
  color: #fb923c;
}

.info-banner {
  padding: 14px 18px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 0.88rem;
  line-height: 1.4;
  border-left: 3px solid var(--accent-indigo);
}

.info-icon {
  font-size: 1.5rem;
}

/* Grids */
.evo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.evo-card {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: transform 0.2s ease;
}

.evo-card:hover {
  transform: translateY(-2px);
}

.ready-card {
  border-color: rgba(34, 197, 94, 0.4);
  box-shadow: 0 0 18px rgba(34, 197, 94, 0.15);
}

.pc-card {
  border-color: rgba(56, 189, 248, 0.3);
}

.card-status-strip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.method-badges {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.method-tag {
  font-size: 0.65rem;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 5px;
  letter-spacing: 0.3px;
}

.level-method-tag {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.35);
}

.item-method-tag {
  background: rgba(168, 85, 247, 0.15);
  color: #c084fc;
  border: 1px solid rgba(168, 85, 247, 0.35);
}

.trade-method-tag {
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.35);
}

.mechanic-method-tag {
  background: rgba(99, 102, 241, 0.15);
  color: #818cf8;
  border: 1px solid rgba(99, 102, 241, 0.35);
}

.friendship-method-tag {
  background: rgba(236, 72, 153, 0.15);
  color: #f472b6;
  border: 1px solid rgba(236, 72, 153, 0.35);
}

.move-method-tag {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.35);
}

.weather-method-tag {
  background: rgba(6, 182, 212, 0.15);
  color: #22d3ee;
  border: 1px solid rgba(6, 182, 212, 0.35);
}

.action-method-tag {
  background: rgba(249, 115, 22, 0.15);
  color: #fb923c;
  border: 1px solid rgba(249, 115, 22, 0.35);
}

.special-method-tag {
  background: rgba(139, 92, 246, 0.15);
  color: #a78bfa;
  border: 1px solid rgba(139, 92, 246, 0.35);
}

.flow-conditions {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 2px;
}

.cond-chip {
  font-size: 0.65rem;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: var(--text-dim);
  padding: 2px 6px;
  border-radius: 4px;
  line-height: 1.2;
}

.badge {
  font-size: 0.68rem;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 6px;
  letter-spacing: 0.5px;
}

.ready-tag {
  background: rgba(34, 197, 94, 0.2);
  color: #4ade80;
  border: 1px solid #22c55e;
}

.pc-tag {
  background: rgba(56, 189, 248, 0.2);
  color: #38bdf8;
  border: 1px solid #38bdf8;
}

.item-owned-tag {
  background: rgba(34, 197, 94, 0.2);
  color: #4ade80;
  border: 1px solid #22c55e;
}

.item-missing-tag {
  background: rgba(244, 63, 94, 0.15);
  color: #fb7185;
  border: 1px solid rgba(244, 63, 94, 0.3);
}

.caught-tag {
  background: rgba(34, 197, 94, 0.2);
  color: #4ade80;
  border: 1px solid #22c55e;
}

.seen-tag {
  background: rgba(56, 189, 248, 0.2);
  color: #38bdf8;
  border: 1px solid #38bdf8;
}

.missing-tag {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-dim);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.neutral-tag {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-dim);
}

/* Flow Arrow */
.evo-flow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 6px 0;
}

.flow-mon {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  flex: 1;
}

.mon-sprite-box {
  width: 70px;
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  transition: transform 0.2s ease;
}

.target-box {
  background: rgba(99, 102, 241, 0.08);
  border-color: rgba(99, 102, 241, 0.25);
}

.flow-mon:hover .mon-sprite-box {
  transform: scale(1.08);
  border-color: rgba(255, 255, 255, 0.3);
}

.flow-sprite {
  max-width: 60px;
  max-height: 60px;
  image-rendering: pixelated;
}

.mon-name {
  font-size: 0.85rem;
  font-weight: 700;
  margin-top: 4px;
}

.target-name {
  color: var(--accent-cyan);
}

.mon-dex {
  font-size: 0.7rem;
  color: var(--text-dim);
}

.flow-arrow-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 4px;
  flex: 1.2;
}

.flow-arrow {
  font-size: 1.3rem;
  color: var(--text-dim);
}

.flow-req {
  font-size: 0.72rem;
  color: var(--text-main);
  line-height: 1.2;
}

.stone-badge {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.15);
  display: inline-block;
  margin-top: 4px;
}

.stone-badge.owned {
  background: rgba(34, 197, 94, 0.2);
  border-color: #22c55e;
  color: #4ade80;
}

.evo-footer {
  font-size: 0.75rem;
  color: var(--text-dim);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.has-specimen {
  color: #4ade80;
}

.no-specimen {
  color: var(--text-dim);
}

/* Origins Grid */
.origins-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}

.origin-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ready-breed {
  border-color: rgba(34, 197, 94, 0.35);
  box-shadow: 0 0 16px rgba(34, 197, 94, 0.12);
}

.origin-body {
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
}

.baby-box {
  width: 80px;
  height: 80px;
  flex-shrink: 0;
}

.origin-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.baby-badge {
  font-size: 0.65rem;
  font-weight: 800;
  color: #fb7185;
}

.origin-name {
  font-size: 1.05rem;
  font-weight: 700;
  margin: 0;
}

.dex-sub {
  font-size: 0.72rem;
  color: var(--text-dim);
}

.parents-box {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.parents-box .label {
  font-size: 0.72rem;
  color: var(--text-dim);
}

.parents-list {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.parent-pill {
  font-size: 0.7rem;
  background: rgba(255, 255, 255, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.incense-box {
  font-size: 0.75rem;
  color: #f472b6;
  margin-top: 2px;
}

.parents-status {
  font-size: 0.75rem;
  color: var(--accent-cyan);
  margin-top: 4px;
}

/* Fossils Grid */
.fossils-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.fossil-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;
  cursor: pointer;
}

.fossil-card.ready-revive {
  border-color: #22c55e;
  box-shadow: 0 0 20px rgba(34, 197, 94, 0.3);
}

.fossil-sprite-box {
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 4px 0;
}

.fossil-sprite {
  max-width: 75px;
  max-height: 75px;
  image-rendering: pixelated;
}

.fossil-name {
  font-size: 0.95rem;
  font-weight: 700;
}

.fossil-dex {
  font-size: 0.72rem;
  color: var(--text-dim);
}

.fossil-item-badge {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #fef08a;
  display: flex;
  align-items: center;
  gap: 6px;
}

.fossil-inventory {
  font-size: 0.78rem;
  color: var(--text-dim);
}

.fossil-inventory strong {
  color: var(--text-main);
}

.structures-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
  width: 100%;
}

.struct-title {
  font-size: 0.68rem;
  color: var(--text-dim);
  text-transform: uppercase;
}

.struct-tag {
  font-size: 0.68rem;
  background: rgba(255, 255, 255, 0.04);
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--text-dim);
  border: 1px solid rgba(255, 255, 255, 0.06);
}

/* Legendaries Grid */
.legendaries-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.legendary-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;
  cursor: pointer;
}

.legendary-card.status-missing {
  opacity: 0.65;
  filter: grayscale(40%);
}

.legendary-card:hover {
  opacity: 1;
  filter: grayscale(0%);
}

.legendary-sprite-box {
  width: 90px;
  height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.leg-sprite {
  max-width: 85px;
  max-height: 85px;
  image-rendering: pixelated;
}

.leg-name {
  font-size: 1rem;
  font-weight: 700;
}

.leg-dex {
  font-size: 0.75rem;
  color: var(--text-dim);
}

.monument-info {
  margin-top: 6px;
  font-size: 0.72rem;
  padding: 4px 8px;
  border-radius: 6px;
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.3);
  display: flex;
  align-items: center;
  gap: 4px;
}

.glow-pulse {
  animation: pulse 1.8s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.85; transform: scale(1.03); }
}
</style>
