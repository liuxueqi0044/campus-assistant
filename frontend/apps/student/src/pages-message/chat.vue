<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { state } from '@/state'
import { api } from '@/services/api'
import { go } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import DemoNotice from '@/components/DemoNotice.vue'

const id = ref('')
const draft = ref('')
const busy = ref(false)
const error = ref('')
const scrollTarget = ref('')
const conversation = computed(() => state.conversations.find((c) => c.id === id.value))
const sortedMessages = computed(() =>
  [...(conversation.value?.messages || [])].sort((a, b) => a.seq - b.seq),
)
async function scrollToEnd() {
  scrollTarget.value = ''
  await nextTick()
  scrollTarget.value = 'message-end'
}
async function read() {
  if (!state.user.loggedIn || !conversation.value) return
  try {
    await api.readConversation(id.value)
    await scrollToEnd()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '消息暂未同步，请重新进入。'
  }
}
onLoad((options) => {
  id.value = options?.id || ''
  void read()
})
onShow(() => {
  void read()
})
async function send() {
  const text = draft.value.trim()
  if (!text || busy.value || !conversation.value) return
  busy.value = true
  error.value = ''
  try {
    await api.sendMessage(id.value, text)
    draft.value = ''
    await scrollToEnd()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '发送失败，内容已保留，可以重试。'
  } finally {
    busy.value = false
  }
}
function openContext() {
  const item = conversation.value
  if (!item) return
  const routes = {
    market: '/pages-market/detail',
    errand: '/pages-errand/detail',
    activity: '/pages-activity/detail',
  }
  go(`${routes[item.contextType]}?id=${encodeURIComponent(item.contextId)}`)
}
</script>

<template>
  <view class="chat-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader
      :title="conversation?.name || '会话'"
      :subtitle="state.demo ? '演示私信 · 消息仅保存在当前演示中' : '私聊'"
      back
    />
    <template v-if="state.user.loggedIn && conversation">
      <view class="chat-demo"><DemoNotice /></view>
      <view class="context-card" @click="openContext">
        <view class="context-icon">
          <AppIcon
            :name="
              conversation.contextType === 'market'
                ? 'bag'
                : conversation.contextType === 'errand'
                  ? 'cart'
                  : 'calendar'
            "
            :size="22"
          />
        </view>
        <view class="context-copy">
          <text class="context-label">
            来自{{
              conversation.contextType === 'market'
                ? '二手交易'
                : conversation.contextType === 'errand'
                  ? '跑腿订单'
                  : '校园活动'
            }}
          </text>
          <text class="context-title">{{ conversation.contextTitle }}</text>
        </view>
        <text class="context-action">查看</text>
        <AppIcon name="arrow-right" :size="15" />
      </view>
      <scroll-view
        class="message-scroll"
        scroll-y
        :scroll-into-view="scrollTarget"
        :scroll-with-animation="true"
      >
        <view class="message-area">
          <view class="chat-hint">
            {{ state.demo ? '以下是演示会话，不会发送给真实同学' : '交接地点优先选择校园公共区域' }}
          </view>
          <view v-if="!sortedMessages.length" class="conversation-start">
            <text>关于「{{ conversation.contextTitle }}」</text>
            <text>说声你好，开始交流吧</text>
          </view>
          <view
            v-for="message in sortedMessages"
            :key="message.id"
            :class="['message-row', { mine: message.mine }]"
          >
            <template v-if="message.removed">
              <view class="removed-message">这条消息已撤回或移除</view>
            </template>
            <template v-else>
              <view
                class="chat-avatar"
                :style="{ background: message.mine ? '#d9eade' : conversation.tone }"
              >
                {{
                  message.mine
                    ? state.user.name.slice(0, 1)
                    : conversation.avatar || conversation.name.slice(0, 1)
                }}
              </view>
              <view class="bubble-stack">
                <view class="message-bubble">
                  <text selectable>{{ message.text }}</text>
                </view>
                <view class="message-meta">
                  <text>{{ message.time }}</text>
                  <text v-if="message.mine">
                    {{
                      message.status === 'sending'
                        ? '发送中'
                        : message.status === 'failed'
                          ? '发送失败'
                          : state.demo
                            ? '已保存'
                            : '已发送'
                    }}
                  </text>
                </view>
              </view>
            </template>
          </view>
          <view id="message-end" class="message-end" />
        </view>
      </scroll-view>
      <view class="composer-area">
        <view v-if="error" class="form-error composer-error">{{ error }}</view>
        <view class="composer">
          <textarea
            v-model="draft"
            class="chat-input"
            placeholder="说点什么…"
            :maxlength="1000"
            auto-height
            :disabled="busy"
            :show-confirm-bar="false"
            :adjust-position="true"
            :cursor-spacing="24"
            confirm-type="send"
            @confirm="send"
          />
          <button class="send-button" :disabled="!draft.trim() || busy" @click="send">
            {{ busy ? '发送中' : '发送' }}
          </button>
        </view>
        <text v-if="draft.length > 900" class="input-count">{{ draft.length }} / 1000</text>
      </view>
    </template>
    <view v-else class="content missing-conversation">
      <EmptyState
        :title="state.user.loggedIn ? '这段会话暂不可用' : '登录后查看私信'"
        :description="
          state.user.loggedIn
            ? '会话可能不存在，或当前账号没有访问权限。'
            : '私信只向会话参与者展示。'
        "
        icon="chat"
      />
      <button
        class="btn btn-primary"
        @click="state.user.loggedIn ? go('/pages/messages/index') : go('/pages-account/login')"
      >
        {{ state.user.loggedIn ? '返回消息列表' : '前往登录' }}
      </button>
    </view>
  </view>
</template>

<style scoped>
.chat-page {
  height: 100vh;
  max-width: 520px;
  margin: 0 auto;
  background: #f3f5f2;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.chat-demo {
  padding: 0 16px;
  flex-shrink: 0;
}
.context-card {
  margin: 13px 16px 8px;
  background: #fff;
  border: 1px solid #e8ede7;
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  flex-shrink: 0;
}
.context-icon {
  width: 39px;
  height: 39px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #edf3eb;
  color: #6b8369;
  border-radius: 9px;
  flex-shrink: 0;
}
.context-copy {
  flex: 1;
  min-width: 0;
}
.context-label {
  font-size: calc(10px * var(--font-scale, 1));
  color: var(--color-muted);
  display: block;
  margin-bottom: 4px;
}
.context-title {
  font-size: calc(13px * var(--font-scale, 1));
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.context-action {
  font-size: calc(12px * var(--font-scale, 1));
  color: #7d897f;
  flex-shrink: 0;
}
.message-scroll {
  height: 0;
  flex: 1;
  min-height: 0;
}
.message-area {
  padding: 10px 16px 0;
}
.chat-hint {
  text-align: center;
  line-height: 1.6;
  font-size: calc(11px * var(--font-scale, 1));
  color: #9aa399;
  padding: 7px 10px 24px;
}
.conversation-start {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  font-size: calc(13px * var(--font-scale, 1));
  color: #96a18f;
  padding: 30px 5px;
}
.message-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-bottom: 22px;
}
.message-row.mine {
  flex-direction: row-reverse;
}
.chat-avatar {
  height: 35px;
  width: 35px;
  flex-shrink: 0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: calc(16px * var(--font-scale, 1));
  color: #5b7460;
}
.bubble-stack {
  max-width: calc(100% - 68px);
  min-width: 0;
}
.message-bubble {
  padding: 11px 14px;
  background: #fff;
  border: 1px solid #e7eae5;
  border-radius: 2px 11px 11px 11px;
  line-height: 1.65;
  font-size: calc(15px * var(--font-scale, 1));
  overflow-wrap: anywhere;
  word-break: break-word;
  white-space: pre-wrap;
}
.mine .message-bubble {
  background: #dfeedd;
  border-color: #dce8d9;
  border-radius: 11px 2px 11px 11px;
  color: #294b34;
}
.message-meta {
  display: flex;
  gap: 7px;
  color: #9aa197;
  font-size: calc(10px * var(--font-scale, 1));
  margin-top: 6px;
}
.mine .message-meta {
  justify-content: flex-end;
}
.removed-message {
  text-align: center;
  font-size: calc(12px * var(--font-scale, 1));
  color: #9aa197;
  width: 100%;
  padding: 4px;
}
.message-end {
  height: 12px;
}
.composer-area {
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  background: #fafbf9;
  border-top: 1px solid #e6ebe3;
  flex-shrink: 0;
}
.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}
.chat-input {
  background: #fff;
  border: 1px solid #e0e6dd;
  border-radius: 9px;
  padding: 10px 12px;
  line-height: 24px;
  min-height: 24px;
  max-height: 120px;
  flex: 1;
  width: 0;
  font-size: calc(15px * var(--font-scale, 1));
  box-sizing: content-box;
}
.send-button {
  padding: 0 14px;
  height: 44px;
  min-width: 64px;
  margin: 0;
  border-radius: 8px;
  background: var(--color-primary);
  font-size: calc(14px * var(--font-scale, 1));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.send-button::after {
  border: 0;
}
.send-button[disabled] {
  background: #e2e9df;
  color: #9fa99c;
}
.composer-error {
  margin-bottom: 10px;
}
.input-count {
  display: block;
  text-align: right;
  font-size: calc(10px * var(--font-scale, 1));
  color: #8a9385;
  margin-top: 5px;
}
.missing-conversation {
  padding-top: 40px;
}
.context-label,
.chat-hint,
.message-meta,
.input-count {
  font-size: calc(12px * var(--font-scale, 1));
}
.context-title {
  font-size: calc(14px * var(--font-scale, 1));
}
.message-bubble,
.chat-input {
  font-size: calc(16px * var(--font-scale, 1));
}
</style>
