<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>
<template>
  <view class="section-title">
    <text>{{ title }}</text>
    <text v-if="subtitle" class="section-subtitle">{{ subtitle }}</text>
    <slot />
  </view>
</template>
<style scoped>
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 20px 0 12px;
  font-size: calc(16px * var(--font-scale, 1));
  font-weight: 500;
}
.section-subtitle {
  font-size: calc(12px * var(--font-scale, 1));
  font-weight: 400;
  color: #89958d;
}
</style>
