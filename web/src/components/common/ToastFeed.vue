<script setup lang="ts">
import { useMinecraftLive } from '../../composables/useMinecraftLive'

const { toasts, removeToast } = useMinecraftLive()
</script>

<template>
  <div class="toast-container">
    <transition-group name="toast">
      <div 
        v-for="toast in toasts" 
        :key="toast.id" 
        class="toast-card glass-panel"
        :class="toast.type"
      >
        <div class="toast-glow"></div>
        <div class="toast-content">
          <div class="toast-icon">
            <span v-if="toast.type === 'capture'">🔴</span>
            <span v-else-if="toast.type === 'evolution'">✨</span>
            <span v-else>ℹ️</span>
          </div>
          <div class="toast-body">
            <div class="toast-title">{{ toast.title }}</div>
            <div class="toast-subtitle">{{ toast.subtitle }}</div>
          </div>
          <button class="toast-close" @click="removeToast(toast.id)">✕</button>
        </div>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-container {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 380px;
  pointer-events: none;
}

.toast-card {
  pointer-events: auto;
  position: relative;
  overflow: hidden;
  padding: 14px 18px;
  border-radius: 14px;
  background: rgba(18, 24, 38, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
}

.toast-card.capture {
  border-color: rgba(34, 197, 94, 0.4);
}
.toast-card.capture .toast-glow {
  background: radial-gradient(circle at top left, rgba(34, 197, 94, 0.25), transparent 70%);
}

.toast-card.evolution {
  border-color: rgba(245, 158, 11, 0.4);
}
.toast-card.evolution .toast-glow {
  background: radial-gradient(circle at top left, rgba(245, 158, 11, 0.25), transparent 70%);
}

.toast-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}

.toast-content {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
}

.toast-icon {
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toast-body {
  flex: 1;
}

.toast-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: #FFFFFF;
}

.toast-subtitle {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin-top: 2px;
}

.toast-close {
  background: transparent;
  border: none;
  color: var(--text-dim);
  font-size: 14px;
  cursor: pointer;
  padding: 4px;
  transition: color 0.2s ease;
}

.toast-close:hover {
  color: #FFFFFF;
}

/* Animations transition */
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(50px) scale(0.9);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.9);
}
</style>
