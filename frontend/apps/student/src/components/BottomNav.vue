<script setup lang="ts">
import { computed } from 'vue'
import { state } from '@/state'
import type { Tab } from '@/types'
import AppIcon from './AppIcon.vue'
const props = defineProps<{ active: Tab }>()
const tabs: { id: Tab; label: string; icon: string; url: string }[] = [
  { id: 'campus', label: '校园', icon: 'home', url: '/pages/index/index' },
  { id: 'timetable', label: '课表', icon: 'calendar', url: '/pages/timetable/index' },
  { id: 'messages', label: '消息', icon: 'chat', url: '/pages/messages/index' },
  { id: 'me', label: '我', icon: 'user', url: '/pages/me/index' },
]
const unread = computed(() =>
  state.user.loggedIn
    ? state.conversations.reduce((n, c) => n + c.unread, 0) +
      state.notices.filter((n) => !n.read).length
    : 0,
)
function navigate(tab: (typeof tabs)[number]) {
  if (props.active !== tab.id) uni.reLaunch({ url: tab.url })
}
</script>
<template>
  <view class="bottom-nav" role="navigation" aria-label="主要功能">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      :class="['nav-item', { selected: active === tab.id }]"
      :aria-label="tab.label"
      :aria-current="active === tab.id ? 'page' : undefined"
      @click="navigate(tab)"
    >
      <view class="nav-icon">
        <AppIcon :name="tab.icon" :size="23" />
        <text v-if="tab.id === 'messages' && unread" class="unread-dot">
          {{ unread > 9 ? '9+' : unread }}
        </text>
      </view>
      <text class="nav-label">{{ tab.label }}</text>
    </button>
  </view>
</template>
<style scoped>
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  max-width: 520px;
  width: 100%;
  display: flex;
  background: #fff;
  border-top: 1px solid #e9eeea;
  padding: 7px 8px calc(9px + env(safe-area-inset-bottom));
  z-index: 40;
}
.nav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 51px;
  padding: 2px 0;
  margin: 0;
  background: transparent;
  color: #929c95;
  border-radius: 0;
  line-height: 1.2;
}
.nav-item.selected {
  color: #07855d;
}
.nav-icon {
  position: relative;
  display: flex;
  align-items: center;
}
.nav-label {
  font-size: calc(11px * var(--font-scale, 1));
  font-weight: 500;
}
.unread-dot {
  position: absolute;
  right: -10px;
  top: -5px;
  font-size: calc(9px * var(--font-scale, 1));
  min-width: 14px;
  height: 14px;
  border-radius: 20px;
  padding: 1px 3px;
  color: #fff;
  background: #c36959;
  text-align: center;
  border: 1px solid white;
}
</style>
