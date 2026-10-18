<script setup lang="ts">
import { computed } from 'vue'
const props = withDefaults(defineProps<{ name: string; size?: number; color?: string }>(), {
  size: 20,
})
const aliases: Record<string, string> = {
  'message-circle': 'chat',
  messages: 'chat',
  heart: 'heart',
  plus: 'add',
  grid: 'apps',
  home: 'home',
  time: 'time',
  notification: 'notification',
  book: 'read',
  shield: 'secured',
  camera: 'camera',
  'chevron-down': 'arrow-down',
  'chevron-right': 'arrow-right',
  'check-circle': 'check-circle',
  upload: 'upload',
  'calendar-days': 'calendar',
  'user-round': 'user',
  'layout-grid': 'apps',
  'map-pin': 'location',
  'shopping-bag': 'shop',
  package: 'goods',
  leaf: 'eco',
  'arrow-up-right': 'arrow-up',
  search: 'search',
  image: 'image',
  check: 'check',
  close: 'close',
}
Object.assign(aliases, { bag: 'shop', grid: 'app', 'layout-grid': 'app', leaf: 'read' })
const icon = computed(() => aliases[props.name] || props.name)
</script>
<template>
  <view
    class="app-icon"
    aria-hidden="true"
    :style="{ color: color || 'inherit', fontSize: size + 'px' }"
  >
    <wd-icon :name="icon" :size="size" :color="color" />
  </view>
</template>
<style scoped>
.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  line-height: 1;
}
</style>
