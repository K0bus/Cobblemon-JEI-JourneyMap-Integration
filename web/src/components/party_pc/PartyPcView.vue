<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { usePartyPcStore } from '../../stores/partyPcStore'
import { usePokedexStore } from '../../stores/pokedexStore'
import { usePokemonSprites } from '../../composables/usePokemonSprites'
import TypeBadge from '../common/TypeBadge.vue'
import StatHexagon from '../common/StatHexagon.vue'
import type { OwnedPokemon } from '../../types'

const partyStore = usePartyPcStore()
const pokedexStore = usePokedexStore()
const { getSpriteUrl, getFallbackSpriteUrl, handleImgError } = usePokemonSprites()

onMounted(() => {
  partyStore.fetchAll()
})

const activeSubTab = ref<'PARTY' | 'PC'>('PARTY')
const selectedPokemon = ref<OwnedPokemon | null>(null)
const pcSearchQuery = ref('')
const isSearchFocused = ref(false)

function selectPokemon(p: OwnedPokemon | null) {
  selectedPokemon.value = p
}

function getHpPercentage(mon: OwnedPokemon): number {
  if (!mon.maxHp || mon.maxHp <= 0) return 100
  return Math.min(100, Math.max(0, Math.round((mon.currentHp / mon.maxHp) * 100)))
}

function getHpColor(pct: number): string {
  if (pct > 50) return '#22c55e'
  if (pct > 20) return '#f59e0b'
  return '#ef4444'
}

const partyOccupiedCount = computed(() => partyStore.party.filter(p => p !== null).length)

interface IndexedPcPokemon {
  pokemon: OwnedPokemon
  boxIndex: number
  boxName: string
  slotIndex: number
}

// Aplatir tous les Pokémon de toutes les boîtes PC pour la recherche
const allIndexedPcPokemons = computed<IndexedPcPokemon[]>(() => {
  const list: IndexedPcPokemon[] = []
  for (const box of partyStore.pcBoxes) {
    for (let slot = 0; slot < box.pokemons.length; slot++) {
      const mon = box.pokemons[slot]
      if (mon) {
        list.push({
          pokemon: mon,
          boxIndex: box.boxIndex,
          boxName: box.boxName,
          slotIndex: slot
        })
      }
    }
  }
  return list
})

// Résultats de recherche fuzzy avec suggestions
const fuzzySearchResults = computed<IndexedPcPokemon[]>(() => {
  const q = pcSearchQuery.value.trim().toLowerCase()
  if (!q) return []
  return allIndexedPcPokemons.value.filter(item => {
    const p = item.pokemon
    const nameFr = p.displayName.toLowerCase()
    const nameEn = (p.speciesName || '').toLowerCase()
    const dex = p.nationalDexNumber?.toString() || ''
    const ability = (p.ability || '').toLowerCase()
    const itemHeld = (p.heldItem || '').toLowerCase()
    const nature = (p.nature || '').toLowerCase()
    const isShinyMatch = p.isShiny && (q.includes('shiny') || q.includes('chroma'))

    return nameFr.includes(q) ||
           nameEn.includes(q) ||
           dex.includes(q) ||
           ability.includes(q) ||
           itemHeld.includes(q) ||
           nature.includes(q) ||
           isShinyMatch ||
           `lv.${p.level}`.includes(q) ||
           `niv.${p.level}`.includes(q)
  }).slice(0, 10)
})

function selectFuzzyResult(match: IndexedPcPokemon) {
  partyStore.selectedBoxIndex = match.boxIndex
  selectedPokemon.value = match.pokemon
  isSearchFocused.value = false
}

function isMatchingCurrentSearch(mon: OwnedPokemon | null): boolean {
  if (!pcSearchQuery.value.trim() || !mon) return false
  const q = pcSearchQuery.value.trim().toLowerCase()
  return mon.displayName.toLowerCase().includes(q) ||
         (mon.speciesName || '').toLowerCase().includes(q) ||
         (mon.nationalDexNumber?.toString() || '').includes(q) ||
         (mon.ability || '').toLowerCase().includes(q) ||
         (mon.heldItem || '').toLowerCase().includes(q) ||
         (mon.isShiny && (q.includes('shiny') || q.includes('chroma')))
}
</script>

<template>
  <div class="party-pc-view">
    <!-- Sub-tab Navigation Header -->
    <div class="glass-panel nav-bar">
      <div class="tabs-list">
        <button 
          class="nav-tab-btn" 
          :class="{ active: activeSubTab === 'PARTY' }"
          @click="activeSubTab = 'PARTY'"
        >
          <span class="tab-icon">🎒</span>
          <span class="tab-label">Équipe Active ({{ partyOccupiedCount }}/6)</span>
        </button>

        <button 
          class="nav-tab-btn" 
          :class="{ active: activeSubTab === 'PC' }"
          @click="activeSubTab = 'PC'"
        >
          <span class="tab-icon">🖥</span>
          <span class="tab-label">Boîtes PC ({{ partyStore.pcBoxes.length }} boîtes)</span>
        </button>
      </div>

      <div class="actions">
        <button class="glass-btn refresh-btn" @click="partyStore.fetchAll" :disabled="partyStore.isLoading">
          🔄 Actualiser
        </button>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="partyStore.isLoading" class="loading-state">
      <div class="spinner"></div>
      <p>Chargement des données de l'équipe et du PC...</p>
    </div>

    <!-- MAIN CONTENT -->
    <div v-else class="view-content">
      <!-- 1. PARTY VIEW (6 Cards) -->
      <div v-if="activeSubTab === 'PARTY'" class="party-container">
        <div class="party-grid">
          <div 
            v-for="(mon, slotIdx) in partyStore.party" 
            :key="slotIdx"
            class="party-slot-card glass-card"
            :class="{ 'empty-slot': !mon, 'selected-slot': mon && selectedPokemon?.uuid === mon.uuid }"
            @click="mon && selectPokemon(mon)"
          >
            <!-- Slot Index -->
            <div class="slot-number font-mono">SLOT #{{ slotIdx + 1 }}</div>

            <!-- Empty slot state -->
            <div v-if="!mon" class="empty-slot-content">
              <span class="empty-pokeball">⚪</span>
              <span class="empty-label">Emplacement libre</span>
            </div>

            <!-- Populated slot -->
            <div v-else class="slot-body">
              <!-- Top Row: Shiny / Gender / Level -->
              <div class="slot-top-row">
                <span v-if="mon.isShiny" class="shiny-star" title="Chromatique">✨ Shiny</span>
                <span class="gender-symbol" :class="mon.gender.toLowerCase()">
                  {{ mon.gender === 'MALE' ? '♂' : mon.gender === 'FEMALE' ? '♀' : '⚲' }}
                </span>
                <span class="mon-lvl font-mono">Niv. {{ mon.level }}</span>
              </div>

              <!-- Animated Sprite & Name -->
              <div class="slot-center">
                <div class="mon-sprite-box">
                  <img 
                    :src="getSpriteUrl(mon.speciesName || mon.displayName, mon.nationalDexNumber, mon.isShiny)" 
                    class="party-sprite"
                    @error="handleImgError($event, mon.nationalDexNumber, mon.isShiny)"
                  />
                </div>
                <div class="mon-name">{{ mon.displayName }}</div>
                <div class="nature-ability">
                  {{ mon.nature }} • {{ mon.ability }}
                </div>
              </div>

              <!-- HP Bar -->
              <div class="hp-section">
                <div class="hp-label-row">
                  <span class="hp-tag">PV</span>
                  <span class="hp-numbers font-mono">{{ mon.currentHp }} / {{ mon.maxHp }}</span>
                </div>
                <div class="hp-bar-bg">
                  <div 
                    class="hp-bar-fill"
                    :style="{ 
                      width: `${getHpPercentage(mon)}%`,
                      backgroundColor: getHpColor(getHpPercentage(mon))
                    }"
                  ></div>
                </div>
              </div>

              <!-- Held Item -->
              <div class="held-item-box" v-if="mon.heldItem">
                <span class="item-icon">🎒</span>
                <span class="item-name">{{ mon.heldItem }}</span>
              </div>

              <!-- Moves List (4 moves) -->
              <div class="moves-list">
                <div 
                  v-for="(move, mIdx) in mon.moves" 
                  :key="mIdx"
                  class="move-row"
                >
                  <TypeBadge :type="move.type" />
                  <span class="move-name">{{ move.displayName }}</span>
                  <span class="move-pp font-mono" v-if="move.currentPp !== null && move.currentPp !== undefined">
                    {{ move.currentPp }}/{{ move.maxPp }} PP
                  </span>
                </div>
              </div>

              <!-- Card Action Button -->
              <button class="inspect-btn glass-btn" @click.stop="selectPokemon(mon)">
                🔍 Inspecter stats & IVs
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. PC BOXES VIEW (Selector + 30 Slot Grid) -->
      <div v-else-if="activeSubTab === 'PC'" class="pc-container">
        <!-- Box Navigator & Fuzzy Search -->
        <div class="glass-panel box-nav-panel">
          <!-- Fuzzy Search Bar -->
          <div class="pc-search-row">
            <div class="pc-search-box-wrap">
              <span class="search-icon">🔍</span>
              <input 
                v-model="pcSearchQuery" 
                class="glass-input pc-search-input" 
                placeholder="Rechercher dans tout le PC (Pikachu, #25, Shiny, Talent, Niveau...)"
                @focus="isSearchFocused = true"
              />
              <button v-if="pcSearchQuery" class="clear-search-btn" @click="pcSearchQuery = ''">✕</button>

              <!-- Fuzzy Dropdown Suggestions -->
              <div v-if="pcSearchQuery && fuzzySearchResults.length > 0" class="fuzzy-dropdown glass-panel">
                <div class="fuzzy-header">
                  <span>{{ fuzzySearchResults.length }} Pokémon trouvé(s) dans le PC</span>
                </div>
                <div 
                  v-for="item in fuzzySearchResults" 
                  :key="item.pokemon.uuid"
                  class="fuzzy-item"
                  @click="selectFuzzyResult(item)"
                >
                  <img 
                    :src="getSpriteUrl(item.pokemon.speciesName || item.pokemon.displayName, item.pokemon.nationalDexNumber, item.pokemon.isShiny)" 
                    class="fuzzy-mini-sprite"
                    @error="handleImgError($event, item.pokemon.nationalDexNumber, item.pokemon.isShiny)"
                  />
                  <div class="fuzzy-info">
                    <div class="fuzzy-name-row">
                      <span class="fuzzy-name">{{ item.pokemon.displayName }}</span>
                      <span v-if="item.pokemon.isShiny" class="fuzzy-shiny">✨ Shiny</span>
                      <span class="fuzzy-lvl font-mono">Niv. {{ item.pokemon.level }}</span>
                    </div>
                    <div class="fuzzy-loc">
                      📍 <strong>{{ item.boxName }}</strong> • Emplacement #{{ item.slotIndex + 1 }}
                    </div>
                  </div>
                  <div class="fuzzy-action">
                    <span class="fuzzy-jump-pill">Sélectionner ➔</span>
                  </div>
                </div>
              </div>

              <div v-else-if="pcSearchQuery && isSearchFocused" class="fuzzy-dropdown glass-panel empty-fuzzy">
                Aucun Pokémon correspondant dans vos boîtes PC.
              </div>
            </div>
          </div>

          <div class="box-selector-row">
            <button 
              class="box-nav-arrow glass-btn"
              :disabled="partyStore.selectedBoxIndex <= 0"
              @click="partyStore.selectedBoxIndex--"
            >
              ◀
            </button>

            <div class="box-pills-scroll">
              <button 
                v-for="b in partyStore.pcBoxes" 
                :key="b.boxIndex"
                class="box-pill glass-btn"
                :class="{ active: partyStore.selectedBoxIndex === b.boxIndex }"
                @click="partyStore.selectedBoxIndex = b.boxIndex"
              >
                {{ b.boxName }} ({{ b.occupiedSlots }}/{{ b.totalSlots }})
              </button>
            </div>

            <button 
              class="box-nav-arrow glass-btn"
              :disabled="partyStore.selectedBoxIndex >= partyStore.pcBoxes.length - 1"
              @click="partyStore.selectedBoxIndex++"
            >
              ▶
            </button>
          </div>
        </div>

        <!-- 30-slot PC Grid -->
        <div class="pc-grid-wrap glass-panel" v-if="partyStore.currentBox">
          <div class="box-title-header">
            <h3>{{ partyStore.currentBox.boxName }}</h3>
            <span class="box-occupancy font-mono">
              {{ partyStore.currentBox.occupiedSlots }} Pokémon stocké(s) sur {{ partyStore.currentBox.totalSlots }}
            </span>
          </div>

          <div class="pc-grid">
            <div 
              v-for="(mon, slotIdx) in partyStore.currentBox.pokemons" 
              :key="slotIdx"
              class="pc-slot"
              :class="{ 
                'pc-slot-empty': !mon, 
                'pc-slot-active': mon && selectedPokemon?.uuid === mon.uuid,
                'pc-slot-shiny': mon?.isShiny,
                'pc-slot-match': isMatchingCurrentSearch(mon),
                'pc-slot-dimmed': pcSearchQuery && mon && !isMatchingCurrentSearch(mon)
              }"
              @click="mon && selectPokemon(mon)"
            >
              <template v-if="mon">
                <span v-if="mon.isShiny" class="mini-shiny">✨</span>
                <img 
                  :src="getSpriteUrl(mon.speciesName || mon.displayName, mon.nationalDexNumber, mon.isShiny)" 
                  class="pc-mini-sprite"
                  loading="lazy"
                  @error="handleImgError($event, mon.nationalDexNumber, mon.isShiny)"
                />
                <span class="pc-mon-lvl font-mono">Lv.{{ mon.level }}</span>
                <span class="pc-mon-name">{{ mon.displayName }}</span>
              </template>
              <template v-else>
                <span class="slot-dot">·</span>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- INSPECTOR MODAL / SIDE PANEL -->
      <div v-if="selectedPokemon" class="inspector-backdrop" @click="selectedPokemon = null">
        <div class="inspector-card glass-panel" @click.stop>
          <div class="inspector-header">
            <div class="inspector-title-row">
              <span v-if="selectedPokemon.isShiny" class="inspector-shiny">✨ SHINY</span>
              <h2 class="inspector-name">{{ selectedPokemon.displayName }}</h2>
              <span class="font-mono inspector-lvl">Niveau {{ selectedPokemon.level }}</span>
            </div>
            <button class="close-btn" @click="selectedPokemon = null">✕</button>
          </div>

          <div class="inspector-body">
            <!-- Left Column: Visual & Info -->
            <div class="inspector-col-left">
              <div class="inspector-sprite-box">
                <img 
                  :src="getSpriteUrl(selectedPokemon.speciesName || selectedPokemon.displayName, selectedPokemon.nationalDexNumber, selectedPokemon.isShiny)" 
                  class="inspector-sprite"
                  @error="handleImgError($event, selectedPokemon.nationalDexNumber, selectedPokemon.isShiny)"
                />
              </div>

              <div class="metadata-grid">
                <div class="meta-item">
                  <span class="label">Emplacement</span>
                  <span class="val">{{ selectedPokemon.location }}</span>
                </div>
                <div class="meta-item">
                  <span class="label">Genre</span>
                  <span class="val">{{ selectedPokemon.gender }}</span>
                </div>
                <div class="meta-item">
                  <span class="label">Nature</span>
                  <span class="val">{{ selectedPokemon.nature }}</span>
                </div>
                <div class="meta-item">
                  <span class="label">Talent</span>
                  <span class="val">{{ selectedPokemon.ability }}</span>
                </div>
                <div class="meta-item" v-if="selectedPokemon.heldItem">
                  <span class="label">Objet tenu</span>
                  <span class="val">{{ selectedPokemon.heldItem }}</span>
                </div>
              </div>

              <!-- Pokedex quick link -->
              <button 
                class="glass-btn pokedex-link-btn"
                @click="pokedexStore.openDetail(selectedPokemon.speciesId)"
              >
                📖 Ouvrir la fiche Pokédex complète
              </button>
            </div>

            <!-- Right Column: IVs & EVs radar / moves -->
            <div class="inspector-col-right">
              <!-- IV / EV Section -->
              <div class="stats-table-box glass-card">
                <h4>Potentiel Génétique (IVs & EVs)</h4>
                <div class="stat-rows">
                  <div 
                    v-for="statKey in ['hp', 'attack', 'defence', 'special_attack', 'special_defence', 'speed']" 
                    :key="statKey"
                    class="stat-bar-row"
                  >
                    <span class="stat-name uppercase">{{ statKey.replace('_', ' ') }}</span>
                    <div class="stat-meters">
                      <div class="meter-bar iv-meter">
                        <div 
                          class="meter-fill iv-fill" 
                          :style="{ width: `${((selectedPokemon.ivs[statKey] || 0) / 31) * 100}%` }"
                        ></div>
                        <span class="meter-text font-mono">IV: {{ selectedPokemon.ivs[statKey] || 0 }}/31</span>
                      </div>
                      <div class="meter-bar ev-meter">
                        <div 
                          class="meter-fill ev-fill" 
                          :style="{ width: `${((selectedPokemon.evs[statKey] || 0) / 252) * 100}%` }"
                        ></div>
                        <span class="meter-text font-mono">EV: {{ selectedPokemon.evs[statKey] || 0 }}/252</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Moves Section -->
              <div class="moves-panel glass-card">
                <h4>Capacités actuelles</h4>
                <div class="moves-grid-2x2">
                  <div 
                    v-for="(m, idx) in selectedPokemon.moves" 
                    :key="idx" 
                    class="inspector-move-card"
                  >
                    <div class="move-card-top">
                      <TypeBadge :type="m.type" />
                      <span class="move-pp-badge font-mono">{{ m.currentPp !== null ? `${m.currentPp}/${m.maxPp} PP` : `${m.maxPp} PP` }}</span>
                    </div>
                    <div class="inspector-move-title">{{ m.displayName }}</div>
                    <div class="inspector-move-meta font-mono" v-if="m.power">
                      Puissance: {{ m.power }} | Précision: {{ m.accuracy ? `${m.accuracy}%` : '-' }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.party-pc-view {
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

.tabs-list {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nav-tab-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: var(--text-dim);
  cursor: pointer;
  transition: all 0.2s ease;
  font-weight: 700;
  font-size: 0.9rem;
}

.nav-tab-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-main);
}

.nav-tab-btn.active {
  background: rgba(99, 102, 241, 0.25);
  border-color: var(--accent-indigo);
  color: #FFFFFF;
  box-shadow: 0 0 16px rgba(99, 102, 241, 0.25);
}

.refresh-btn {
  padding: 8px 14px;
  font-size: 0.82rem;
}

/* Party 6 cards grid */
.party-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

.party-slot-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  position: relative;
  min-height: 380px;
  cursor: pointer;
}

.party-slot-card.empty-slot {
  display: flex;
  align-items: center;
  justify-content: center;
  border-style: dashed;
  opacity: 0.4;
  cursor: default;
}

.selected-slot {
  border-color: var(--accent-cyan);
  box-shadow: 0 0 20px rgba(6, 182, 212, 0.3);
}

.slot-number {
  position: absolute;
  top: 10px;
  right: 12px;
  font-size: 0.7rem;
  color: var(--text-dim);
}

.empty-slot-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.empty-pokeball {
  font-size: 2.2rem;
  opacity: 0.5;
}

.empty-label {
  font-size: 0.85rem;
  color: var(--text-dim);
}

.slot-top-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.shiny-star {
  font-size: 0.72rem;
  font-weight: 800;
  color: #fef08a;
  background: rgba(234, 179, 8, 0.2);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid rgba(234, 179, 8, 0.4);
}

.gender-symbol {
  font-weight: bold;
  font-size: 0.9rem;
}

.gender-symbol.male { color: #60a5fa; }
.gender-symbol.female { color: #f472b6; }
.gender-symbol.genderless { color: var(--text-dim); }

.mon-lvl {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--accent-cyan);
  margin-left: auto;
  margin-right: 40px;
}

.slot-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 8px 0;
}

.party-sprite {
  width: 90px;
  height: 90px;
  image-rendering: pixelated;
}

.mon-name {
  font-size: 1.15rem;
  font-weight: 800;
  margin-top: 4px;
}

.nature-ability {
  font-size: 0.78rem;
  color: var(--text-dim);
}

.hp-section {
  margin: 10px 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hp-label-row {
  display: flex;
  justify-content: space-between;
  font-size: 0.75rem;
}

.hp-tag {
  font-weight: 800;
  color: var(--text-dim);
}

.hp-numbers {
  font-weight: 700;
}

.hp-bar-bg {
  width: 100%;
  height: 7px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 9999px;
  overflow: hidden;
}

.hp-bar-fill {
  height: 100%;
  border-radius: 9999px;
  transition: width 0.3s ease, background-color 0.3s ease;
}

.held-item-box {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  background: rgba(255, 255, 255, 0.04);
  padding: 4px 8px;
  border-radius: 6px;
  margin-bottom: 10px;
}

.moves-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.move-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(0, 0, 0, 0.2);
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 0.78rem;
}

.move-name {
  font-weight: 600;
  flex: 1;
}

.move-pp {
  font-size: 0.7rem;
  color: var(--text-dim);
}

.inspect-btn {
  margin-top: auto;
  width: 100%;
  padding: 8px;
  font-size: 0.78rem;
  font-weight: 700;
}

/* PC Box view */
.box-nav-panel {
  padding: 12px 16px;
  margin-bottom: 16px;
}

.box-selector-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.box-nav-arrow {
  padding: 8px 14px;
  font-size: 0.9rem;
}

.box-pills-scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  flex: 1;
  padding-bottom: 4px;
}

.box-pill {
  padding: 6px 14px;
  font-size: 0.8rem;
  white-space: nowrap;
}

.box-pill.active {
  background: rgba(99, 102, 241, 0.3);
  border-color: var(--accent-indigo);
  color: #FFFFFF;
}

.pc-grid-wrap {
  padding: 20px;
}

.box-title-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  padding-bottom: 10px;
}

.box-title-header h3 {
  margin: 0;
  font-size: 1.2rem;
}

.box-occupancy {
  font-size: 0.8rem;
  color: var(--text-dim);
}

.pc-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}

.pc-slot {
  height: 90px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  transition: all 0.2s ease;
  user-select: none;
}

.pc-slot:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.25);
  transform: translateY(-2px);
}

.pc-slot-empty {
  cursor: default;
  opacity: 0.3;
}

.pc-slot-empty:hover {
  transform: none;
  background: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.08);
}

.pc-slot-active {
  border-color: var(--accent-cyan);
  background: rgba(6, 182, 212, 0.15);
  box-shadow: 0 0 14px rgba(6, 182, 212, 0.25);
}

.pc-slot-shiny {
  border-color: rgba(234, 179, 8, 0.4);
}

.mini-shiny {
  position: absolute;
  top: 4px;
  left: 6px;
  font-size: 0.65rem;
}

.pc-mini-sprite {
  max-width: 50px;
  max-height: 50px;
  image-rendering: pixelated;
}

.pc-mon-lvl {
  font-size: 0.65rem;
  color: var(--text-dim);
}

.pc-mon-name {
  font-size: 0.68rem;
  font-weight: 700;
  max-width: 90%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.slot-dot {
  color: var(--text-dim);
  font-size: 1.5rem;
}

/* Inspector Modal */
.inspector-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(8px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.inspector-card {
  width: 100%;
  max-width: 850px;
  max-height: 90vh;
  overflow-y: auto;
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.inspector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 14px;
  margin-bottom: 20px;
}

.inspector-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.inspector-name {
  margin: 0;
  font-size: 1.5rem;
}

.inspector-lvl {
  font-size: 1rem;
  color: var(--accent-cyan);
}

.inspector-shiny {
  font-size: 0.75rem;
  font-weight: 800;
  color: #fef08a;
  background: rgba(234, 179, 8, 0.2);
  padding: 2px 8px;
  border-radius: 6px;
  border: 1px solid rgba(234, 179, 8, 0.4);
}

.close-btn {
  background: transparent;
  border: none;
  color: var(--text-dim);
  font-size: 1.4rem;
  cursor: pointer;
}

.close-btn:hover {
  color: #FFFFFF;
}

.inspector-body {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
}

.inspector-col-left {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.inspector-sprite-box {
  width: 100%;
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.inspector-sprite {
  max-width: 150px;
  max-height: 150px;
  image-rendering: pixelated;
}

.metadata-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.82rem;
}

.meta-item {
  display: flex;
  justify-content: space-between;
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 6px;
}

.meta-item .label {
  color: var(--text-dim);
}

.meta-item .val {
  font-weight: 700;
}

.pokedex-link-btn {
  padding: 8px;
  font-size: 0.8rem;
  font-weight: 700;
}

.inspector-col-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-table-box, .moves-panel {
  padding: 14px;
}

.stats-table-box h4, .moves-panel h4 {
  margin: 0 0 12px 0;
  font-size: 0.9rem;
  color: var(--text-dim);
  text-transform: uppercase;
}

.stat-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-bar-row {
  display: grid;
  grid-template-columns: 120px 1fr;
  align-items: center;
  gap: 10px;
  font-size: 0.75rem;
}

.stat-name {
  font-weight: 700;
  color: var(--text-dim);
}

.stat-meters {
  display: flex;
  gap: 8px;
}

.meter-bar {
  flex: 1;
  height: 18px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.meter-fill {
  height: 100%;
  border-radius: 4px;
}

.iv-fill {
  background: linear-gradient(90deg, #38bdf8, #6366f1);
}

.ev-fill {
  background: linear-gradient(90deg, #34d399, #10b981);
}

.meter-text {
  position: absolute;
  top: 50%;
  left: 6px;
  transform: translateY(-50%);
  font-size: 0.68rem;
  font-weight: 700;
  color: #FFFFFF;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.8);
}

.moves-grid-2x2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.inspector-move-card {
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 8px 10px;
}

.move-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.move-pp-badge {
  font-size: 0.68rem;
  color: var(--text-dim);
}

.inspector-move-title {
  font-weight: 700;
  font-size: 0.85rem;
}

.inspector-move-meta {
  font-size: 0.68rem;
  color: var(--text-dim);
  margin-top: 2px;
}

/* PC Search & Fuzzy Suggestions */
.pc-search-row {
  margin-bottom: 14px;
}

.pc-search-box-wrap {
  position: relative;
  width: 100%;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-dim);
  font-size: 14px;
  pointer-events: none;
}

.pc-search-input {
  width: 100%;
  padding-left: 38px;
  padding-right: 36px;
  font-size: 0.88rem;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.pc-search-input:focus {
  border-color: var(--accent-cyan);
  box-shadow: 0 0 16px rgba(6, 182, 212, 0.25);
}

.clear-search-btn {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  background: transparent;
  border: none;
  color: var(--text-dim);
  cursor: pointer;
  font-size: 14px;
}

.clear-search-btn:hover {
  color: #FFFFFF;
}

.fuzzy-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  max-height: 380px;
  overflow-y: auto;
  z-index: 500;
  background: rgba(15, 23, 42, 0.95);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(56, 189, 248, 0.3);
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.7);
  border-radius: 12px;
  padding: 8px 0;
}

.fuzzy-header {
  padding: 6px 14px;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--accent-cyan);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  text-transform: uppercase;
}

.fuzzy-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  cursor: pointer;
  transition: background 0.15s ease;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
}

.fuzzy-item:hover {
  background: rgba(56, 189, 248, 0.15);
}

.fuzzy-mini-sprite {
  width: 38px;
  height: 38px;
  image-rendering: pixelated;
  flex-shrink: 0;
}

.fuzzy-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.fuzzy-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.fuzzy-name {
  font-weight: 700;
  font-size: 0.88rem;
  color: #FFFFFF;
}

.fuzzy-shiny {
  font-size: 0.65rem;
  color: #facc15;
  background: rgba(234, 179, 8, 0.2);
  padding: 1px 4px;
  border-radius: 4px;
}

.fuzzy-lvl {
  font-size: 0.72rem;
  color: var(--accent-cyan);
}

.fuzzy-loc {
  font-size: 0.72rem;
  color: var(--text-dim);
}

.fuzzy-loc strong {
  color: var(--text-main);
}

.fuzzy-jump-pill {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--accent-cyan);
  padding: 4px 8px;
  border-radius: 6px;
  background: rgba(6, 182, 212, 0.12);
  border: 1px solid rgba(6, 182, 212, 0.3);
}

.empty-fuzzy {
  padding: 14px 18px;
  font-size: 0.82rem;
  color: var(--text-dim);
  text-align: center;
}

/* PC Slot Matching Highlight */
.pc-slot-match {
  border-color: #38bdf8 !important;
  box-shadow: 0 0 14px rgba(56, 189, 248, 0.4) !important;
  background: rgba(56, 189, 248, 0.12) !important;
  animation: match-pulse 2s infinite ease-in-out;
}

@keyframes match-pulse {
  0%, 100% { transform: scale(1); box-shadow: 0 0 14px rgba(56, 189, 248, 0.4); }
  50% { transform: scale(1.04); box-shadow: 0 0 20px rgba(56, 189, 248, 0.7); }
}

.pc-slot-dimmed {
  opacity: 0.35;
}
</style>
