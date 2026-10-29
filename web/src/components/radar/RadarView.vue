<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRadarStore } from '../../stores/radarStore'
import { usePokedexStore } from '../../stores/pokedexStore'
import { usePokemonSprites } from '../../composables/usePokemonSprites'

const radarStore = useRadarStore()
const pokedexStore = usePokedexStore()
const { getSpriteUrl, getFallbackSpriteUrl } = usePokemonSprites()

const trackedSuccessMessage = ref<string | null>(null)

onMounted(() => {
  radarStore.fetchRadar()
})

async function onTrack(speciesName: string) {
  const success = await radarStore.trackSpecies(speciesName)
  if (success) {
    trackedSuccessMessage.value = `Guidage actif vers ${speciesName} dans Minecraft !`
    setTimeout(() => {
      trackedSuccessMessage.value = null
    }, 4000)
  }
}
</script>

<template>
  <div class="radar-view">
    <!-- Header banner -->
    <div class="glass-panel nav-bar">
      <div class="title-group">
        <span class="radar-pulse-icon">📡</span>
        <div>
          <h2>Radar de Détection Cobblemon</h2>
          <p class="subtitle">Entités vivantes détectées dans votre zone de chargement</p>
        </div>
      </div>

      <div class="actions">
        <button class="glass-btn refresh-btn" @click="radarStore.fetchRadar">
          🔄 Actualiser radar
        </button>
      </div>
    </div>

    <!-- Alert toast for tracking -->
    <div v-if="trackedSuccessMessage" class="tracking-alert glass-panel">
      🎯 {{ trackedSuccessMessage }}
    </div>

    <!-- Empty or List -->
    <div v-if="radarStore.entities.length === 0" class="empty-state glass-panel">
      <span class="empty-icon">🛰</span>
      <h3>Aucun Pokémon détecté à proximité</h3>
      <p>Aucun Pokémon sauvage n'a été repéré dans le rayon de détection actuel. Déplacez-vous ou patientez un instant.</p>
    </div>

    <div v-else class="radar-grid">
      <div 
        v-for="(ent, idx) in radarStore.entities" 
        :key="idx"
        class="radar-card glass-card"
        :class="{ 
          'is-shiny': ent.isShiny,
          'is-missing': ent.status === 'MISSING',
          'is-caught': ent.status === 'CAUGHT'
        }"
        @click="pokedexStore.openDetail(ent.speciesId)"
      >
        <!-- Top Status Tags -->
        <div class="card-tags">
          <span v-if="ent.isShiny" class="tag shiny-tag">✨ SHINY !</span>
          <span v-if="ent.perfectIvsCount > 0" class="tag perfect-tag">⭐ {{ ent.perfectIvsCount }}x 31 IV</span>
          <span v-if="ent.hasHiddenAbility" class="tag ha-tag">⚡ Talent Caché</span>
          <span v-if="ent.status === 'MISSING'" class="tag missing-tag">NOUVEAU DANS LE DEX</span>
        </div>

        <!-- Animated Sprite -->
        <div class="radar-sprite-box">
          <img 
            :src="getSpriteUrl(ent.displayName, ent.nationalDexNumber, ent.isShiny)" 
            class="radar-sprite"
            @error="($event.target as HTMLImageElement).src = getFallbackSpriteUrl(ent.nationalDexNumber)"
          />
        </div>

        <!-- Name & Level -->
        <div class="mon-info">
          <h3 class="mon-name">{{ ent.displayName }}</h3>
          <span class="mon-lvl font-mono">Niveau {{ ent.level }}</span>
        </div>

        <!-- Proximity / Location Metrics -->
        <div class="metrics-grid">
          <div class="metric-chip">
            <span class="m-label">Distance</span>
            <span class="m-val font-mono">{{ Math.round(ent.distance) }}m</span>
          </div>

          <div class="metric-chip">
            <span class="m-label">Altitude</span>
            <span class="m-val font-mono">
              {{ ent.altitudeDiff >= 0 ? `+${Math.round(ent.altitudeDiff)}m` : `${Math.round(ent.altitudeDiff)}m` }}
            </span>
          </div>
        </div>

        <!-- Action Track Button -->
        <button class="track-btn glass-btn" @click.stop="onTrack(ent.displayName)">
          🎯 Guider le compas
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.radar-view {
  padding: 0 16px 32px 16px;
}

.nav-bar {
  padding: 14px 20px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.title-group {
  display: flex;
  align-items: center;
  gap: 14px;
}

.radar-pulse-icon {
  font-size: 2rem;
  animation: radar-pulse 2s infinite ease-in-out;
}

@keyframes radar-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.15); opacity: 0.7; }
}

.title-group h2 {
  margin: 0;
  font-size: 1.25rem;
}

.subtitle {
  margin: 2px 0 0 0;
  font-size: 0.8rem;
  color: var(--text-dim);
}

.tracking-alert {
  padding: 12px 18px;
  margin-bottom: 16px;
  background: rgba(34, 197, 94, 0.2);
  border-color: #22c55e;
  color: #4ade80;
  font-weight: 700;
  animation: fadeIn 0.3s ease;
}

.radar-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.radar-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 10px;
  cursor: pointer;
  position: relative;
  transition: transform 0.2s ease;
}

.radar-card:hover {
  transform: translateY(-2px);
}

.radar-card.is-shiny {
  border-color: #facc15;
  box-shadow: 0 0 20px rgba(250, 204, 21, 0.25);
}

.card-tags {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

.tag {
  font-size: 0.65rem;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 4px;
}

.shiny-tag {
  background: rgba(234, 179, 8, 0.25);
  color: #fde047;
  border: 1px solid #facc15;
}

.perfect-tag {
  background: rgba(168, 85, 247, 0.25);
  color: #c084fc;
  border: 1px solid #a855f7;
}

.ha-tag {
  background: rgba(244, 63, 94, 0.2);
  color: #fb7185;
}

.missing-tag {
  background: rgba(34, 197, 94, 0.2);
  color: #4ade80;
  border: 1px solid #22c55e;
}

.radar-sprite-box {
  width: 90px;
  height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.radar-sprite {
  max-width: 80px;
  max-height: 80px;
  image-rendering: pixelated;
}

.mon-name {
  font-size: 1.05rem;
  font-weight: 800;
  margin: 0;
}

.mon-lvl {
  font-size: 0.78rem;
  color: var(--accent-cyan);
}

.metrics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  width: 100%;
}

.metric-chip {
  background: rgba(255, 255, 255, 0.04);
  padding: 6px;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
}

.m-label {
  font-size: 0.65rem;
  color: var(--text-dim);
  text-transform: uppercase;
}

.m-val {
  font-size: 0.82rem;
  font-weight: 700;
}

.track-btn {
  width: 100%;
  padding: 8px;
  font-size: 0.78rem;
  font-weight: 700;
  margin-top: 6px;
  background: rgba(99, 102, 241, 0.2);
  border-color: var(--accent-indigo);
}

.track-btn:hover {
  background: rgba(99, 102, 241, 0.4);
}
</style>
