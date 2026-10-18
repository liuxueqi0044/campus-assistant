<script setup lang="ts">
import { ref } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { go } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import AppIcon from '@/components/AppIcon.vue'

const confirmAction = ref<'logout' | 'reset' | null>(null)
const busy = ref(false)
const error = ref('')
const success = ref('')
function openConfirmation(action: 'logout' | 'reset') {
  confirmAction.value = action
  error.value = ''
  success.value = ''
}
function setNotifications(event: unknown) {
  const value = (event as { detail?: { value?: boolean } })?.detail?.value
  if (typeof value === 'boolean') state.notifications = value
}
async function confirm() {
  if (!confirmAction.value || busy.value) return
  const action = confirmAction.value
  busy.value = true
  error.value = ''
  success.value = ''
  try {
    if (action === 'reset') {
      await api.resetDemo()
      success.value = '已恢复初始演示数据与设置。'
    } else {
      await api.logout()
      success.value = '已退出，私信、通知与课表缓存已清理。'
    }
    confirmAction.value = null
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作暂未完成，请重试。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page detail-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="设置" subtitle="按你的习惯，安排这个小小的空间" back />
    <view class="content">
      <DemoNotice />
      <text class="setting-group-label">阅读与打开方式</text>
      <view class="settings-card surface">
        <view class="setting-heading">
          <text class="setting-title">文字大小</text>
          <text class="setting-description">让阅读更舒服一点</text>
        </view>
        <view class="size-options">
          <button
            :class="['size-option', { selected: state.fontScale === 1 }]"
            @click="state.fontScale = 1"
          >
            <text class="size-preview">字</text>
            <text>标准</text>
            <AppIcon v-if="state.fontScale === 1" name="check" :size="15" />
          </button>
          <button
            :class="['size-option', { selected: state.fontScale > 1 }]"
            @click="state.fontScale = 1.125"
          >
            <text class="size-preview large">字</text>
            <text>较大</text>
            <AppIcon v-if="state.fontScale > 1" name="check" :size="15" />
          </button>
        </view>
        <view class="reading-preview" :style="{ fontSize: `${16 * state.fontScale}px` }">
          今天也在校园里，遇见一点小美好。
        </view>
      </view>
      <view class="settings-card surface">
        <view class="setting-heading">
          <text class="setting-title">默认首页</text>
          <text class="setting-description">下次打开时，先看到什么</text>
        </view>
        <view class="choice-row" @click="state.defaultTab = 'campus'">
          <view>
            <text class="choice-title">校园</text>
            <text class="choice-note">二手、跑腿和活动，一眼看见</text>
          </view>
          <view :class="['radio-mark', { checked: state.defaultTab === 'campus' }]">
            <view v-if="state.defaultTab === 'campus'" />
          </view>
        </view>
        <view class="choice-row" @click="state.defaultTab = 'timetable'">
          <view>
            <text class="choice-title">课表</text>
            <text class="choice-note">先看看今天的课程安排</text>
          </view>
          <view :class="['radio-mark', { checked: state.defaultTab === 'timetable' }]">
            <view v-if="state.defaultTab === 'timetable'" />
          </view>
        </view>
      </view>
      <text class="setting-group-label">通知与隐私</text>
      <view class="settings-card surface">
        <view class="notification-row">
          <view class="notification-copy">
            <text class="setting-title">额外消息提醒</text>
            <text class="setting-description">关闭后仍可在通知中心查看记录</text>
          </view>
          <switch
            :checked="state.notifications"
            color="#07855D"
            style="transform: scale(0.83); transform-origin: right center"
            @change="setNotifications"
          />
        </view>
        <view class="settings-note">
          {{
            state.demo
              ? '此处仅保存演示偏好，不会申请或发送真实微信订阅消息。'
              : '此处保存提醒偏好，实际微信提醒仍需单独授权与渠道支持。'
          }}
        </view>
      </view>
      <view class="privacy-card">
        <AppIcon name="lock-on" :size="19" />
        <view>
          <text class="privacy-title">你的私人空间</text>
          <text class="privacy-description">
            私聊和课表只向本人或获授权的参与者展示。退出账号会清理当前会话中的私有数据。
          </text>
        </view>
      </view>
      <text class="setting-group-label">账号</text>
      <view class="settings-card surface account-card">
        <view class="account-row">
          <text>{{ state.user.loggedIn ? state.user.name : '当前未登录' }}</text>
          <text class="account-state">
            {{ state.demo ? '演示模式' : state.user.loggedIn ? '已登录' : '访客' }}
          </text>
        </view>
        <button
          v-if="state.user.loggedIn"
          class="account-action"
          :disabled="busy"
          @click="openConfirmation('logout')"
        >
          退出账号
        </button>
        <button v-else class="account-action sign-in" @click="go('/pages-account/login')">
          {{ state.demo ? '进入演示账号' : '前往登录' }}
        </button>
      </view>
      <template v-if="state.demo">
        <text class="setting-group-label">演示工具</text>
        <view class="settings-card surface">
          <view class="setting-heading">
            <text class="setting-title">重新开始体验</text>
            <text class="setting-description">清除新增演示内容，恢复初始状态与偏好</text>
          </view>
          <button
            class="reset-button"
            :disabled="busy"
            @click="openConfirmation('reset')"
          >
            重置演示数据
          </button>
        </view>
      </template>
      <view v-if="confirmAction" class="confirmation surface">
        <text class="confirmation-title">
          {{ confirmAction === 'reset' ? '恢复初始演示？' : '退出当前账号？' }}
        </text>
        <text class="confirmation-description">
          {{
            confirmAction === 'reset'
              ? '本次新增的发布、订单、聊天和课表修改会被清除，演示账号将恢复登录。'
              : '私有数据会从当前会话中清理。你仍然可以浏览公开校园内容。'
          }}
        </text>
        <view class="confirmation-buttons">
          <button class="btn btn-secondary" :disabled="busy" @click="confirmAction = null">
            取消
          </button>
          <button class="btn btn-primary" :disabled="busy" @click="confirm">
            {{ busy ? '处理中…' : '确认' }}
          </button>
        </view>
      </view>
      <view v-if="error" class="form-error section">{{ error }}</view>
      <view v-if="success" class="success-message">{{ success }}</view>
      <text class="settings-footer">校园助手 · {{ state.demo ? '前端演示版' : '学生端' }}</text>
    </view>
  </view>
</template>

<style scoped>
.setting-group-label {
  font-size: calc(12px * var(--font-scale, 1));
  color: #8a9780;
  display: block;
  margin: 23px 2px 11px;
}
.settings-card {
  border-radius: 12px;
  padding: 17px;
  margin-bottom: 12px;
}
.setting-title {
  display: block;
  font-size: calc(15px * var(--font-scale, 1));
  font-weight: 500;
}
.setting-description {
  display: block;
  font-size: calc(12px * var(--font-scale, 1));
  color: #929d89;
  line-height: 1.6;
  margin-top: 6px;
}
.size-options {
  display: flex;
  gap: 11px;
  margin-top: 17px;
}
.size-option {
  flex: 1;
  border: 1px solid #e5e9df;
  border-radius: 9px;
  background: #fafbf8;
  color: #819076;
  display: flex;
  align-items: center;
  gap: 7px;
  white-space: nowrap;
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1;
  padding: 12px;
  margin: 0;
  min-height: 59px;
}
.size-option::after {
  border: 0;
}
.size-option.selected {
  background: #edf4e8;
  border-color: #96b58b;
  color: #53774a;
}
.size-preview {
  font-size: calc(18px * var(--font-scale, 1));
}
.size-preview.large {
  font-size: calc(23px * var(--font-scale, 1));
}
.reading-preview {
  margin-top: 15px;
  padding: 13px 0 0;
  border-top: 1px solid #edf0e8;
  color: #829373;
  line-height: 1.7;
}
.choice-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 0 2px;
  gap: 12px;
}
.choice-row + .choice-row {
  border-top: 1px solid #edf0e8;
  margin-top: 15px;
}
.choice-title {
  display: block;
  font-size: calc(15px * var(--font-scale, 1));
}
.choice-note {
  display: block;
  font-size: calc(11px * var(--font-scale, 1));
  color: #929d89;
  margin-top: 6px;
}
.radio-mark {
  width: 19px;
  height: 19px;
  border: 1.5px solid #cbd5c2;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.radio-mark.checked {
  border-color: var(--color-primary);
}
.radio-mark > view {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--color-primary);
}
.notification-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.notification-copy {
  flex: 1;
  min-width: 0;
}
.settings-note {
  border-top: 1px solid #edf0e8;
  font-size: calc(11px * var(--font-scale, 1));
  line-height: 1.8;
  color: #99a38f;
  margin-top: 15px;
  padding-top: 12px;
}
.privacy-card {
  display: flex;
  gap: 10px;
  padding: 10px 3px;
  color: #7d9272;
}
.privacy-card > view:last-child {
  flex: 1;
}
.privacy-title {
  display: block;
  font-size: calc(13px * var(--font-scale, 1));
  font-weight: 500;
}
.privacy-description {
  font-size: calc(11px * var(--font-scale, 1));
  line-height: 1.9;
  color: #9aa58f;
  display: block;
  margin-top: 5px;
}
.account-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: calc(14px * var(--font-scale, 1));
  padding: 1px 0 17px;
}
.account-state {
  font-size: calc(11px * var(--font-scale, 1));
  background: #eef3e9;
  color: #87977b;
  padding: 4px 7px;
  border-radius: 4px;
}
.account-action {
  border-top: 1px solid #edf0e8;
  background: transparent;
  color: #aa7465;
  font-size: calc(14px * var(--font-scale, 1));
  padding: 15px 0 1px;
  line-height: 1.8;
  border-radius: 0;
}
.account-action::after,
.reset-button::after {
  border: 0;
}
.account-action.sign-in {
  color: var(--color-primary);
}
.reset-button {
  background: #f2f5ee;
  color: #7b8a6e;
  font-size: calc(13px * var(--font-scale, 1));
  border-radius: 8px;
  padding: 10px;
  line-height: 1.8;
  margin-top: 16px;
  min-height: 44px;
}
.confirmation {
  border: 1px solid #d9e5ce;
  border-radius: 12px;
  padding: 19px;
  margin-top: 16px;
}
.confirmation-title {
  font-size: calc(16px * var(--font-scale, 1));
  font-weight: 600;
  display: block;
}
.confirmation-description {
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.8;
  color: #87967a;
  display: block;
  margin-top: 10px;
}
.confirmation-buttons {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}
.confirmation-buttons button {
  flex: 1;
}
.success-message {
  font-size: calc(13px * var(--font-scale, 1));
  color: #5a8051;
  line-height: 1.7;
  padding: 15px 4px;
}
.settings-footer {
  display: block;
  text-align: center;
  font-size: calc(11px * var(--font-scale, 1));
  color: #a1ad96;
  padding: 24px 0;
}
.setting-title,
.choice-title {
  font-size: calc(16px * var(--font-scale, 1));
}
.setting-description,
.privacy-description {
  font-size: calc(13px * var(--font-scale, 1));
}
.choice-note,
.settings-note,
.account-state,
.settings-footer {
  font-size: calc(12px * var(--font-scale, 1));
}
</style>

