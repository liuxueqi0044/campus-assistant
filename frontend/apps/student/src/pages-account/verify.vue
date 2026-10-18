<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { ApiError } from '@/services/transport'
import {
  beginVerificationSubmission,
  beginVerificationUpload,
  canSubmitVerification,
  completeVerificationSubmission,
  failVerificationSubmission,
  resetVerificationFlow,
  retryVerificationFlow,
  selectVerificationMaterials,
  updateVerificationMaterial,
  verificationEvidenceRefs,
} from '@/features/account/verification'
import { go } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { VerificationMaterialStatus, VerificationStatus } from '@/types'

const editing = ref(false)
const flow = state.verificationFlow
const campus = computed(() => state.campuses.find((item) => item.id === state.user.campusId))
const canShowForm = computed(
  () => !['PENDING', 'VERIFIED'].includes(state.user.verification) || editing.value,
)
const submitting = computed(() => flow.phase === 'SUBMITTING')
const canSubmit = computed(() => canSubmitVerification(flow) && !submitting.value)
const applicationId = computed(() => flow.applicationId)

const statusCopy: Record<VerificationStatus, { title: string; description: string }> = {
  NONE: { title: '尚未认证', description: '提交材料后由有权审核人员处理，不会自动通过。' },
  PENDING: {
    title: '资料已提交，等待审核',
    description: '当前后端尚未提供申请详情查询；请以服务通知和后续资格核验为准。',
  },
  VERIFIED: {
    title: '学生身份已认证',
    description: '当前状态来自账号服务；认证材料不会在公开页面展示。',
  },
  REJECTED: { title: '本次申请未通过', description: '请根据可披露原因更新材料后重新提交。' },
  EXPIRED: { title: '学生身份已过期', description: '需要重新提交有效材料，历史订单入口仍会保留。' },
  REVOKED: { title: '学生身份已撤销', description: '当前账号不能继续使用需要认证的新增功能。' },
}

const flowCopy = computed(() => {
  const copies = {
    IDLE: '请选择材料后开始上传。',
    UNAVAILABLE: '材料上传服务尚未接通，当前不会收集文件或提交申请。',
    SELECTED: '材料已选择，等待上传。',
    UPLOADING: '材料正在上传到私有存储。',
    VERIFYING: '服务端正在验证材料类型和完整性。',
    READY: '全部材料已验证，可以提交审核。',
    SUBMITTING: '正在提交认证申请，请勿重复操作。',
    PENDING: '认证申请已进入审核队列。',
    FAILED: flow.errorMessage || '材料或申请未完成，请按提示恢复。',
    RESULT_UNKNOWN: '网络中断，提交结果尚未确认；请勿创建新的申请。',
  }
  return copies[flow.phase]
})

const submitLabel = computed(() => {
  if (submitting.value) return '正在提交…'
  if (flow.phase === 'UNAVAILABLE') return '材料服务暂不可用'
  if (flow.phase === 'RESULT_UNKNOWN') return '提交结果待确认'
  return state.demo ? '提交演示申请' : '提交认证'
})

function materialStatusText(status: VerificationMaterialStatus) {
  return {
    SELECTED: '已选择',
    UPLOADING: '上传中',
    VERIFYING: '验证中',
    READY: '已验证',
    FAILED: '失败',
  }[status]
}

function prepareDemoMaterial() {
  resetVerificationFlow(flow, true)
  selectVerificationMaterials(flow, [
    { localId: 'demo-student-card', name: '演示学生证（非真实文件）' },
  ])
  beginVerificationUpload(flow)
  updateVerificationMaterial(flow, 'demo-student-card', 'VERIFYING')
  updateVerificationMaterial(flow, 'demo-student-card', 'READY', {
    assetId: 'demo-media-reference',
  })
}

function clearMaterials() {
  resetVerificationFlow(flow, state.demo)
}

function cancelEdit() {
  editing.value = false
  clearMaterials()
}

async function submit() {
  if (!canSubmit.value) return
  const selectedCampus = campus.value
  if (!selectedCampus)
    return failVerificationSubmission(
      flow,
      new ApiError('SCOPE_REQUIRED', '请先选择认证所属校区。', undefined, 'USER_FIX'),
    )
  beginVerificationSubmission(flow)
  try {
    const application = await api.verify({
      schoolId: selectedCampus.schoolId || (state.demo ? 'demo-school' : ''),
      campusId: selectedCampus.id,
      method: 'STUDENT_CARD',
      evidenceRefs: verificationEvidenceRefs(flow),
    })
    completeVerificationSubmission(flow, application)
    editing.value = false
  } catch (error) {
    failVerificationSubmission(flow, error)
  }
}

function retry() {
  retryVerificationFlow(flow)
  if (canSubmitVerification(flow)) void submit()
}

onUnmounted(() => {
  if (flow.phase !== 'PENDING') resetVerificationFlow(flow, state.demo)
})
</script>

<template>
  <view class="page detail-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="学生认证" subtitle="材料私有保存，审核结果来自服务端" back />
    <view class="content">
      <DemoNotice />
      <template v-if="state.user.loggedIn">
        <view class="verification-school surface">
          <view class="school-icon"><AppIcon name="home" :size="26" /></view>
          <view class="school-copy">
            <text class="school-name">
              {{ state.user.school || campus?.school || '当前账号学校' }}
            </text>
            <text class="muted caption">{{ campus?.name || '请先选择校区' }}</text>
          </view>
          <text class="school-fixed">以账号与服务端范围为准</text>
        </view>

        <view v-if="!canShowForm" class="verification-status surface">
          <view :class="['status-symbol', { pending: state.user.verification === 'PENDING' }]">
            <AppIcon :name="state.user.verification === 'PENDING' ? 'time' : 'check'" :size="30" />
          </view>
          <text class="status-title">{{ statusCopy[state.user.verification].title }}</text>
          <text class="status-description">
            {{ statusCopy[state.user.verification].description }}
          </text>
          <text v-if="applicationId" class="application-id">申请编号：{{ applicationId }}</text>
          <button class="btn btn-secondary status-action" @click="go('/pages-message/notices')">
            查看服务通知
          </button>
          <button
            v-if="state.demo && state.user.verification === 'VERIFIED'"
            class="text-button"
            @click="editing = true"
          >
            体验认证失败恢复
          </button>
        </view>

        <template v-else>
          <view class="form-intro">
            <text class="intro-title">{{ statusCopy[state.user.verification].title }}</text>
            <text class="intro-copy">
              {{
                state.demo
                  ? '只使用明确标记的演示材料，不会上传真实文件或获得真实资格。'
                  : '当前仅支持学生证材料。上传完成并由服务端验证后，才能提交审核。'
              }}
            </text>
          </view>

          <view class="material-card surface">
            <view class="material-heading">
              <view class="material-icon"><AppIcon name="secured" :size="22" /></view>
              <view class="material-title-copy">
                <text class="material-title">学生证材料</text>
                <text class="material-subtitle">1–10 份 · 私有存储 · 不接受公开 URL</text>
              </view>
              <text :class="['phase-badge', flow.phase.toLowerCase()]">{{ flow.phase }}</text>
            </view>
            <text class="flow-copy">{{ flowCopy }}</text>

            <view v-if="flow.materials.length" class="material-list">
              <view v-for="material in flow.materials" :key="material.localId" class="material-row">
                <AppIcon name="image" :size="19" />
                <view class="material-name-copy">
                  <text class="material-name">{{ material.name }}</text>
                  <text v-if="material.errorMessage" class="material-error">
                    {{ material.errorMessage }}
                  </text>
                </view>
                <text :class="['material-status', material.status.toLowerCase()]">
                  {{ materialStatusText(material.status) }}
                </text>
              </view>
            </view>

            <view v-if="flow.phase === 'UNAVAILABLE'" class="unavailable-note">
              <AppIcon name="time" :size="17" />
              <text>
                上传票据、字节上传、完成校验和用途绑定接口仍在接入。本页不会让你手工填写媒体
                ID，也不会显示认证成功。
              </text>
            </view>

            <view v-if="state.demo" class="material-actions">
              <button class="btn btn-secondary compact-button" @click="prepareDemoMaterial">
                使用演示材料
              </button>
              <button v-if="flow.materials.length" class="text-button" @click="clearMaterials">
                清除演示材料
              </button>
            </view>
          </view>

          <view
            v-if="flow.phase === 'FAILED' || flow.phase === 'RESULT_UNKNOWN'"
            class="failure-card"
          >
            <text class="failure-title">
              {{ flow.phase === 'RESULT_UNKNOWN' ? '提交结果待确认' : '认证流程未完成' }}
            </text>
            <text>{{ flow.errorMessage }}</text>
            <text v-if="flow.traceId" class="trace-id">Trace ID：{{ flow.traceId }}</text>
            <button
              v-if="flow.phase !== 'RESULT_UNKNOWN' && canSubmitVerification(flow)"
              class="btn btn-secondary compact-button"
              @click="retry"
            >
              使用原材料重试
            </button>
          </view>

          <button class="btn btn-primary submit-button" :disabled="!canSubmit" @click="submit">
            {{ submitLabel }}
          </button>
          <button v-if="editing" class="text-button" :disabled="submitting" @click="cancelEdit">
            取消，保留当前认证状态
          </button>
        </template>

        <view class="verification-explanation">
          <text class="explanation-title">登录、浏览学校与学生认证相互独立</text>
          <text>切换浏览校区不会增加成员资格；审核通过前，页面不会开放需要认证的新增操作。</text>
          <text>当前没有申请详情、撤销或重新提交查询接口；网络未知时不会自动新建第二份申请。</text>
        </view>
      </template>
      <template v-else>
        <EmptyState
          title="先登录，再认证"
          description="认证申请只能关联当前登录账号。"
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
.school-icon,
.material-icon {
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
.school-copy,
.material-title-copy,
.material-name-copy {
  min-width: 0;
  flex: 1;
}
.school-name,
.material-title {
  display: block;
  font-weight: 600;
  font-size: calc(16px * var(--font-scale, 1));
  margin-bottom: 5px;
  overflow-wrap: anywhere;
}
.school-fixed {
  margin-left: auto;
  font-size: calc(12px * var(--font-scale, 1));
  color: #97a18e;
  max-width: 84px;
  line-height: 1.5;
  text-align: right;
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
.status-description,
.flow-copy {
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.85;
  display: block;
  color: #86927f;
  margin-top: 12px;
}
.application-id,
.trace-id {
  display: block;
  margin-top: 10px;
  color: #879280;
  font-size: calc(12px * var(--font-scale, 1));
  overflow-wrap: anywhere;
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
.material-card {
  padding: 18px 16px;
  border-radius: 12px;
}
.material-heading,
.material-row,
.unavailable-note {
  display: flex;
  align-items: center;
  gap: 11px;
}
.material-subtitle,
.material-error {
  display: block;
  color: #8c9788;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.6;
}
.phase-badge,
.material-status {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 4px 8px;
  background: #f1f2ed;
  color: #717d70;
  font-size: calc(10px * var(--font-scale, 1));
}
.phase-badge.ready,
.material-status.ready {
  background: #e7f2e7;
  color: #4f7957;
}
.phase-badge.failed,
.material-status.failed,
.phase-badge.result_unknown {
  background: #f8ebe7;
  color: #9b5d4e;
}
.material-list {
  margin-top: 16px;
  border-top: 1px solid var(--color-line);
}
.material-row {
  padding: 14px 0;
  border-bottom: 1px solid var(--color-line);
}
.material-name {
  display: block;
  font-size: calc(14px * var(--font-scale, 1));
  overflow-wrap: anywhere;
}
.unavailable-note {
  align-items: flex-start;
  margin-top: 16px;
  padding: 13px;
  border-radius: 9px;
  background: #f4f1e9;
  color: #85795e;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.75;
}
.material-actions {
  margin-top: 17px;
}
.compact-button {
  min-height: 44px;
  font-size: calc(13px * var(--font-scale, 1));
}
.failure-card {
  margin-top: 14px;
  padding: 15px;
  border-radius: 10px;
  background: #f9eeea;
  color: #8e5b50;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.7;
}
.failure-title {
  font-weight: 600;
  color: #784b43;
}
.submit-button {
  margin-top: 23px;
  width: 100%;
}
.verification-explanation {
  display: flex;
  flex-direction: column;
  gap: 9px;
  font-size: calc(13px * var(--font-scale, 1));
  color: #93a087;
  line-height: 1.9;
  padding: 28px 5px;
}
.explanation-title {
  font-size: calc(13px * var(--font-scale, 1));
  color: #6f8163;
  font-weight: 500;
}
</style>
