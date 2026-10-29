<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  stats: Record<string, number>
  maxStat?: number
  label?: string
  isIv?: boolean
}>()

const max = computed(() => props.maxStat || (props.isIv ? 31 : 252))

const statDisplayNames: Record<string, string> = {
  hp: 'PV',
  attack: 'Attaque',
  defence: 'Défense',
  special_attack: 'Att. Spé',
  special_defence: 'Déf. Spé',
  speed: 'Vitesse'
}

function getColor(val: number): string {
  if (props.isIv) {
    if (val === 31) return 'linear-gradient(90deg, #10b981, #059669)'
    if (val >= 25) return 'linear-gradient(90deg, #3b82f6, #2563eb)'
    if (val >= 15) return 'linear-gradient(90deg, #f59e0b, #d97706)'
    return 'linear-gradient(90deg, #ef4444, #dc2626)'
  }
  return 'linear-gradient(90deg, #38bdf8, #6366f1)'
}
</script>

<template>
  <div class="stat-container">
    <div v-if="label" class="stat-group-label">{{ label }}</div>
    <div class="stat-grid">
      <div v-for="(val, key) in stats" :key="key" class="stat-row">
        <span class="stat-name">{{ statDisplayNames[key] || key }}</span>
        <div class="stat-bar-track">
          <div 
            class="stat-bar-fill" 
            :style="{ 
              width: `${Math.min(100, (val / max) * 100)}%`,
              background: getColor(val)
            }"
          ></div>
        </div>
        <span class="stat-value font-mono" :class="{ 'perfect-iv': isIv && val === 31 }">
          {{ val }}<span v-if="isIv && val === 31" class="star">★</span>
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-group-label {
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-dim);
}

.stat-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.8rem;
}

.stat-name {
  width: 65px;
  color: var(--text-muted);
}

.stat-bar-track {
  flex: 1;
  height: 8px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 4px;
  overflow: hidden;
}

.stat-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
}

.stat-value {
  width: 40px;
  text-align: right;
  color: var(--text-main);
  font-weight: 600;
}

.perfect-iv {
  color: #34d399;
  font-weight: 700;
  text-shadow: 0 0 6px rgba(52, 211, 153, 0.5);
}

.star {
  font-size: 0.75rem;
  margin-left: 2px;
}
</style>
