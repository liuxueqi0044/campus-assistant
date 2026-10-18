<script setup lang="ts">
import { computed, ref } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { go } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const name = ref(state.user.name)
const studentId = ref('')
const editing = ref(false)
const busy = ref(false)
const error = ref('')
const fieldErrors = ref({ name: '', studentId: '' })
const campus = computed(() => state.campuses.find((c) => c.id === state.user.campusId))
const canShowForm = computed(() => state.user.verification === 'NONE' || editing.value)
function cancelEdit() {
  editing.value = false
  error.value = ''
  studentId.value = ''
}
async function submit() {
  fieldErrors.value = {
    name: name.value.trim().length < 2 ? '请填写至少 2 个字的姓名或演示昵称。' : '',
    studentId: /^[A-Za-z0-9-]{4,24}$/.test(studentId.value.trim())
      ? ''
      : '请填写 4–24 位字母、数字或短横线。',
  }
  if (fieldErrors.value.name || fieldErrors.value.studentId || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.verify({ name: name.value.trim(), studentId: studentId.value.trim() })
    editing.value = false
    studentId.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '提交失败，请保留填写内容后重试。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page detail-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="学生认证" subtitle="让同校交流，多一份信任" back />
    <view class="content">
      <DemoNotice />
      <template v-if="state.user.loggedIn">
        <view class="verification-school surface">
          <view class="school-icon"><AppIcon name="home" :size="26" /></view>
          <view>
            <text class="school-name">{{ state.user.school }}</text>
            <text class="muted caption">{{ campus?.name || '当前账号所属校区' }}</text>
          </view>
          <text class="school-fixed">账号所属学校</text>
        </view>
        <view v-if="!canShowForm" class="verification-status surface">
          <view :class="['status-symbol', { pending: state.user.verification === 'PENDING' }]">
            <AppIcon :name="state.user.verification === 'PENDING' ? 'time' : 'check'" :size="30" />
          </view>
          <text class="status-title">
            {{
              state.user.verification === 'PENDING'
                ? '资料已提交，等待审核'
                : state.demo
                  ? '演示身份已认证'
                  : '学生身份已认证'
            }}
          </text>
          <text class="status-description">
            {{
              state.user.verification === 'PENDING'
                ? state.demo
                  ? '当前仅模拟待审核状态，未提交到真实学校或审核系统。'
                  : '审核结果将通过服务通知告知。你可以继续浏览校园内容。'
                : state.demo
                  ? '这是预设的前端演示身份，不代表真实校方核验。'
                  : '你的认证状态来自账号服务，认证材料不会公开展示。'
            }}
          </text>
          <button class="btn btn-secondary status-action" @click="go('/pages-message/notices')">
            查看服务通知
          </button>
          <button
            v-if="state.demo && state.user.verification === 'VERIFIED'"
            class="text-button"
            @click="editing = true"
          >
            体验提交演示申请
          </button>
        </view>
        <template v-else>
          <view class="form-intro">
            <text class="intro-title">{{ state.demo ? '填写一份演示申请' : '填写认证资料' }}</text>
            <text class="intro-copy">
              {{
                state.demo
                  ? '请使用虚构姓名与学号，例如 DEMO20260001。演示不会完成真实认证。'
                  : '资料仅用于学生身份核验，不向其他同学公开。'
              }}
            </text>
          </view>
          <view class="verification-form surface">
            <view class="field">
              <text class="label">{{ state.demo ? '演示姓名' : '姓名' }}</text>
              <input
                v-model="name"
                class="form-input"
                :maxlength="30"
                :placeholder="state.demo ? '例如 林同学' : '请输入姓名'"
              />
              <text v-if="fieldErrors.name" class="form-error">{{ fieldErrors.name }}</text>
            </view>
            <view class="field">
              <text class="label">{{ state.demo ? '演示学号' : '学号' }}</text>
              <input
                v-model="studentId"
                class="form-input"
                :maxlength="24"
                :placeholder="state.demo ? 'DEMO20260001' : '请输入学号'"
              />
              <text v-if="fieldErrors.studentId" class="form-error">
                {{ fieldErrors.studentId }}
              </text>
            </view>
            <view class="privacy-tip">
              <AppIcon name="lock-on" :size="16" />
              <text>
                {{ state.demo ? '请勿填写真实个人信息' : '提交后会进入审核，不会自动通过' }}
              </text>
            </view>
          </view>
          <view v-if="error" class="form-error section">{{ error }}</view>
          <button class="btn btn-primary submit-button" :disabled="busy" @click="submit">
            {{ busy ? '正在提交…' : state.demo ? '提交演示申请' : '提交认证' }}
          </button>
          <button
            v-if="editing"
            class="text-button"
            :disabled="busy"
            @click="cancelEdit"
          >
            取消，保留当前状态
          </button>
        </template>
        <view class="verification-explanation">
          <text class="explanation-title">认证与登录分别是什么？</text>
          <text>登录建立账号，学生认证核验学校身份。切换浏览校区不会自动改变你的认证学校。</text>
          <text v-if="state.demo">
            当前页面用于前端流程演示；校方接口、材料审核与正式认证由后端接入。
          </text>
        </view>
      </template>
      <template v-else>
        <EmptyState
          title="先登录，再认证"
          description="认证申请需要关联你的个人账号。"
          icon="user"
        />
        <button class="btn btn-primary" @click="go('/pages-account/login')">前往登录</button>
      </template>
    </view>
  </view>
</template>

<style scoped>
.verification-school {
  margin-top: 18px;
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 18px 15px;
  border-radius: 12px;
}
.school-icon {
  width: 43px;
  height: 43px;
  background: #eaf0e5;
  color: #658165;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.school-name {
  display: block;
  font-weight: 600;
  font-size: calc(16px * var(--font-scale, 1));
  margin-bottom: 5px;
}
.school-fixed {
  margin-left: auto;
  font-size: calc(10px * var(--font-scale, 1));
  color: #97a18e;
  max-width: 50px;
  line-height: 1.6;
}
.verification-status {
  text-align: center;
  padding: 30px 22px 20px;
  border-radius: 12px;
  margin-top: 16px;
}
.status-symbol {
  height: 65px;
  width: 65px;
  background: #e9f2e7;
  color: #5f8b5e;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 19px;
}
.status-symbol.pending {
  background: #f5eedf;
  color: #9c8957;
}
.status-title {
  font-size: calc(19px * var(--font-scale, 1));
  font-weight: 600;
  display: block;
}
.status-description {
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.9;
  display: block;
  color: #86927f;
  margin-top: 12px;
}
.status-action {
  margin-top: 25px;
}
.text-button {
  background: transparent;
  margin: 9px 0 0;
  padding: 8px 0;
  line-height: 1.8;
  font-size: calc(13px * var(--font-scale, 1));
  color: #78916e;
  min-height: 44px;
}
.text-button::after {
  border: 0;
}
.form-intro {
  padding: 28px 2px 15px;
}
.intro-title {
  font-size: calc(17px * var(--font-scale, 1));
  font-weight: 600;
  display: block;
  margin-bottom: 8px;
}
.intro-copy {
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.8;
  color: #879280;
  display: block;
}
.verification-form {
  padding: 20px 17px 17px;
  border-radius: 12px;
}
.verification-form .field + .field {
  margin-top: 23px;
}
.verification-form .form-input {
  margin-top: 9px;
}
.privacy-tip {
  display: flex;
  gap: 6px;
  align-items: center;
  color: #8f9b86;
  font-size: calc(11px * var(--font-scale, 1));
  margin-top: 19px;
}
.submit-button {
  margin-top: 23px;
  width: 100%;
}
.verification-explanation {
  display: flex;
  flex-direction: column;
  gap: 9px;
  font-size: calc(12px * var(--font-scale, 1));
  color: #93a087;
  line-height: 1.9;
  padding: 28px 5px;
}
.explanation-title {
  font-size: calc(13px * var(--font-scale, 1));
  color: #6f8163;
  font-weight: 500;
}
.school-fixed,
.privacy-tip {
  font-size: calc(12px * var(--font-scale, 1));
}
.status-description,
.intro-copy,
.verification-explanation {
  font-size: calc(13px * var(--font-scale, 1));
}
</style>
