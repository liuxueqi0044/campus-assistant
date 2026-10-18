<script setup lang="ts">
import { computed, ref } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { go } from '@/utils'
import type { Notice } from '@/types'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import AppIcon from '@/components/AppIcon.vue'

const filter = ref<'all' | Notice['type']>('all')
const busy = ref(false)
const error = ref('')
const filters: { key: 'all' | Notice['type']; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'errand', label: '跑腿' },
  { key: 'activity', label: '活动' },
  { key: 'system', label: '账号' },
]
const notices = computed(() =>
  state.notices.filter((item) => filter.value === 'all' || item.type === filter.value),
)
const unreadCount = computed(() => state.notices.filter((item) => !item.read).length)
async function markAllRead() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.readNotices()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '更新失败，请重试。'
  } finally {
    busy.value = false
  }
}
function openRelated(notice: Notice) {
  go(
    notice.type === 'errand'
      ? '/pages-account/records?type=errands'
      : notice.type === 'activity'
        ? '/pages-account/records?type=activities'
        : '/pages-account/verify',
  )
}
</script>

<template>
  <view class="page detail-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader
      title="服务通知"
      :subtitle="unreadCount ? `${unreadCount} 条未读更新` : '订单与校园生活的最新进展'"
      back
    >
      <template #right>
        <button
          v-if="state.user.loggedIn"
          class="read-button"
          :disabled="!unreadCount || busy"
          @click="markAllRead"
        >
          {{ busy ? '更新中' : '全部已读' }}
        </button>
      </template>
    </PageHeader>
    <view class="content">
      <DemoNotice />
      <template v-if="state.user.loggedIn">
        <view class="notice-filters">
          <button
            v-for="option in filters"
            :key="option.key"
            :class="['notice-filter', { active: filter === option.key }]"
            @click="filter = option.key"
          >
            {{ option.label }}
          </button>
        </view>
        <view v-if="error" class="form-error section">{{ error }}</view>
        <view v-for="notice in notices" :key="notice.id" class="notice-card surface">
          <view class="notice-top">
            <view :class="['type-icon', notice.type]">
              <AppIcon
                :name="
                  notice.type === 'activity'
                    ? 'calendar'
                    : notice.type === 'errand'
                      ? 'cart'
                      : 'user'
                "
                :size="18"
              />
            </view>
            <text class="notice-type">
              {{
                notice.type === 'activity'
                  ? '活动动态'
                  : notice.type === 'errand'
                    ? '跑腿进展'
                    : '账号服务'
              }}
            </text>
            <text v-if="!notice.read" class="unread-dot" />
            <text class="notice-time">{{ notice.time }}</text>
          </view>
          <text class="notice-title">{{ notice.title }}</text>
          <text class="notice-body">{{ notice.text }}</text>
          <button class="notice-link" @click="openRelated(notice)">
            <text>
              {{
                notice.type === 'activity'
                  ? '查看我的活动'
                  : notice.type === 'errand'
                    ? '查看我的订单'
                    : '查看认证状态'
              }}
            </text>
            <AppIcon name="arrow-right" :size="14" />
          </button>
        </view>
        <EmptyState
          v-if="!notices.length"
          title="这里暂时没有通知"
          description="有新的进展，会及时留在这里。"
          icon="notification"
        />
        <text class="footer-note">站内通知始终保留在通知列表中</text>
      </template>
      <template v-else>
        <EmptyState
          title="登录后查看通知"
          description="只有你能查看自己的订单和账号动态。"
          icon="notification"
        />
        <button class="btn btn-primary" @click="go('/pages-account/login')">前往登录</button>
      </template>
    </view>
  </view>
</template>

<style scoped>
.read-button {
  font-size: calc(13px * var(--font-scale, 1));
  color: var(--color-primary);
  background: transparent;
  margin: 0;
  padding: 0;
  min-height: 44px;
  display: flex;
  align-items: center;
}
.read-button::after {
  border: 0;
}
.read-button[disabled] {
  color: #a0aaa1;
}
.notice-filters {
  display: flex;
  gap: 9px;
  margin: 21px 0;
}
.notice-filter {
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 38px;
  padding: 0 16px;
  margin: 0;
  border-radius: 8px;
  background: #ecefeb;
  color: #768471;
  flex: 1;
}
.notice-filter::after {
  border: 0;
}
.notice-filter.active {
  background: #e0ede1;
  color: var(--color-primary);
  font-weight: 600;
}
.notice-card {
  padding: 17px 17px 0;
  border-radius: 12px;
  margin-bottom: 13px;
}
.notice-top {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 15px;
}
.type-icon {
  width: 29px;
  height: 29px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ecf2e8;
  color: #70865a;
}
.type-icon.errand {
  background: #f5eddf;
  color: #99805b;
}
.type-icon.activity {
  background: #e8eef5;
  color: #6586a2;
}
.notice-type {
  font-size: calc(12px * var(--font-scale, 1));
  color: #7e8a78;
}
.notice-time {
  margin-left: auto;
  font-size: calc(11px * var(--font-scale, 1));
  color: #a0a79a;
}
.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #db7867;
}
.notice-title {
  display: block;
  font-size: calc(16px * var(--font-scale, 1));
  font-weight: 600;
  margin-bottom: 9px;
}
.notice-body {
  font-size: calc(14px * var(--font-scale, 1));
  line-height: 1.8;
  color: #7a8474;
  display: block;
  white-space: pre-wrap;
}
.notice-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 0;
  margin: 16px 0 0;
  background: transparent;
  border-top: 1px solid var(--color-line);
  font-size: calc(13px * var(--font-scale, 1));
  color: #537753;
  line-height: 1.5;
  border-radius: 0;
  text-align: left;
}
.notice-link::after {
  border: 0;
}
.footer-note {
  display: block;
  text-align: center;
  font-size: calc(11px * var(--font-scale, 1));
  color: #9ba593;
  margin: 28px 0;
}
.notice-time,
.footer-note {
  font-size: calc(12px * var(--font-scale, 1));
}
.notice-filter {
  line-height: 44px;
}
.notice-body {
  font-size: calc(15px * var(--font-scale, 1));
}
</style>
