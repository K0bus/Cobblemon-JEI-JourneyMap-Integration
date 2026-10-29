<script setup lang="ts">
defineProps<{
  type: string
}>()
</script>

<template>
  <span :class="['type-badge', `type-${type.toLowerCase()}`]">
    {{ type }}
  </span>
</template>
