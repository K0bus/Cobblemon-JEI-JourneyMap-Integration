<script setup lang="ts">
import { useMinecraftLive } from '../../composables/useMinecraftLive'

const { connectionStatus, playerStatus } = useMinecraftLive()

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {})
  } else {
    document.exitFullscreen().catch(() => {})
  }
}
</script>

<template>
  <header class="glass-panel header-container">
    <div class="brand-section">
      <div class="logo-box">
        <span class="logo-icon">⚡</span>
      </div>
      <div>
        <h1 class="brand-title">COBBLEMON <span class="brand-subtitle">COMPANION</span></h1>
        <p class="brand-tagline">Console d'Exploration & Dex en Direct</p>
      </div>
    </div>

    <!-- Live Status & Player Badges -->
    <div class="player-info-section" v-if="playerStatus">
      <div class="info-badge">
        <span class="badge-label">DRESSEUR</span>
        <span class="badge-val font-bold">{{ playerStatus.playerName }}</span>
      </div>
      <div class="info-badge">
        <span class="badge-label">DIMENSION</span>
        <span class="badge-val text-cyan">{{ playerStatus.dimension.replace('minecraft:', '') }}</span>
      </div>
      <div class="info-badge">
        <span class="badge-label">BIOME</span>
        <span class="badge-val text-emerald">{{ playerStatus.biome.replace('minecraft:', '').replace('cobblemon:', '') }}</span>
      </div>
      <div class="info-badge">
        <span class="badge-label">COORDONNÉES</span>
        <span class="badge-val text-muted font-mono">{{ playerStatus.posX }}, {{ playerStatus.posY }}, {{ playerStatus.posZ }}</span>
      </div>
    </div>

    <!-- Pokedex Progress & Live Badge -->
    <div class="actions-section">
      <div class="progress-box" v-if="playerStatus">
        <div class="progress-text">
          <span>Pokédex</span>
          <span class="progress-count text-cyan font-bold">{{ playerStatus.pokedexCaught }} / {{ playerStatus.pokedexTotal }} ({{ Math.round(playerStatus.pokedexPercentage) }}%)</span>
        </div>
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: `${playerStatus.pokedexPercentage}%` }"></div>
        </div>
      </div>

      <!-- Live Connection Pill -->
      <div class="status-pill" :class="connectionStatus.toLowerCase()">
        <span class="status-dot" :class="{ 'pulse-green': connectionStatus === 'CONNECTED' }"></span>
        <span class="status-text">
          {{ connectionStatus === 'CONNECTED' ? 'EN DIRECT' : connectionStatus === 'CONNECTING' ? 'CONNEXION...' : 'DÉCONNECTÉ' }}
        </span>
      </div>

      <!-- Fullscreen Toggle Button -->
      <button class="glass-btn icon-btn" @click="toggleFullscreen" title="Plein écran">
        ⛶
      </button>
    </div>
  </header>
</template>

<style scoped>
.header-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  margin: 16px;
  gap: 20px;
}

.brand-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-box {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.2), rgba(168, 85, 247, 0.2));
  border: 1px solid rgba(56, 189, 248, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 16px rgba(56, 189, 248, 0.3);
}

.logo-icon {
  font-size: 22px;
}

.brand-title {
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #FFFFFF;
}

.brand-subtitle {
  color: var(--accent-cyan);
}

.brand-tagline {
  font-size: 0.75rem;
  color: var(--text-dim);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.player-info-section {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(14, 18, 28, 0.6);
  padding: 6px 14px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.info-badge {
  display: flex;
  flex-direction: column;
}

.badge-label {
  font-size: 0.62rem;
  color: var(--text-dim);
  font-weight: 600;
  letter-spacing: 0.06em;
}

.badge-val {
  font-size: 0.84rem;
}

.text-cyan { color: var(--accent-cyan); }
.text-emerald { color: var(--accent-green); }
.text-muted { color: var(--text-muted); }
.font-bold { font-weight: 700; }
.font-mono { font-family: var(--font-mono); }

.actions-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.progress-box {
  width: 170px;
}

.progress-text {
  display: flex;
  justify-content: space-between;
  font-size: 0.72rem;
  margin-bottom: 4px;
  color: var(--text-muted);
}

.progress-track {
  width: 100%;
  height: 6px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent-cyan), var(--accent-green));
  transition: width 0.4s ease;
  box-shadow: 0 0 8px rgba(56, 189, 248, 0.6);
}

.status-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-pill.connected {
  border-color: rgba(34, 197, 94, 0.4);
  color: #4ade80;
}
.status-pill.connected .status-dot {
  background: #22c55e;
}

.status-pill.connecting {
  border-color: rgba(245, 158, 11, 0.4);
  color: #fbbf24;
}
.status-pill.connecting .status-dot {
  background: #f59e0b;
}

.status-pill.disconnected {
  border-color: rgba(244, 63, 94, 0.4);
  color: #fb7185;
}
.status-pill.disconnected .status-dot {
  background: #f43f5e;
}

.icon-btn {
  padding: 8px 12px;
  font-size: 1.1rem;
}
</style>
