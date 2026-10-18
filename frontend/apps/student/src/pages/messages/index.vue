<script setup lang="ts">
import { computed, ref } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { go } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import BottomNav from '@/components/BottomNav.vue'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import DemoNotice from '@/components/DemoNotice.vue'

const query = ref('')
const unreadOnly = ref(false)
const busy = ref(false)
const error = ref('')
const noticeCount = computed(() => state.notices.filter((n) => !n.read).length)
const unreadCount = computed(
  () => state.conversations.reduce((sum, item) => sum + item.unread, 0) + noticeCount.value,
)
const conversations = computed(() =>
  state.conversations.filter(
    (item) =>
      (!unreadOnly.value || item.unread > 0) &&
      `${item.name}${item.preview}${item.contextTitle}`.includes(query.value.trim()),
  ),
)
async function readAll() {
  if (busy.value || !unreadCount.value) return
  busy.value = true
  error.value = ''
  try {
    for (const item of state.conversations.filter((item) => item.unread > 0))
      await api.readConversation(item.id)
    await api.readNotices()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '暂时没能更新已读，请重试。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page inbox-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader
      title="消息"
      :subtitle="
        unreadCount ? `${unreadCount} 条未读，慢慢来，一件件处理` : '聊聊闲置，也聊聊校园生活'
      "
    >
      <template #right>
        <button
          v-if="state.user.loggedIn"
          class="read-all"
          :disabled="busy || !unreadCount"
          @click="readAll"
        >
          {{ busy ? '更新中' : '全部已读' }}
        </button>
      </template>
    </PageHeader>
    <view class="content"><DemoNotice /></view>
    <template v-if="state.user.loggedIn">
      <view class="content">
        <view class="search-box">
          <AppIcon name="search" :size="19" />
          <input
            v-model="query"
            class="search-input"
            placeholder="搜索联系人或聊天内容"
            aria-label="搜索会话"
          />
          <button v-if="query" class="clear-search" @click="query = ''">
            <AppIcon name="close" :size="16" />
          </button>
        </view>
        <view v-if="error" class="form-error section">{{ error }}</view>
        <view class="notice-entry surface" @click="go('/pages-message/notices')">
          <view class="notice-icon"><AppIcon name="notification" :size="25" /></view>
          <view class="entry-copy">
            <view class="entry-title">
              服务通知
              <text v-if="noticeCount" class="count-badge">
                {{ noticeCount > 99 ? '99+' : noticeCount }}
              </text>
            </view>
            <text class="muted caption">订单进展、报名提醒和账号动态</text>
          </view>
          <AppIcon name="arrow-right" :size="16" />
        </view>
        <view class="inbox-toolbar">
          <text class="group-heading">私信</text>
          <view class="filter-tabs">
            <button :class="['filter-tab', { chosen: !unreadOnly }]" @click="unreadOnly = false">
              全部
            </button>
            <button :class="['filter-tab', { chosen: unreadOnly }]" @click="unreadOnly = true">
              未读
            </button>
          </view>
        </view>
      </view>
      <view v-if="conversations.length" class="surface conversation-list">
        <view
          v-for="conversation in conversations"
          :key="conversation.id"
          class="conversation-row"
          @click="go(`/pages-message/chat?id=${encodeURIComponent(conversation.id)}`)"
        >
          <view class="avatar" :style="{ background: conversation.tone || '#eaf2ed' }">
            <text>{{ conversation.avatar || conversation.name.slice(0, 1) }}</text>
            <text v-if="conversation.unread" class="avatar-badge">
              {{ conversation.unread > 99 ? '99+' : conversation.unread }}
            </text>
          </view>
          <view class="conversation-copy">
            <view class="row between">
              <text class="person-name">{{ conversation.name }}</text>
              <text class="timestamp">{{ conversation.time }}</text>
            </view>
            <text class="preview">{{ conversation.preview || '从一句你好开始' }}</text>
            <view class="context-tag">
              <text>
                {{
                  conversation.contextType === 'market'
                    ? '二手'
                    : conversation.contextType === 'errand'
                      ? '跑腿'
                      : '活动'
                }}
              </text>
              <text class="context-text">{{ conversation.contextTitle }}</text>
            </view>
          </view>
        </view>
      </view>
      <view v-else class="content">
        <EmptyState
          :title="query ? '没有找到相关会话' : unreadOnly ? '消息都看完了' : '还没有私信'"
          :description="
            query
              ? '换个关键词，或清空搜索试试。'
              : unreadOnly
                ? '新的回复会出现在这里。'
                : '在商品详情联系卖家，就可以开始聊天。'
          "
          icon="chat"
        />
        <button v-if="query" class="btn btn-secondary" @click="query = ''">清空搜索</button>
      </view>
      <text class="inbox-footer">私信仅向会话参与者展示</text>
    </template>
    <view v-else class="content guest-inbox">
      <EmptyState
        title="登录后，接着聊"
        description="你的私信和服务通知会统一放在这里。"
        icon="chat"
      />
      <button class="btn btn-primary" @click="go('/pages-account/login')">
        {{ state.demo ? '进入演示账号' : '前往登录' }}
      </button>
    </view>
    <BottomNav active="messages" />
  </view>
</template>

<style scoped>
.search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #ecefec;
  border-radius: 10px;
  padding: 0 13px;
  height: 45px;
  margin: 16px 0 18px;
  color: var(--color-muted);
}
.search-input {
  flex: 1;
  min-width: 0;
  font-size: calc(15px * var(--font-scale, 1));
  height: 44px;
}
.clear-search,
.read-all,
.filter-tab {
  padding: 0;
  background: transparent;
  line-height: normal;
  margin: 0;
  border: 0;
}
.clear-search::after,
.read-all::after,
.filter-tab::after {
  border: 0;
}
.clear-search {
  width: 32px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.read-all {
  font-size: calc(14px * var(--font-scale, 1));
  color: var(--color-primary);
  min-height: 44px;
  white-space: nowrap;
}
.read-all[disabled] {
  color: #a1aaa5;
}
.notice-entry {
  display: flex;
  align-items: center;
  padding: 17px 14px;
  border-radius: 12px;
  gap: 13px;
}
.notice-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 13px;
  background: #edf4ec;
  color: #65815b;
  flex-shrink: 0;
}
.entry-copy {
  flex: 1;
  min-width: 0;
}
.entry-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 5px;
}
.count-badge,
.avatar-badge {
  font-size: calc(11px * var(--font-scale, 1));
  background: #dc665a;
  color: white;
  border-radius: 20px;
  padding: 2px 5px;
  line-height: 15px;
  min-width: 15px;
  text-align: center;
}
.inbox-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 23px 0 11px;
}
.group-heading {
  font-weight: 600;
}
.filter-tabs {
  display: flex;
  gap: 5px;
}
.filter-tab {
  font-size: calc(13px * var(--font-scale, 1));
  padding: 8px 12px;
  color: var(--color-muted);
}
.filter-tab.chosen {
  color: var(--color-primary);
  background: #e6f1ea;
  border-radius: 7px;
}
.conversation-list {
  margin: 0 16px;
  border-radius: 12px;
  overflow: hidden;
}
.conversation-row {
  display: flex;
  gap: 13px;
  padding: 18px 14px;
  border-bottom: 1px solid var(--color-line);
}
.conversation-row:last-child {
  border-bottom: 0;
}
.avatar {
  width: 47px;
  height: 47px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  flex-shrink: 0;
  font-size: calc(22px * var(--font-scale, 1));
  color: #526958;
}
.avatar-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  border: 2px solid #fff;
}
.conversation-copy {
  flex: 1;
  min-width: 0;
}
.person-name {
  font-weight: 600;
  font-size: calc(16px * var(--font-scale, 1));
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.timestamp {
  font-size: calc(12px * var(--font-scale, 1));
  color: var(--color-muted);
  margin-left: 9px;
  flex-shrink: 0;
}
.preview {
  font-size: calc(14px * var(--font-scale, 1));
  color: var(--color-muted);
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 5px;
}
.context-tag {
  margin-top: 8px;
  font-size: calc(11px * var(--font-scale, 1));
  color: #7b857e;
  display: flex;
  align-items: center;
  gap: 7px;
}
.context-tag > text:first-child {
  background: #f1f3f1;
  border-radius: 3px;
  padding: 1px 5px;
  flex-shrink: 0;
}
.context-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.inbox-footer {
  display: block;
  text-align: center;
  font-size: calc(12px * var(--font-scale, 1));
  color: #909a93;
  padding: 28px 16px;
}
.guest-inbox {
  padding-top: 30px;
}
.context-tag,
.inbox-footer {
  font-size: calc(12px * var(--font-scale, 1));
}
.filter-tab {
  min-height: 44px;
}
.preview {
  font-size: calc(14px * var(--font-scale, 1));
}
</style>
