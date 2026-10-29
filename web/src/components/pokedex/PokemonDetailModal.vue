<script setup lang="ts">
import { ref } from 'vue'
import { usePokedexStore } from '../../stores/pokedexStore'
import { usePokemonSprites } from '../../composables/usePokemonSprites'
import TypeBadge from '../common/TypeBadge.vue'
import StatHexagon from '../common/StatHexagon.vue'

const pokedexStore = usePokedexStore()
const { getArtworkUrl, getSpriteUrl, getFallbackSpriteUrl } = usePokemonSprites()

type DetailTab = 'INFOS' | 'STATS' | 'TALENTS' | 'FORMES' | 'EVOLUTIONS' | 'DROPS' | 'SPAWNS' | 'OWNED'
const currentTab = ref<DetailTab>('INFOS')
</script>

<template>
  <div v-if="pokedexStore.isDetailOpen" class="modal-overlay" @click.self="pokedexStore.closeDetail()">
    <div class="glass-panel modal-container">
      <!-- Loading State -->
      <div v-if="pokedexStore.isLoadingDetail" class="modal-loading">
        <p>Chargement des données détaillées...</p>
      </div>

      <template v-else-if="pokedexStore.selectedSpeciesDetail">
        <!-- Close Button -->
        <button class="modal-close-btn" @click="pokedexStore.closeDetail()">✕</button>

        <!-- Header -->
        <div class="modal-header">
          <div class="header-sprite-box">
            <img 
              :src="getArtworkUrl(pokedexStore.selectedSpeciesDetail.nationalDexNumber)" 
              :alt="pokedexStore.selectedSpeciesDetail.displayName"
              class="header-artwork"
              @error="($event.target as HTMLImageElement).src = getSpriteUrl(pokedexStore.selectedSpeciesDetail.name, pokedexStore.selectedSpeciesDetail.nationalDexNumber)"
            />
          </div>

          <div class="header-details">
            <div class="dex-tag font-mono">#{{ String(pokedexStore.selectedSpeciesDetail.nationalDexNumber).padStart(4, '0') }} • GEN {{ pokedexStore.selectedSpeciesDetail.generation }}</div>
            <h2 class="species-title">{{ pokedexStore.selectedSpeciesDetail.displayName }}</h2>
            <div class="species-types">
              <TypeBadge :type="pokedexStore.selectedSpeciesDetail.primaryType" />
              <TypeBadge v-if="pokedexStore.selectedSpeciesDetail.secondaryType" :type="pokedexStore.selectedSpeciesDetail.secondaryType" />
              <span class="status-pill-small" :class="pokedexStore.selectedSpeciesDetail.status.toLowerCase()">
                {{ pokedexStore.selectedSpeciesDetail.status === 'CAUGHT' ? '✔ Capturé' : pokedexStore.selectedSpeciesDetail.status === 'ENCOUNTERED' ? '👁 Vu' : '✕ Manquant' }}
              </span>
            </div>
          </div>
        </div>

        <!-- Navigation Tabs -->
        <div class="modal-nav">
          <button 
            v-for="t in (['INFOS', 'STATS', 'TALENTS', 'FORMES', 'EVOLUTIONS', 'DROPS', 'SPAWNS', 'OWNED'] as DetailTab[])"
            :key="t"
            class="modal-tab-btn"
            :class="{ active: currentTab === t }"
            @click="currentTab = t"
          >
            {{ t === 'OWNED' ? `Mes Spécimens (${pokedexStore.selectedSpeciesDetail.ownedInstances.length})` : t }}
          </button>
        </div>

        <!-- Tab Content -->
        <div class="modal-body">
          <!-- 1. INFOS -->
          <div v-if="currentTab === 'INFOS'" class="tab-pane">
            <div class="info-grid">
              <div class="info-card glass-card">
                <span class="info-label">Taille</span>
                <span class="info-val">{{ (pokedexStore.selectedSpeciesDetail.height / 10).toFixed(1) }} m</span>
              </div>
              <div class="info-card glass-card">
                <span class="info-label">Poids</span>
                <span class="info-val">{{ (pokedexStore.selectedSpeciesDetail.weight / 10).toFixed(1) }} kg</span>
              </div>
              <div class="info-card glass-card">
                <span class="info-label">Taux de Capture</span>
                <span class="info-val">{{ pokedexStore.selectedSpeciesDetail.catchRate }} / 255</span>
              </div>
              <div class="info-card glass-card">
                <span class="info-label">Amitié de Base</span>
                <span class="info-val">{{ pokedexStore.selectedSpeciesDetail.baseFriendship }}</span>
              </div>
            </div>

            <div v-if="pokedexStore.selectedSpeciesDetail.pastureJob" class="section-box glass-card mt-4">
              <div class="section-title">🌾 Métier de Pâturage (Cobbleworkers)</div>
              <div class="job-badge">{{ pokedexStore.selectedSpeciesDetail.pastureJob.jobName }}</div>
              <p class="job-desc">{{ pokedexStore.selectedSpeciesDetail.pastureJob.description }}</p>
            </div>
          </div>

          <!-- 2. STATS -->
          <div v-if="currentTab === 'STATS'" class="tab-pane">
            <div class="bst-box">
              <span>Total des Statistiques de Base (BST) :</span>
              <span class="bst-val">{{ pokedexStore.selectedSpeciesDetail.totalStats }}</span>
            </div>
            <div class="stat-bars-wrap glass-card">
              <StatHexagon 
                :stats="Object.fromEntries(pokedexStore.selectedSpeciesDetail.baseStats.map(s => [s.name, s.value]))"
                :max-stat="180"
              />
            </div>
          </div>

          <!-- 3. TALENTS -->
          <div v-if="currentTab === 'TALENTS'" class="tab-pane">
            <div class="abilities-list">
              <div 
                v-for="a in pokedexStore.selectedSpeciesDetail.abilities" 
                :key="a.name"
                class="ability-card glass-card"
                :class="{ 'hidden-ability': a.isHidden }"
              >
                <div class="ability-header">
                  <span class="ability-name">{{ a.displayName }}</span>
                  <span class="ability-badge" :class="a.isHidden ? 'badge-hidden' : 'badge-normal'">
                    {{ a.isHidden ? 'Talent Caché' : 'Normal' }}
                  </span>
                </div>
                <p class="ability-desc" v-if="a.description">{{ a.description }}</p>
              </div>
            </div>
          </div>

          <!-- 4. FORMES -->
          <div v-if="currentTab === 'FORMES'" class="tab-pane">
            <div class="forms-grid">
              <div 
                v-for="f in pokedexStore.selectedSpeciesDetail.forms" 
                :key="f.name"
                class="form-card glass-card"
              >
                <div class="form-title">{{ f.displayName }}</div>
                <div class="types-box">
                  <TypeBadge :type="f.primaryType" />
                  <TypeBadge v-if="f.secondaryType" :type="f.secondaryType" />
                </div>
                <div class="form-aspects" v-if="f.aspects.length > 0">
                  Aspects : {{ f.aspects.join(', ') }}
                </div>
              </div>
            </div>
          </div>

          <!-- 5. EVOLUTIONS -->
          <div v-if="currentTab === 'EVOLUTIONS'" class="tab-pane">
            <div v-if="pokedexStore.selectedSpeciesDetail.evolutions.length === 0" class="empty-tab">
              Ce Pokémon ne possède aucune évolution connue.
            </div>
            <div v-else class="evo-chain">
              <div 
                v-for="(evo, idx) in pokedexStore.selectedSpeciesDetail.evolutions" 
                :key="idx"
                class="evo-step glass-card"
              >
                <div class="evo-side">
                  <span class="evo-name font-bold">{{ evo.sourceName }}</span>
                </div>
                <div class="evo-arrow">
                  <span class="arrow-symbol">➔</span>
                  <span class="arrow-req">{{ evo.requirementsSummary }}</span>
                </div>
                <div class="evo-side">
                  <span class="evo-name font-bold">{{ evo.targetName }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 6. DROPS -->
          <div v-if="currentTab === 'DROPS'" class="tab-pane">
            <div v-if="pokedexStore.selectedSpeciesDetail.drops.length === 0" class="empty-tab">
              Aucun butin répertorié à la défaite de ce Pokémon.
            </div>
            <div v-else class="drops-grid">
              <div 
                v-for="(dr, idx) in pokedexStore.selectedSpeciesDetail.drops" 
                :key="idx"
                class="drop-card glass-card"
              >
                <div class="drop-item-name font-bold">{{ dr.displayName }}</div>
                <div class="drop-counts">Quantité : {{ dr.minCount }} - {{ dr.maxCount }}</div>
                <div class="drop-chance text-emerald font-bold">Chance : {{ dr.chancePercentage }}%</div>
              </div>
            </div>
          </div>

          <!-- 7. SPAWNS & POKE SNACK -->
          <div v-if="currentTab === 'SPAWNS'" class="tab-pane">
            <!-- Poké Snack Box -->
            <div v-if="pokedexStore.selectedSpeciesDetail.pokeSnack" class="snack-card glass-card mb-4">
              <div class="snack-header">
                <span class="snack-title">🍪 Assaisonnements de Poké Snack Optimaux</span>
                <span class="snack-rarity">{{ pokedexStore.selectedSpeciesDetail.pokeSnack.optimalTaste }}</span>
              </div>
              <div class="snack-recipe">
                <span v-for="item in pokedexStore.selectedSpeciesDetail.pokeSnack.recipeItems" :key="item" class="snack-pill">
                  {{ item }}
                </span>
              </div>
              <p class="snack-desc">{{ pokedexStore.selectedSpeciesDetail.pokeSnack.description }}</p>
            </div>

            <div class="section-title mb-2">🌲 Zones d'Apparition Sauvage</div>
            <div v-if="pokedexStore.selectedSpeciesDetail.spawns.length === 0" class="empty-tab">
              Ce Pokémon n'apparaît pas naturellement à l'état sauvage.
            </div>
            <div v-else class="spawns-list">
              <div 
                v-for="(sp, idx) in pokedexStore.selectedSpeciesDetail.spawns" 
                :key="idx"
                class="spawn-card glass-card"
              >
                <div class="spawn-header">
                  <span class="spawn-rarity font-bold uppercase">{{ sp.rarityBucket }}</span>
                  <span class="spawn-level">Niveaux {{ sp.minLevel }} - {{ sp.maxLevel }}</span>
                </div>
                <div class="spawn-biomes">
                  <span class="label">Biomes :</span> {{ sp.biomes.join(', ') || 'Tous biomes' }}
                </div>
                <div class="spawn-conditions" v-if="sp.timeRange || sp.weather">
                  <span v-if="sp.timeRange">⏰ {{ sp.timeRange }}</span>
                  <span v-if="sp.weather"> 🌧 {{ sp.weather }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 8. MES SPÉCIMENS -->
          <div v-if="currentTab === 'OWNED'" class="tab-pane">
            <div v-if="pokedexStore.selectedSpeciesDetail.ownedInstances.length === 0" class="empty-tab">
              Vous ne possédez aucun spécimen de cette espèce dans votre équipe ou vos boîtes PC.
            </div>
            <div v-else class="specimens-list">
              <div 
                v-for="poke in pokedexStore.selectedSpeciesDetail.ownedInstances" 
                :key="poke.uuid"
                class="specimen-card glass-card"
              >
                <div class="specimen-header">
                  <div class="specimen-title font-bold">
                    {{ poke.displayName }}
                    <span v-if="poke.isShiny" class="shiny-star">✨</span>
                  </div>
                  <div class="specimen-loc text-cyan font-bold">{{ poke.location }}</div>
                </div>
                <div class="specimen-sub">
                  Niveau {{ poke.level }} • {{ poke.nature }} • Talent: {{ poke.ability }} • Objet: {{ poke.heldItem || 'Aucun' }}
                </div>
                <div class="specimen-stats mt-2">
                  <StatHexagon :stats="poke.ivs" :is-iv="true" label="IVs (31 Parfaits)" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(8px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal-container {
  width: 100%;
  max-width: 780px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  background: rgba(18, 24, 38, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.modal-close-btn {
  position: absolute;
  top: 16px;
  right: 18px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: var(--text-main);
  border-radius: 8px;
  width: 32px;
  height: 32px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: all 0.2s ease;
  z-index: 10;
}

.modal-close-btn:hover {
  background: rgba(244, 63, 94, 0.3);
  border-color: #f43f5e;
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: radial-gradient(circle at top left, rgba(56, 189, 248, 0.1), transparent 60%);
}

.header-sprite-box {
  width: 110px;
  height: 110px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-artwork {
  max-width: 105px;
  max-height: 105px;
  filter: drop-shadow(0 8px 16px rgba(0, 0, 0, 0.5));
}

.dex-tag {
  font-size: 0.8rem;
  color: var(--text-dim);
  font-weight: 600;
}

.species-title {
  font-size: 1.6rem;
  font-weight: 800;
  color: #FFFFFF;
  margin: 2px 0 8px 0;
}

.species-types {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-pill-small {
  font-size: 0.72rem;
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 700;
}

.status-pill-small.caught {
  background: rgba(34, 197, 94, 0.2);
  color: #4ade80;
  border: 1px solid rgba(34, 197, 94, 0.4);
}

.status-pill-small.encountered {
  background: rgba(56, 189, 248, 0.2);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.4);
}

.status-pill-small.missing {
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-dim);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.modal-nav {
  display: flex;
  gap: 4px;
  padding: 10px 24px;
  background: rgba(14, 18, 28, 0.6);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  overflow-x: auto;
}

.modal-tab-btn {
  background: transparent;
  border: none;
  padding: 6px 12px;
  border-radius: 8px;
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.modal-tab-btn:hover {
  color: #FFFFFF;
  background: rgba(255, 255, 255, 0.06);
}

.modal-tab-btn.active {
  color: #FFFFFF;
  background: rgba(56, 189, 248, 0.2);
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.25);
}

.modal-body {
  padding: 20px 24px;
  overflow-y: auto;
  flex: 1;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.info-card {
  padding: 14px;
  display: flex;
  flex-direction: column;
}

.info-label {
  font-size: 0.72rem;
  color: var(--text-dim);
  font-weight: 700;
  text-transform: uppercase;
}

.info-val {
  font-size: 1.1rem;
  font-weight: 700;
  color: #FFFFFF;
}

.bst-box {
  display: flex;
  justify-content: space-between;
  padding: 12px 16px;
  background: rgba(56, 189, 248, 0.1);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 10px;
  margin-bottom: 14px;
  font-weight: 700;
}

.bst-val {
  color: var(--accent-cyan);
  font-size: 1.15rem;
}

.stat-bars-wrap {
  padding: 16px;
}

.abilities-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ability-card {
  padding: 14px;
}

.ability-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}

.ability-name {
  font-weight: 700;
  color: #FFFFFF;
}

.ability-badge {
  font-size: 0.68rem;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
}

.badge-normal {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
}

.badge-hidden {
  background: rgba(168, 85, 247, 0.2);
  color: #c084fc;
}

.ability-desc {
  font-size: 0.84rem;
  color: var(--text-muted);
}

.forms-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.form-card {
  padding: 14px;
}

.form-title {
  font-weight: 700;
  margin-bottom: 6px;
}

.form-aspects {
  font-size: 0.75rem;
  color: var(--text-dim);
  margin-top: 6px;
}

.evo-chain {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.evo-step {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
}

.evo-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--accent-cyan);
}

.arrow-symbol {
  font-size: 20px;
}

.arrow-req {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.snack-card {
  padding: 14px 18px;
  border-color: rgba(245, 158, 11, 0.3);
  background: rgba(245, 158, 11, 0.05);
}

.snack-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 700;
}

.snack-title {
  color: #fbbf24;
}

.snack-recipe {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.snack-pill {
  padding: 3px 10px;
  border-radius: 9999px;
  background: rgba(245, 158, 11, 0.2);
  color: #fde68a;
  font-size: 0.75rem;
  font-weight: 600;
}

.snack-desc {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.spawns-list, .specimens-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.spawn-card, .specimen-card {
  padding: 14px;
}

.spawn-header, .specimen-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}

.spawn-rarity {
  color: var(--accent-green);
}

.empty-tab {
  text-align: center;
  padding: 32px 0;
  color: var(--text-dim);
}

.text-cyan { color: var(--accent-cyan); }
.text-emerald { color: var(--accent-green); }
.font-bold { font-weight: 700; }
.font-mono { font-family: var(--font-mono); }
.mt-2 { margin-top: 8px; }
.mt-4 { margin-top: 16px; }
.mb-2 { margin-bottom: 8px; }
.mb-4 { margin-bottom: 16px; }
.uppercase { text-transform: uppercase; }
</style>
