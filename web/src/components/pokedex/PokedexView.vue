<script setup lang="ts">
import { onMounted } from 'vue'
import { usePokedexStore } from '../../stores/pokedexStore'
import { usePokemonSprites } from '../../composables/usePokemonSprites'
import TypeBadge from '../common/TypeBadge.vue'

const pokedexStore = usePokedexStore()
const { getSpriteUrl, getFallbackSpriteUrl } = usePokemonSprites()

const allTypes = [
  'Normal', 'Fire', 'Water', 'Electric', 'Grass', 'Ice',
  'Fighting', 'Poison', 'Ground', 'Flying', 'Psychic', 'Bug',
  'Rock', 'Ghost', 'Dragon', 'Dark', 'Steel', 'Fairy'
]

onMounted(() => {
  if (pokedexStore.pokemonList.length === 0) {
    pokedexStore.fetchPokedex()
  }
})
</script>

<template>
  <div class="pokedex-view">
    <!-- Filter and Search Bar -->
    <div class="glass-panel filter-panel">
      <!-- Search row -->
      <div class="search-row">
        <div class="search-input-wrap">
          <span class="search-icon">🔍</span>
          <input 
            v-model="pokedexStore.searchQuery" 
            class="glass-input search-box" 
            placeholder="Rechercher par nom (Pikachu), numéro (#25)..." 
          />
          <button 
            v-if="pokedexStore.searchQuery" 
            class="clear-btn" 
            @click="pokedexStore.searchQuery = ''"
          >
            ✕
          </button>
        </div>

        <!-- Status Filter Pills -->
        <div class="status-filters">
          <button 
            class="glass-btn" 
            :class="{ active: pokedexStore.selectedStatus === 'ALL' }"
            @click="pokedexStore.selectedStatus = 'ALL'"
          >
            Tous ({{ pokedexStore.pokemonList.length }})
          </button>
          <button 
            class="glass-btn caught-btn" 
            :class="{ active: pokedexStore.selectedStatus === 'CAUGHT' }"
            @click="pokedexStore.selectedStatus = 'CAUGHT'"
          >
            ✔ Capturés ({{ pokedexStore.pokemonList.filter(p => p.status === 'CAUGHT').length }})
          </button>
          <button 
            class="glass-btn seen-btn" 
            :class="{ active: pokedexStore.selectedStatus === 'ENCOUNTERED' }"
            @click="pokedexStore.selectedStatus = 'ENCOUNTERED'"
          >
            👁 Vus ({{ pokedexStore.pokemonList.filter(p => p.status === 'ENCOUNTERED').length }})
          </button>
          <button 
            class="glass-btn missing-btn" 
            :class="{ active: pokedexStore.selectedStatus === 'MISSING' }"
            @click="pokedexStore.selectedStatus = 'MISSING'"
          >
            ✕ Manquants ({{ pokedexStore.pokemonList.filter(p => p.status === 'MISSING').length }})
          </button>
        </div>

        <!-- Sort Select -->
        <div class="sort-box">
          <select v-model="pokedexStore.sortBy" class="glass-input sort-select">
            <option value="DEX">N° National</option>
            <option value="NAME">Nom Alphabétique</option>
            <option value="OWNED">Quantité Possédée</option>
          </select>
        </div>
      </div>

      <!-- Type Pills Row -->
      <div class="types-row">
        <div class="types-label">Types :</div>
        <div class="types-list">
          <button 
            v-for="t in allTypes" 
            :key="t"
            class="type-filter-btn"
            :class="[
              `type-${t.toLowerCase()}`, 
              { selected: pokedexStore.selectedTypes.includes(t.toLowerCase()) }
            ]"
            @click="pokedexStore.toggleTypeFilter(t.toLowerCase())"
          >
            {{ t }}
          </button>
          <button 
            v-if="pokedexStore.selectedTypes.length > 0"
            class="clear-types-btn"
            @click="pokedexStore.selectedTypes = []"
          >
            Réinitialiser types
          </button>
        </div>
      </div>

      <!-- Generation Pills Row -->
      <div class="gen-row">
        <div class="gen-label">Générations :</div>
        <div class="gen-list">
          <button 
            class="glass-btn gen-btn" 
            :class="{ active: pokedexStore.selectedGeneration === null }"
            @click="pokedexStore.selectedGeneration = null"
          >
            Toutes
          </button>
          <button 
            v-for="gen in 9" 
            :key="gen" 
            class="glass-btn gen-btn"
            :class="{ active: pokedexStore.selectedGeneration === gen }"
            @click="pokedexStore.selectedGeneration = pokedexStore.selectedGeneration === gen ? null : gen"
          >
            Gen {{ gen }}
          </button>
        </div>
      </div>
    </div>

    <!-- Pokemon Grid -->
    <div class="grid-container">
      <div v-if="pokedexStore.isLoading" class="loading-state">
        <div class="spinner"></div>
        <p>Synchronisation avec Cobblemon...</p>
      </div>

      <div v-else-if="pokedexStore.filteredPokemon.length === 0" class="empty-state glass-panel">
        <span class="empty-icon">🔎</span>
        <h3>Aucun Pokémon correspondant</h3>
        <p>Essayez de modifier vos filtres ou votre recherche.</p>
        <button class="glass-btn" @click="pokedexStore.clearFilters()">Réinitialiser les filtres</button>
      </div>

      <div v-else class="pokemon-grid">
        <div 
          v-for="p in pokedexStore.filteredPokemon" 
          :key="p.speciesId"
          class="pokemon-card glass-card"
          :class="[
            `status-${p.status.toLowerCase()}`,
            { 'has-evo': p.hasPendingEvolution }
          ]"
          @click="pokedexStore.openDetail(p.speciesId)"
        >
          <!-- Shiny / Pending Evo Badge -->
          <div class="card-badges">
            <span v-if="p.hasPendingEvolution" class="badge-evo" title="Prêt à évoluer !">⚡ Évo</span>
            <span v-if="p.isLegendary" class="badge-legendary">👑 Légendaire</span>
          </div>

          <!-- Dex Number -->
          <div class="dex-num font-mono">#{{ String(p.nationalDexNumber).padStart(4, '0') }}</div>

          <!-- Animated Sprite -->
          <div class="sprite-wrapper">
            <img 
              :src="getSpriteUrl(p.name, p.nationalDexNumber)" 
              :alt="p.displayName"
              class="pokemon-sprite"
              loading="lazy"
              @error="($event.target as HTMLImageElement).src = getFallbackSpriteUrl(p.nationalDexNumber)"
            />
          </div>

          <!-- Pokemon Info -->
          <div class="pokemon-name">{{ p.displayName }}</div>
          
          <!-- Element Types -->
          <div class="types-box">
            <TypeBadge :type="p.primaryType" />
            <TypeBadge v-if="p.secondaryType" :type="p.secondaryType" />
          </div>

          <!-- Ownership / Status Footer -->
          <div class="card-footer">
            <span v-if="p.status === 'CAUGHT'" class="status-tag caught">
              ✔ Capturé <span v-if="p.ownedCount > 0" class="owned-tag">({{ p.ownedCount }})</span>
            </span>
            <span v-else-if="p.status === 'ENCOUNTERED'" class="status-tag encountered">
              👁 Vu
            </span>
            <span v-else class="status-tag missing">
              ✕ Manquant
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pokedex-view {
  padding: 0 16px 32px 16px;
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

.status-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.caught-btn.active {
  background: rgba(34, 197, 94, 0.2);
  border-color: #22c55e;
}

.seen-btn.active {
  background: rgba(56, 189, 248, 0.2);
  border-color: #38bdf8;
}

.missing-btn.active {
  background: rgba(244, 63, 94, 0.2);
  border-color: #f43f5e;
}

.sort-select {
  padding: 8px 12px;
  cursor: pointer;
}

.types-row, .gen-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.types-label, .gen-label {
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--text-dim);
  min-width: 90px;
}

.types-list, .gen-list {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.type-filter-btn {
  padding: 3px 8px;
  border-radius: 9999px;
  font-size: 0.7rem;
  font-weight: 700;
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #FFFFFF;
  cursor: pointer;
  opacity: 0.55;
  transition: all 0.2s ease;
}

.type-filter-btn.selected {
  opacity: 1;
  transform: scale(1.08);
  box-shadow: 0 0 12px rgba(255, 255, 255, 0.4);
  border-color: #FFFFFF;
}

.clear-types-btn {
  font-size: 0.72rem;
  background: transparent;
  border: none;
  color: var(--accent-rose);
  cursor: pointer;
  text-decoration: underline;
  margin-left: 6px;
}

.gen-btn {
  padding: 4px 10px;
  font-size: 0.78rem;
}

/* Pokemon Grid Layout */
.pokemon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}

.pokemon-card {
  position: relative;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  cursor: pointer;
  user-select: none;
}

.pokemon-card.status-missing {
  opacity: 0.65;
  filter: grayscale(40%);
}

.pokemon-card.status-missing:hover {
  opacity: 0.95;
  filter: grayscale(0%);
}

.pokemon-card.has-evo {
  border-color: rgba(34, 197, 94, 0.4);
  box-shadow: 0 0 16px rgba(34, 197, 94, 0.2);
}

.card-badges {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.badge-evo {
  font-size: 0.65rem;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(34, 197, 94, 0.25);
  color: #4ade80;
  border: 1px solid #22c55e;
}

.badge-legendary {
  font-size: 0.62rem;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(245, 158, 11, 0.25);
  color: #fbbf24;
  border: 1px solid #f59e0b;
}

.dex-num {
  position: absolute;
  top: 8px;
  right: 10px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-dim);
}

.sprite-wrapper {
  width: 90px;
  height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 8px 0;
}

.pokemon-sprite {
  max-width: 85px;
  max-height: 85px;
  image-rendering: pixelated;
  transition: transform 0.2s ease;
}

.pokemon-card:hover .pokemon-sprite {
  transform: scale(1.12);
}

.pokemon-name {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.types-box {
  display: flex;
  gap: 4px;
  margin-bottom: 10px;
}

.card-footer {
  width: 100%;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding-top: 8px;
  margin-top: auto;
}

.status-tag {
  font-size: 0.72rem;
  font-weight: 600;
}

.status-tag.caught {
  color: #4ade80;
}

.status-tag.encountered {
  color: #38bdf8;
}

.status-tag.missing {
  color: var(--text-dim);
}

.owned-tag {
  color: var(--accent-cyan);
  font-weight: 700;
}

.empty-state {
  padding: 48px 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.empty-icon {
  font-size: 48px;
}
</style>
