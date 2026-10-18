<script setup lang="ts">
import { computed } from 'vue'
import { state } from '@/state'
import { go } from '@/utils'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
const props = defineProps<{ query: string; category: string }>()
const items = computed(() =>
  state.activities.filter(
    (item) =>
      item.campusId === state.campusId &&
      item.status === 'OPEN' &&
      (props.category === '全部' || !props.category || item.category === props.category) &&
      (!props.query.trim() ||
        `${item.title} ${item.location} ${item.organizer}`
          .toLowerCase()
          .includes(props.query.trim().toLowerCase())),
  ),
)
const remaining = (capacity: number, occupied: number) => Math.max(0, capacity - occupied)
</script>

<template>
  <view class="activity-feed">
    <view class="feed-note">给课余时间，安排一点喜欢的事</view>
    <view
      v-for="item in items"
      :key="item.id"
      class="activity-card"
      hover-class="card-pressed"
      @click="go(`/pages-activity/detail?id=${item.id}`)"
    >
      <view class="cover-wrap">
        <image class="cover" :src="item.cover" mode="aspectFill" />
        <text class="cover-tag">{{ item.category }}</text>
        <text class="free-tag">免费参加</text>
      </view>
      <view class="card-body">
        <view class="card-title">{{ item.title }}</view>
        <view class="meta">
          <AppIcon name="time" :size="15" />
          <text>{{ item.date }} · {{ item.time }}</text>
        </view>
        <view class="meta">
          <AppIcon name="location" :size="15" />
          <text>{{ item.location }}</text>
        </view>
        <view class="card-bottom">
          <view class="organizer">
            <view class="avatar">{{ item.organizer.slice(0, 1) }}</view>
            <text>{{ item.organizer }}</text>
          </view>
          <text :class="['spots', { full: !remaining(item.capacity, item.occupied) }]">
            {{
              remaining(item.capacity, item.occupied)
                ? `余 ${remaining(item.capacity, item.occupied)} 位`
                : '已满 · 可候补'
            }}
          </text>
        </view>
      </view>
    </view>
    <EmptyState
      v-if="!items.length"
      title="这里还没有活动"
      description="换个分类看看，或者发起一场你期待的相聚。"
      icon="calendar"
    />
    <view v-if="items.length" class="list-end">都是同校区的小小相聚</view>
  </view>
</template>

<style scoped>
.feed-note {
  font-size: calc(13px * var(--font-scale, 1));
  color: #738178;
  margin: 3px 0 14px;
}
.activity-card {
  border: 1px solid #e8ede9;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
  margin-bottom: 17px;
}
.card-pressed {
  opacity: 0.93;
}
.cover-wrap {
  height: 150px;
  position: relative;
  background: #e6efe9;
}
.cover {
  width: 100%;
  height: 100%;
  display: block;
}
.cover-tag,
.free-tag {
  position: absolute;
  top: 12px;
  font-size: calc(12px * var(--font-scale, 1));
  padding: 4px 9px;
  border-radius: 4px;
  line-height: 1.5;
}
.cover-tag {
  left: 12px;
  background: #ffffffed;
  color: #304c3c;
}
.free-tag {
  right: 12px;
  background: #233a2dd9;
  color: #fff;
}
.card-body {
  padding: 15px 16px 12px;
}
.card-title {
  font-size: calc(18px * var(--font-scale, 1));
  font-weight: 600;
  line-height: 1.5;
  margin-bottom: 9px;
}
.meta {
  display: flex;
  gap: 7px;
  align-items: center;
  color: #707b73;
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.6;
  margin-top: 5px;
}
.meta text {
  min-width: 0;
  overflow-wrap: anywhere;
}
.card-bottom {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #eff2ee;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.organizer {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #5e6e63;
  font-size: calc(12px * var(--font-scale, 1));
  min-width: 0;
}
.avatar {
  width: 23px;
  height: 23px;
  border-radius: 50%;
  background: #eff3ea;
  color: #6d8267;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: calc(11px * var(--font-scale, 1));
  flex-shrink: 0;
}
.spots {
  color: #07855d;
  font-size: calc(12px * var(--font-scale, 1));
  white-space: nowrap;
}
.spots.full {
  color: #9a7a42;
}
.list-end {
  padding: 7px 0 12px;
  text-align: center;
  font-size: calc(12px * var(--font-scale, 1));
  color: #939c95;
}
</style>
