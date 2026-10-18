<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { back as navigateBack } from '@/utils'
defineProps<{ title: string; subtitle?: string; back?: boolean }>()
</script>
<template>
  <view class="page-header">
    <view class="header-row">
      <button v-if="back" class="back-button" aria-label="返回" @click="navigateBack">
        <AppIcon name="arrow-left" :size="22" />
      </button>
      <view class="header-copy">
        <text class="header-title">{{ title }}</text>
        <text v-if="subtitle" class="header-subtitle">{{ subtitle }}</text>
        <slot />
      </view>
      <view class="header-actions"><slot name="right" /></view>
    </view>
  </view>
</template>
<style scoped>
.page-header {
  padding: calc(var(--status-bar-height, 0px) + 23px) 20px 20px;
}
.header-row {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 43px;
}
.header-copy {
  flex: 1;
  min-width: 0;
}
.header-title {
  display: block;
  font-size: calc(23px * var(--font-scale, 1));
  font-weight: 600;
  letter-spacing: 0.5px;
  line-height: 1.35;
}
.header-subtitle {
  display: block;
  font-size: calc(12px * var(--font-scale, 1));
  color: #89958d;
  margin-top: 6px;
}
.header-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.back-button {
  width: 36px;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  background: transparent;
  margin: 0;
  padding: 0;
  flex-shrink: 0;
} /* #ifdef MP-WEIXIN */
.header-actions {
  margin-right: 76px;
}
.page-header {
  padding-top: calc(var(--status-bar-height, 20px) + 10px);
} /* #endif */
@media (max-width: 350px) {
  .page-header {
    padding-left: 16px;
    padding-right: 16px;
  }
  .header-title {
    font-size: calc(21px * var(--font-scale, 1));
  }
}
</style>
