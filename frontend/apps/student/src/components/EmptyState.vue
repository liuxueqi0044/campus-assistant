<script setup lang="ts">
import AppIcon from './AppIcon.vue'
withDefaults(defineProps<{ title: string; description?: string; icon?: string }>(), {
  icon: 'search',
})
</script>
<template>
  <view class="empty-state">
    <view class="empty-icon"><AppIcon :name="icon" :size="32" /></view>
    <text class="empty-title">{{ title }}</text>
    <text v-if="description" class="empty-description">{{ description }}</text>
    <slot />
  </view>
</template>
<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 45px 20px;
  text-align: center;
}
.empty-icon {
  background: #eaf0eb;
  color: #9aada0;
  width: 68px;
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 22px;
  margin-bottom: 19px;
}
.empty-title {
  font-size: calc(16px * var(--font-scale, 1));
  font-weight: 500;
  color: #4e6054;
}
.empty-description {
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.8;
  color: #8b968e;
  margin-top: 9px;
  max-width: 260px;
}
</style>
