<script setup lang="ts">
import { computed, ref } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { go, requireLogin } from '@/utils'
import type { Service } from '@/types'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import BottomNav from '@/components/BottomNav.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import MarketFeed from '@/features/market/MarketFeed.vue'
import ErrandFeed from '@/features/errand/ErrandFeed.vue'
import ActivityFeed from '@/features/activity/ActivityFeed.vue'
const query = ref(''),
  category = ref('全部'),
  campusOpen = ref(false),
  switching = ref(false),
  error = ref('')
const school = computed(() => state.campuses.find((c) => c.id === state.campusId))
const services: { id: Service; name: string; icon: string }[] = [
  { id: 'market', name: '二手', icon: 'shop' },
  { id: 'errand', name: '跑腿', icon: 'time' },
  { id: 'activity', name: '活动', icon: 'flag' },
]
const categories = computed(() =>
  state.service === 'market'
    ? ['全部', '教材书籍', '数码配件', '宿舍生活', '运动户外']
    : state.service === 'errand'
      ? ['全部', '快递代取', '校内代送', '其他']
      : ['全部', '运动', '桌游', '电影', '户外'],
)
const current = computed(() => services.find((s) => s.id === state.service)!)
const course = computed(() =>
  state.courses.find((c) => c.day === 4 && c.weeks.includes(state.week)),
)
function select(id: Service) {
  state.service = id
  category.value = '全部'
  query.value = ''
}
async function switchCampus(id: string) {
  switching.value = true
  error.value = ''
  try {
    await api.switchCampus(id)
    campusOpen.value = false
    query.value = ''
    category.value = '全部'
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    switching.value = false
  }
}
function publish() {
  if (requireLogin()) go(`/pages-${state.service}/publish`)
}
</script>
<template>
  <view class="page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="校园" subtitle="让校园里的小事，简单一点">
      <template #right>
        <button class="campus-button" @click="campusOpen = true">
          <AppIcon name="location" :size="15" />
          <text>{{ school?.shortName || '选择校区' }}</text>
          <AppIcon name="arrow-down" :size="12" />
        </button>
      </template>
    </PageHeader>
    <view class="content">
      <view class="search-box">
        <AppIcon name="search" :size="19" />
        <input
          v-model="query"
          :placeholder="`搜一搜${current.name}，就在你身边`"
          confirm-type="search"
          aria-label="搜索校园内容"
        />
        <button v-if="query" class="clear-search" aria-label="清除搜索" @click="query = ''">
          <AppIcon name="close" :size="16" />
        </button>
      </view>
      <view class="today-card" @click="go('/pages/timetable/index')">
        <view class="today-icon"><AppIcon name="calendar" :size="21" /></view>
        <view class="today-copy">
          <text class="today-label">{{ state.demo ? '演示课表 · 周四' : '我的课表' }}</text>
          <text class="today-title">
            {{ course ? `${course.title} · ${course.room}` : '安排好上课，也留一点生活' }}
          </text>
        </view>
        <AppIcon name="arrow-right" :size="15" color="#9ca89e" />
      </view>
      <view class="service-bar">
        <view class="service-tabs">
          <button
            v-for="s in services"
            :key="s.id"
            :class="['service-tab', { active: state.service === s.id }]"
            @click="select(s.id)"
          >
            {{ s.name }}
          </button>
        </view>
        <button class="publish" @click="publish">
          <AppIcon name="add" :size="16" />
          发布
        </button>
      </view>
      <scroll-view class="category-scroll" scroll-x>
        <view class="categories">
          <button
            v-for="c in categories"
            :key="c"
            :class="['category', { chosen: category === c }]"
            @click="category = c"
          >
            {{ c }}
          </button>
        </view>
      </scroll-view>
      <DemoNotice />
      <view v-if="state.networkState === 'loading'" class="loading-bar">正在加载校园内容…</view>
      <view v-else-if="state.networkState === 'error'" class="form-error">
        <text>{{ state.networkError }}</text>
        <button class="text-button" @click="api.refresh().catch(() => {})">重新加载</button>
      </view>
      <template v-else>
        <MarketFeed v-if="state.service === 'market'" :query="query" :category="category" />
        <ErrandFeed v-else-if="state.service === 'errand'" :query="query" :category="category" />
        <ActivityFeed v-else :query="query" :category="category" />
      </template>
    </view>
    <BottomNav active="campus" />
    <wd-popup
      v-model="campusOpen"
      position="bottom"
      :close-on-click-modal="!switching"
      safe-area-inset-bottom
    >
      <view class="campus-sheet">
        <view class="row between">
          <text class="title">选择你的校区</text>
          <button class="icon-button" aria-label="关闭校区选择" @click="campusOpen = false">
            <AppIcon name="close" />
          </button>
        </view>
        <text class="hint">浏览和发布的内容会跟随校区切换。</text>
        <button
          v-for="c in state.campuses"
          :key="c.id"
          class="campus-option"
          :disabled="switching || !c.enabled"
          @click="switchCampus(c.id)"
        >
          <view>
            <text>{{ c.school }} · {{ c.name }}</text>
            <text class="caption muted">
              {{ state.demo ? '虚构学校 · 演示校区' : c.enabled ? '已开放' : '暂未开放' }}
            </text>
          </view>
          <AppIcon v-if="c.id === state.campusId" name="check" color="#07855d" />
        </button>
        <text v-if="error" class="form-error">{{ error }}</text>
      </view>
    </wd-popup>
  </view>
</template>
<style scoped>
.campus-button {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 0;
  padding: 8px 0 8px 8px;
  background: transparent;
  font-size: calc(13px * var(--font-scale, 1));
  color: #657b68;
}
.search-box {
  height: 46px;
  background: #eef1ed;
  border-radius: 9px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 13px;
  color: #96a096;
}
.search-box input {
  flex: 1;
  min-width: 0;
  font-size: calc(14px * var(--font-scale, 1));
  height: 46px;
}
.clear-search {
  background: transparent;
  padding: 0;
  display: flex;
  margin: 0;
}
.today-card {
  display: flex;
  align-items: center;
  gap: 11px;
  background: #edf4ee;
  border: 1px solid #e2ece2;
  border-radius: 9px;
  margin-top: 17px;
  padding: 12px 13px;
  cursor: pointer;
}
.today-icon {
  width: 37px;
  height: 37px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e0ecdf;
  color: #68805f;
  border-radius: 8px;
}
.today-copy {
  flex: 1;
  min-width: 0;
}
.today-label {
  display: block;
  font-size: calc(11px * var(--font-scale, 1));
  color: #85917e;
  margin-bottom: 3px;
}
.today-title {
  font-size: calc(13px * var(--font-scale, 1));
  color: #4c664e;
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.service-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24px;
  border-bottom: 1px solid #e6ece5;
}
.service-tabs {
  display: flex;
  gap: 26px;
}
.service-tab {
  background: transparent;
  font-size: calc(18px * var(--font-scale, 1));
  color: #8b948d;
  padding: 0 0 13px;
  border-radius: 0;
  margin: 0;
  position: relative;
}
.service-tab.active {
  color: #273c2e;
  font-weight: 600;
}
.service-tab.active:after {
  content: '';
  position: absolute;
  left: 25%;
  right: 25%;
  bottom: 0;
  height: 3px;
  border-radius: 2px;
  background: #07855d;
  display: block;
  top: auto;
  width: auto;
}
.publish {
  display: flex;
  align-items: center;
  gap: 3px;
  background: transparent;
  color: #07855d;
  font-size: calc(13px * var(--font-scale, 1));
  margin: 0 0 10px;
  padding: 5px 0 5px 10px;
}
.category-scroll {
  width: 100%;
  margin: 15px 0 7px;
  white-space: nowrap;
}
.categories {
  display: flex;
  gap: 8px;
  padding-bottom: 5px;
}
.category {
  display: inline-flex;
  flex-shrink: 0;
  background: transparent;
  color: #7d897f;
  font-size: calc(12px * var(--font-scale, 1));
  padding: 6px 10px;
  margin: 0;
  border-radius: 6px;
}
.category.chosen {
  background: #e5eee5;
  color: #437450;
  font-weight: 500;
}
.campus-sheet {
  padding: 19px 24px 30px;
  max-width: 520px;
  margin: auto;
}
.campus-option {
  background: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 0;
  text-align: left;
  border-bottom: 1px solid #edf0eb;
  border-radius: 0;
  font-size: calc(16px * var(--font-scale, 1));
}
.campus-option text {
  display: block;
}
.campus-option .caption {
  margin-top: 6px;
}
@media (max-width: 350px) {
  .service-tabs {
    gap: 23px;
  }
  .campus-button {
    font-size: calc(12px * var(--font-scale, 1));
  }
}
</style>
