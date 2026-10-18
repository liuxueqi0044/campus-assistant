<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { watch } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
onLaunch(() => {
  try {
    const prefs = uni.getStorageSync('campus-preferences-v1')
    if (prefs) {
      state.fontScale = prefs.fontScale === 1.125 ? 1.125 : 1
      state.notifications = prefs.notifications !== false
      if (['campus', 'timetable', 'messages', 'me'].includes(prefs.defaultTab))
        state.defaultTab = prefs.defaultTab
    }
  } catch {
    /* preference failures never prevent opening the app */
  }
  api.refresh().catch(() => {})
})
let firstShow = true
onShow((options) => {
  if (!firstShow) return
  firstShow = false
  if ((!options?.path || options.path === 'pages/index/index') && state.defaultTab !== 'campus') {
    const paths = {
      timetable: '/pages/timetable/index',
      messages: '/pages/messages/index',
      me: '/pages/me/index',
    }
    setTimeout(() => uni.reLaunch({ url: paths[state.defaultTab as keyof typeof paths] }), 0)
  }
})
watch(
  () => [state.fontScale, state.defaultTab, state.notifications],
  () => {
    try {
      uni.setStorageSync('campus-preferences-v1', {
        fontScale: state.fontScale,
        defaultTab: state.defaultTab,
        notifications: state.notifications,
      })
    } catch {}
  },
)
</script>
<style lang="scss">
@import '@/styles/theme.scss';
</style>
