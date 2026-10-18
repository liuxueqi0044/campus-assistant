<script setup lang="ts">
import { computed } from 'vue'
import { state } from '@/state'
import { go, money } from '@/utils'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
const props = defineProps<{ query: string; category: string }>()
const items = computed(() =>
  state.errands.filter(
    (item) =>
      item.campusId === state.campusId &&
      item.status === 'OPEN' &&
      (props.category === '全部' || !props.category || item.category === props.category) &&
      (!props.query.trim() ||
        `${item.title} ${item.pickup} ${item.destination} ${item.category}`
          .toLowerCase()
          .includes(props.query.trim().toLowerCase())),
  ),
)
</script>

<template>
  <view class="errand-feed">
    <view class="feed-note">
      <AppIcon name="time" :size="16" />
      <text>顺路帮个忙，也让彼此轻松一点</text>
    </view>
    <view
      v-for="item in items"
      :key="item.id"
      class="errand-card"
      hover-class="card-pressed"
      @click="go(`/pages-errand/detail?id=${item.id}`)"
    >
      <view class="card-top">
        <text class="kind">{{ item.category }}</text>
        <text class="due">{{ item.deadline }}</text>
      </view>
      <view class="card-heading">
        <text class="order-title">{{ item.title }}</text>
        <view class="reward">
          <text class="currency">¥</text>
          <text>{{ money(item.rewardFen) }}</text>
        </view>
      </view>
      <view class="route">
        <view class="route-item">
          <view class="route-dot start" />
          <text class="route-label">取</text>
          <text class="route-place">{{ item.pickup }}</text>
        </view>
        <view class="route-item">
          <view class="route-dot end" />
          <text class="route-label">送</text>
          <text class="route-place">{{ item.destination }}</text>
        </view>
      </view>
      <view class="card-bottom">
        <text class="caption muted">{{ item.weight }} · 接单报酬</text>
        <view class="detail-link">
          <text>看看详情</text>
          <AppIcon name="arrow-right" :size="14" />
        </view>
      </view>
    </view>
    <EmptyState
      v-if="!items.length"
      title="暂时没有合适的跑腿"
      description="试试其他分类，或发布一个需要帮忙的小任务。"
      icon="time"
    />
    <view v-if="items.length" class="list-end">只展示当前校区的待接任务</view>
  </view>
</template>

<style scoped>
.feed-note {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #738178;
  font-size: calc(13px * var(--font-scale, 1));
  margin: 3px 0 14px;
}
.errand-card {
  background: #fff;
  border: 1px solid #e8ede9;
  border-radius: 10px;
  padding: 17px 16px;
  margin-bottom: 12px;
}
.card-pressed {
  background: #fafcfb;
}
.card-top,
.card-heading,
.card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.kind {
  color: #087e58;
  background: #edf6f0;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: calc(12px * var(--font-scale, 1));
}
.due {
  color: #768078;
  font-size: calc(12px * var(--font-scale, 1));
  text-align: right;
}
.card-heading {
  margin-top: 11px;
  align-items: flex-start;
}
.order-title {
  font-size: calc(17px * var(--font-scale, 1));
  font-weight: 600;
  line-height: 1.5;
  flex: 1;
  min-width: 0;
}
.reward {
  color: #ad7540;
  white-space: nowrap;
  font-size: calc(23px * var(--font-scale, 1));
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.currency {
  font-size: calc(13px * var(--font-scale, 1));
  margin-right: 2px;
}
.route {
  margin: 16px 0 14px;
  position: relative;
}
.route:before {
  content: '';
  position: absolute;
  left: 3px;
  top: 12px;
  height: 25px;
  border-left: 1px dashed #d3dcd6;
}
.route-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 29px;
}
.route-dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  z-index: 1;
}
.start {
  background: #07855d;
}
.end {
  background: #bd945f;
}
.route-label {
  font-size: calc(12px * var(--font-scale, 1));
  color: #8a938d;
}
.route-place {
  font-size: calc(14px * var(--font-scale, 1));
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.card-bottom {
  padding-top: 12px;
  border-top: 1px solid #f0f2ef;
}
.detail-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #07855d;
  font-size: calc(13px * var(--font-scale, 1));
}
.list-end {
  text-align: center;
  color: #929b95;
  font-size: calc(12px * var(--font-scale, 1));
  padding: 15px 0 10px;
}
</style>
