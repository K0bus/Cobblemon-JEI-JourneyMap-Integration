<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import HeaderBar from './components/common/HeaderBar.vue'
import ToastFeed from './components/common/ToastFeed.vue'
import PokedexView from './components/pokedex/PokedexView.vue'
import PokemonDetailModal from './components/pokedex/PokemonDetailModal.vue'
import EvolutionsView from './components/evolutions/EvolutionsView.vue'
import PartyPcView from './components/party_pc/PartyPcView.vue'
import RadarView from './components/radar/RadarView.vue'
import { useMinecraftLive } from './composables/useMinecraftLive'

export type MainTab = 'POKEDEX' | 'EVOLUTIONS' | 'PARTY_PC' | 'RADAR'

const activeTab = ref<MainTab>('POKEDEX')
const { startLiveSync, stopLiveSync } = useMinecraftLive()

onMounted(() => {
  startLiveSync()
})

onUnmounted(() => {
  stopLiveSync()
})
</script>

<template>
  <div class="app-layout">
    <!-- Fixed Top Header -->
    <HeaderBar />

    <!-- Main Navigation Bar -->
    <nav class="glass-panel main-nav">
      <div class="nav-tabs-wrapper">
        <button 
          class="nav-item-btn" 
          :class="{ active: activeTab === 'POKEDEX' }"
          @click="activeTab = 'POKEDEX'"
        >
          <span class="nav-icon">📖</span>
          <span class="nav-text">Pokédex Global</span>
        </button>

        <button 
          class="nav-item-btn" 
          :class="{ active: activeTab === 'EVOLUTIONS' }"
          @click="activeTab = 'EVOLUTIONS'"
        >
          <span class="nav-icon">⚡</span>
          <span class="nav-text">Évolutions & Complétion</span>
        </button>

        <button 
          class="nav-item-btn" 
          :class="{ active: activeTab === 'PARTY_PC' }"
          @click="activeTab = 'PARTY_PC'"
        >
          <span class="nav-icon">🎒</span>
          <span class="nav-text">Équipe & Boîtes PC</span>
        </button>

        <button 
          class="nav-item-btn" 
          :class="{ active: activeTab === 'RADAR' }"
          @click="activeTab = 'RADAR'"
        >
          <span class="nav-icon">📡</span>
          <span class="nav-text">Radar Proximité</span>
        </button>
      </div>
    </nav>

    <!-- Main Dynamic View -->
    <main class="main-content">
      <PokedexView v-if="activeTab === 'POKEDEX'" />
      <EvolutionsView v-else-if="activeTab === 'EVOLUTIONS'" />
      <PartyPcView v-else-if="activeTab === 'PARTY_PC'" />
      <RadarView v-else-if="activeTab === 'RADAR'" />
    </main>

    <!-- Global Modal & Toast notifications -->
    <PokemonDetailModal />
    <ToastFeed />
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-nav {
  margin: 0 16px 20px 16px;
  padding: 8px 12px;
}

.nav-tabs-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
}

.nav-item-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  border-radius: 12px;
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-dim);
  font-weight: 700;
  font-size: 0.92rem;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  white-space: nowrap;
}

.nav-item-btn:hover {
  color: var(--text-main);
  background: rgba(255, 255, 255, 0.06);
}

.nav-item-btn.active {
  color: #FFFFFF;
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.25), rgba(99, 102, 241, 0.25));
  border-color: rgba(56, 189, 248, 0.5);
  box-shadow: 0 0 20px rgba(56, 189, 248, 0.25);
}

.nav-icon {
  font-size: 1.15rem;
}

.main-content {
  flex: 1;
}
</style>
