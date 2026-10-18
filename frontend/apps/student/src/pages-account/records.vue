<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { state } from '@/state'
import { go, money } from '@/utils'
import type { Activity, Errand, Product } from '@/types'
import PageHeader from '@/components/PageHeader.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppIcon from '@/components/AppIcon.vue'

type RecordType = 'published' | 'favorites' | 'errands' | 'activities'
type RecordItem = {
  id: string
  title: string
  subtitle: string
  status: string
  image?: string
  amount?: string
  route: string
  label: string
  kind: 'market' | 'errand' | 'activity'
  active: boolean
}
const type = ref<RecordType>('published')
const filter = ref<'all' | 'active' | 'done'>('all')
const tabs: { key: RecordType; label: string }[] = [
  { key: 'published', label: '我的发布' },
  { key: 'favorites', label: '收藏' },
  { key: 'errands', label: '跑腿订单' },
  { key: 'activities', label: '我的活动' },
]
const titles: Record<RecordType, string> = {
  published: '我的发布',
  favorites: '我的收藏',
  errands: '跑腿订单',
  activities: '我的活动',
}
onLoad((options) => {
  if (tabs.some((tab) => tab.key === options?.type)) type.value = options?.type as RecordType
})
function productItem(p: Product): RecordItem {
  return {
    id: p.id,
    title: p.title,
    subtitle: `${p.condition} · ${p.location}`,
    status:
      p.status === 'AVAILABLE' && p.type === 'WANTED'
        ? '求购中'
        : (
            {
              AVAILABLE: '在售',
              RESERVED: '已预约',
              SOLD: '已售出',
              PENDING_REVIEW: '待审核',
            } as const
          )[p.status],
    image: p.image,
    amount: `¥${money(p.priceFen)}`,
    route: `/pages-market/detail?id=${encodeURIComponent(p.id)}`,
    label: p.type === 'WANTED' ? '求购' : '闲置',
    kind: 'market',
    active: p.status !== 'SOLD',
  }
}
function activityItem(a: Activity, published = false): RecordItem {
  return {
    id: a.id,
    title: a.title,
    subtitle: `${a.date} ${a.time} · ${a.location}`,
    status:
      a.status === 'CANCELLED'
        ? '活动已取消'
        : published
          ? a.status === 'PENDING_REVIEW'
            ? '待审核'
            : '报名中'
          : (
              {
                NONE: '未报名',
                CONFIRMED: '已报名',
                WAITLISTED: `候补 ${a.waitlistPosition || ''}`.trim(),
                CHECKED_IN: '已签到',
              } as const
            )[a.participantStatus],
    image: a.cover,
    route: `/pages-activity/detail?id=${encodeURIComponent(a.id)}`,
    label: published ? '我发起的活动' : '活动报名',
    kind: 'activity',
    active: a.status !== 'CANCELLED' && a.participantStatus !== 'CHECKED_IN',
  }
}
function errandItem(e: Errand): RecordItem {
  return {
    id: e.id,
    title: e.title,
    subtitle: `${e.pickup} → ${e.destination}`,
    status: (
      {
        OPEN: '待接单',
        ACCEPTED: '已接单',
        IN_PROGRESS: '配送中',
        DELIVERED: '待确认',
        COMPLETED: '已完成',
        CANCELLED: '已取消',
        WAITING_PAYMENT: '待支付',
      } as const
    )[e.status],
    amount: `¥${money(e.requesterId === state.user.id ? e.rewardFen + e.feeFen : e.rewardFen)}`,
    route: `/pages-errand/detail?id=${encodeURIComponent(e.id)}`,
    label: e.requesterId === state.user.id ? '我发起的' : '我接的',
    kind: 'errand',
    active: !['COMPLETED', 'CANCELLED'].includes(e.status),
  }
}
const allItems = computed<RecordItem[]>(() => {
  if (!state.user.loggedIn) return []
  if (type.value === 'published')
    return [
      ...state.products.filter((p) => p.ownerId === state.user.id).map(productItem),
      ...state.activities
        .filter((a) => a.ownerId === state.user.id)
        .map((a) => activityItem(a, true)),
    ]
  if (type.value === 'favorites')
    return state.products.filter((p) => state.favorites.includes(p.id)).map(productItem)
  if (type.value === 'errands')
    return state.errands
      .filter((e) => e.requesterId === state.user.id || e.runnerId === state.user.id)
      .map(errandItem)
  return state.activities.filter((a) => a.participantStatus !== 'NONE').map((a) => activityItem(a))
})
const items = computed(() =>
  allItems.value.filter(
    (item) => filter.value === 'all' || (filter.value === 'active' ? item.active : !item.active),
  ),
)
function selectTab(value: RecordType) {
  type.value = value
  filter.value = 'all'
}
function explore() {
  if (type.value === 'published') {
    go('/pages-market/publish')
    return
  }
  state.service =
    type.value === 'errands' ? 'errand' : type.value === 'activities' ? 'activity' : 'market'
  uni.reLaunch({ url: '/pages/index/index' })
}
</script>

<template>
  <view class="page detail-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader :title="titles[type]" subtitle="你的发布与参与，都放在这里" back />
    <view class="content">
      <DemoNotice />
      <template v-if="state.user.loggedIn">
        <view class="record-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            :class="['record-tab', { selected: type === tab.key }]"
            @click="selectTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </view>
        <view class="records-toolbar">
          <text class="records-count">
            共 {{ allItems.length }} {{ type === 'errands' ? '笔订单' : '条记录' }}
          </text>
          <view class="record-filters">
            <button :class="{ active: filter === 'all' }" @click="filter = 'all'">全部</button>
            <button :class="{ active: filter === 'active' }" @click="filter = 'active'">
              进行中
            </button>
            <button :class="{ active: filter === 'done' }" @click="filter = 'done'">已结束</button>
          </view>
        </view>
        <view
          v-for="item in items"
          :key="`${item.kind}-${item.id}`"
          class="record-card surface"
          @click="go(item.route)"
        >
          <view class="record-top">
            <text class="record-kind">{{ item.label }}</text>
            <text :class="['record-status', { ended: !item.active }]">{{ item.status }}</text>
          </view>
          <view class="record-main">
            <image v-if="item.image" class="record-image" :src="item.image" mode="aspectFill" />
            <view v-else class="record-placeholder"><AppIcon name="cart" :size="29" /></view>
            <view class="record-copy">
              <text class="record-title">{{ item.title }}</text>
              <text class="record-subtitle">{{ item.subtitle }}</text>
              <view class="record-bottom">
                <text v-if="item.amount" class="record-amount">
                  {{ item.amount }}
                  <text v-if="item.kind === 'errand'" class="amount-label">
                    {{ item.label === '我发起的' ? '总费用' : '报酬' }}
                  </text>
                </text>
                <text v-else class="record-free">免费活动</text>
                <view class="detail-link">
                  <text>查看详情</text>
                  <AppIcon name="arrow-right" :size="13" />
                </view>
              </view>
            </view>
          </view>
        </view>
        <template v-if="!items.length">
          <EmptyState
            :title="
              allItems.length
                ? '暂时没有这类记录'
                : type === 'published'
                  ? '还没有发布内容'
                  : type === 'favorites'
                    ? '收藏夹等你来填'
                    : type === 'errands'
                      ? '还没有跑腿订单'
                      : '还没有报名活动'
            "
            :description="
              allItems.length
                ? '试试切换到全部记录。'
                : type === 'published'
                  ? '把闲置分享给需要的同学。'
                  : type === 'favorites'
                    ? '遇到喜欢的闲置，点一下收藏。'
                    : type === 'errands'
                      ? '你发起的任务和接下的订单都会在这里。'
                      : '找到喜欢的活动，再给生活一点新鲜感。'
            "
            :icon="type === 'activities' ? 'calendar' : type === 'favorites' ? 'star' : 'bag'"
          />
          <button class="btn btn-secondary" @click="allItems.length ? (filter = 'all') : explore()">
            {{
              allItems.length ? '查看全部' : type === 'published' ? '发布一件闲置' : '去校园逛逛'
            }}
          </button>
        </template>
        <view class="records-note">
          <text>记录按当前账号筛选，保留不同校区的参与。</text>
          <text v-if="type === 'errands'">订单完成与款项到账分别记录，请在详情查看。</text>
          <text v-if="state.demo">以上为演示数据，不代表真实交易或报名。</text>
        </view>
      </template>
      <template v-else>
        <EmptyState
          title="登录后查看个人记录"
          description="你的发布、收藏与参与只属于你。"
          icon="user"
        />
        <button class="btn btn-primary" @click="go('/pages-account/login')">前往登录</button>
      </template>
    </view>
  </view>
</template>

<style scoped>
.record-tabs {
  display: flex;
  margin: 20px -2px 0;
  gap: 4px;
  border-bottom: 1px solid var(--color-line);
}
.record-tab {
  flex: 1;
  white-space: nowrap;
  padding: 12px 0;
  margin: 0;
  border-radius: 0;
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.5;
  background: transparent;
  color: #8b9881;
  border-bottom: 2px solid transparent;
}
.record-tab::after {
  border: 0;
}
.record-tab.selected {
  color: var(--color-primary);
  font-weight: 600;
  border-bottom-color: var(--color-primary);
}
.records-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 19px 0 13px;
  gap: 8px;
}
.records-count {
  font-size: calc(11px * var(--font-scale, 1));
  color: #939d89;
}
.record-filters {
  display: flex;
  gap: 3px;
}
.record-filters button {
  font-size: calc(11px * var(--font-scale, 1));
  color: #a0a893;
  background: transparent;
  padding: 6px 8px;
  margin: 0;
  line-height: 1.5;
  min-height: 32px;
}
.record-filters button::after {
  border: 0;
}
.record-filters button.active {
  background: #e9f0e3;
  border-radius: 6px;
  color: #678d59;
}
.record-card {
  border-radius: 12px;
  padding: 14px;
  margin-bottom: 12px;
}
.record-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #edf0e8;
  padding-bottom: 12px;
  margin-bottom: 13px;
}
.record-kind {
  font-size: calc(11px * var(--font-scale, 1));
  color: #89967b;
}
.record-status {
  font-size: calc(11px * var(--font-scale, 1));
  color: #648f56;
}
.record-status.ended {
  color: #9aa58d;
}
.record-main {
  display: flex;
  gap: 12px;
}
.record-image,
.record-placeholder {
  width: 76px;
  height: 83px;
  flex-shrink: 0;
  border-radius: 8px;
  background: #eef1e9;
}
.record-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #839976;
  background: #edf2e6;
}
.record-copy {
  flex: 1;
  min-width: 0;
}
.record-title {
  display: block;
  font-size: calc(15px * var(--font-scale, 1));
  font-weight: 500;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.record-subtitle {
  font-size: calc(11px * var(--font-scale, 1));
  line-height: 1.8;
  display: block;
  color: #99a28d;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 6px;
}
.record-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 12px;
}
.record-amount {
  font-size: calc(17px * var(--font-scale, 1));
  color: #ad7b5c;
  font-weight: 600;
}
.amount-label {
  font-size: calc(9px * var(--font-scale, 1));
  font-weight: 400;
  color: #9aa489;
  margin-left: 5px;
}
.record-free {
  font-size: calc(12px * var(--font-scale, 1));
  color: #859b72;
}
.detail-link {
  font-size: calc(10px * var(--font-scale, 1));
  display: flex;
  align-items: center;
  gap: 3px;
  color: #9aaa8d;
  white-space: nowrap;
}
.records-note {
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-align: center;
  font-size: calc(11px * var(--font-scale, 1));
  color: #a0aa93;
  line-height: 1.8;
  margin: 24px 0 14px;
}
.records-count,
.record-filters button,
.record-kind,
.record-status,
.record-subtitle,
.amount-label,
.detail-link,
.records-note {
  font-size: calc(12px * var(--font-scale, 1));
}
.record-title {
  font-size: calc(16px * var(--font-scale, 1));
}
.record-filters button {
  min-height: 44px;
}
.record-bottom {
  flex-wrap: wrap;
}
</style>
