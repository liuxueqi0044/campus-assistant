<template>
  <section class="page">
    <div class="page-heading">
      <div>
        <h1>认证审核</h1>
        <p>队列与详情始终绑定当前学校、校区和授权版本；切换范围会立即清空旧选择。</p>
      </div>
      <el-button
        :disabled="!canQuery || outcomeUnknown"
        :loading="queueLoading"
        @click="loadQueue"
      >
        刷新队列
      </el-button>
    </div>

    <el-alert
      v-if="!verificationQueryCapability.available"
      title="认证查询后端尚未就绪"
      :description="verificationQueryCapability.reason"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-alert
      v-else-if="!scope.canReviewVerification"
      title="当前范围不能审核认证"
      description="需要认证审核权限，以及当前全校或所选校区的 VERIFICATION_REVIEW 动作授权。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-alert
      v-if="errorMessage"
      class="feedback"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
    />
    <el-alert
      v-if="noticeMessage"
      class="feedback"
      :title="noticeMessage"
      type="success"
      :closable="false"
      show-icon
    />

    <div class="review-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-heading">
            <strong>待审申请</strong>
            <span class="muted">{{ queue.total }} 项</span>
          </div>
        </template>
        <el-table
          v-if="queue.list.length"
          :data="queue.list"
          row-key="applicationId"
          highlight-current-row
          @row-click="selectApplication"
        >
          <el-table-column prop="applicationId" label="申请 ID" min-width="155" />
          <el-table-column prop="method" label="方式" min-width="120" />
          <el-table-column prop="submittedAt" label="提交时间" min-width="190" />
          <el-table-column prop="version" label="版本" width="72" />
        </el-table>
        <el-empty v-else description="当前范围没有已加载的待审申请。" />
        <div class="pager">
          <el-button
            :disabled="queue.page <= 1 || queueLoading || outcomeUnknown"
            @click="changePage(-1)"
          >
            上一页
          </el-button>
          <span>第 {{ queue.page }} 页</span>
          <el-button
            :disabled="queue.page * queue.pageSize >= queue.total || queueLoading || outcomeUnknown"
            @click="changePage(1)"
          >
            下一页
          </el-button>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-heading">
            <strong>申请详情</strong>
            <span v-if="detail" class="muted">版本 {{ detail.version }}</span>
          </div>
        </template>
        <div v-if="detail" class="detail-stack">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="申请 ID">{{ detail.applicationId }}</el-descriptions-item>
            <el-descriptions-item label="学校 / 校区">
              {{ detail.schoolId }} / {{ detail.campusId }}
            </el-descriptions-item>
            <el-descriptions-item label="认证方式">{{ detail.method }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ detail.submittedAt }}</el-descriptions-item>
          </el-descriptions>

          <div>
            <strong>材料引用</strong>
            <p class="muted">详情只接收 assetId；短令牌读取在浏览器联调前保持关闭。</p>
            <div class="asset-list">
              <el-tag v-for="item in detail.evidence" :key="item.assetId" effect="plain">
                {{ item.assetId }}
              </el-tag>
            </div>
          </div>

          <div class="decision-form">
            <strong>审核决定</strong>
            <div class="native-options" role="radiogroup" aria-label="审核决定">
              <label>
                <input
                  v-model="form.decision"
                  type="radio"
                  value="APPROVE"
                  :disabled="outcomeUnknown"
                />
                通过
              </label>
              <label>
                <input
                  v-model="form.decision"
                  type="radio"
                  value="REJECT"
                  :disabled="outcomeUnknown"
                />
                拒绝
              </label>
            </div>
            <el-input
              v-model="form.reason"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              :disabled="outcomeUnknown"
              placeholder="请填写本次审核的真实理由（至少 2 个字符，不会预填）"
            />
            <label class="confirm-line">
              <input v-model="form.confirmed" type="checkbox" :disabled="outcomeUnknown" />
              已核对当前申请 ID、学校/校区、版本、材料与决定
            </label>
            <div v-if="outcomeUnknown" class="unknown-box">
              <strong>上次提交结果未知</strong>
              <p>先核对当前详情；若仍为同一待审版本，只能使用原 Idempotency-Key 重试。</p>
              <div class="button-row">
                <el-button :loading="detailLoading" @click="reconcileUnknown">核对当前结果</el-button>
                <el-button type="primary" :loading="submitting" @click="submitReview">
                  以原意图重试
                </el-button>
              </div>
            </div>
            <el-button
              v-else
              type="primary"
              :disabled="!canSubmit"
              :loading="submitting"
              @click="submitReview"
            >
              提交审核
            </el-button>
          </div>
        </div>
        <el-empty v-else :description="detailLoading ? '正在加载详情' : '从左侧选择待审申请。'" />
      </el-card>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { AdminApiError } from '@/services/http'
import { CampusClientCapabilityError, createCampusCommandIntent } from '@/api/campus/client'
import {
  getVerificationDetail,
  getVerificationQueue,
  reviewVerification,
  verificationQueryCapability,
  type ReviewDecision,
  type VerificationDetail,
  type VerificationQueueItem,
  type VerificationQueuePage
} from '@/api/campus/verification'
import { useCampusScopeStore } from '@/stores/campusScope'

const scope = useCampusScopeStore()
const queue = reactive<VerificationQueuePage>({ list: [], total: 0, page: 1, pageSize: 20 })
const detail = ref<VerificationDetail | null>(null)
const queueLoading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const outcomeUnknown = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const form = reactive<{ decision: ReviewDecision | ''; reason: string; confirmed: boolean }>({
  decision: '',
  reason: '',
  confirmed: false
})
let intent: ReturnType<typeof createCampusCommandIntent> | null = null
let intentSignature = ''

const canQuery = computed(
  () =>
    verificationQueryCapability.available &&
    scope.status === 'ready' &&
    Boolean(scope.selectedSchoolId) &&
    scope.canReviewVerification
)
const canSubmit = computed(
  () =>
    Boolean(
      detail.value?.status === 'PENDING' &&
        scope.canReviewVerification &&
        form.decision &&
        form.reason.trim().length >= 2 &&
        form.reason.trim().length <= 500 &&
        form.confirmed
    )
)

watch(
  () => scope.generation,
  () => {
    clearQueueAndDetail()
    if (canQuery.value) void loadQueue()
  }
)

async function loadQueue() {
  if (!canQuery.value || outcomeUnknown.value) return
  const selection = scope.captureSelection()
  queueLoading.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const result = await getVerificationQueue({
      schoolId: selection.schoolId,
      ...(selection.campusId ? { campusId: selection.campusId } : {}),
      page: queue.page,
      pageSize: queue.pageSize
    })
    if (!scope.isCurrent(selection)) return
    Object.assign(queue, result)
    clearDetail()
  } catch (cause) {
    if (scope.isCurrent(selection)) errorMessage.value = messageFor(cause)
  } finally {
    if (scope.isCurrent(selection)) queueLoading.value = false
  }
}

async function selectApplication(item: VerificationQueueItem) {
  if (!canQuery.value) return
  await loadDetail(item.applicationId, false)
}

async function loadDetail(applicationId: string, preserveUnknown: boolean) {
  const selection = scope.captureSelection()
  detailLoading.value = true
  errorMessage.value = ''
  try {
    const result = await getVerificationDetail(applicationId)
    if (!scope.isCurrent(selection)) return null
    if (
      result.schoolId !== selection.schoolId ||
      (selection.campusId && result.campusId !== selection.campusId)
    ) {
      throw new CampusClientCapabilityError(
        'CONTRACT_INVALID',
        '详情不属于当前学校或校区范围'
      )
    }
    detail.value = result
    if (!preserveUnknown) resetDecision()
    return result
  } catch (cause) {
    if (scope.isCurrent(selection)) {
      detail.value = null
      errorMessage.value = messageFor(cause)
    }
    return null
  } finally {
    if (scope.isCurrent(selection)) detailLoading.value = false
  }
}

async function submitReview() {
  if (!detail.value || (!outcomeUnknown.value && !canSubmit.value) || !form.decision) return
  const reason = form.reason.trim()
  const signature = [detail.value.applicationId, detail.value.version, form.decision, reason].join('|')
  if (!intent || intentSignature !== signature) {
    if (outcomeUnknown.value) return
    intent = createCampusCommandIntent('verification-review')
    intentSignature = signature
  }
  submitting.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const result = await reviewVerification(
      detail.value.applicationId,
      { decision: form.decision, reason, expectedVersion: detail.value.version },
      intent
    )
    outcomeUnknown.value = false
    intent = null
    intentSignature = ''
    const success = `申请 ${result.applicationId} 已处理为 ${result.status}`
    await loadQueue()
    noticeMessage.value = success
  } catch (cause) {
    if (cause instanceof AdminApiError && cause.outcomeUnknown) {
      outcomeUnknown.value = true
      errorMessage.value = '提交结果未知；已保留原审核意图，请先核对当前结果。'
    } else {
      intent = null
      intentSignature = ''
      form.confirmed = false
      errorMessage.value = messageFor(cause)
    }
  } finally {
    submitting.value = false
  }
}

async function reconcileUnknown() {
  if (!detail.value) return
  const oldId = detail.value.applicationId
  const oldVersion = detail.value.version
  const current = await loadDetail(oldId, true)
  if (!current) return
  if (current.status !== 'PENDING' || current.version !== oldVersion) {
    outcomeUnknown.value = false
    intent = null
    intentSignature = ''
    noticeMessage.value = '申请状态或版本已经变化，已停止原意图重试。'
    await loadQueue()
  } else {
    errorMessage.value = '当前仍是同一待审版本；如需重试，系统会继续使用原 Idempotency-Key。'
  }
}

function changePage(offset: number) {
  const next = queue.page + offset
  if (next < 1) return
  queue.page = next
  void loadQueue()
}

function clearQueueAndDetail() {
  Object.assign(queue, { list: [], total: 0, page: 1, pageSize: 20 })
  queueLoading.value = false
  clearDetail()
}

function clearDetail() {
  detail.value = null
  detailLoading.value = false
  resetDecision()
}

function resetDecision() {
  form.decision = ''
  form.reason = ''
  form.confirmed = false
  outcomeUnknown.value = false
  intent = null
  intentSignature = ''
}

function messageFor(cause: unknown) {
  if (cause instanceof AdminApiError) {
    return `${cause.message}${cause.traceId ? `（traceId: ${cause.traceId}）` : ''}`
  }
  return cause instanceof Error ? cause.message : '认证审核请求失败'
}
</script>

<style scoped>
.feedback,
.review-grid {
  margin-top: 16px;
}

.review-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.85fr);
  gap: 16px;
}

.card-heading,
.pager,
.button-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pager {
  justify-content: center;
  margin-top: 16px;
}

.detail-stack,
.decision-form {
  display: grid;
  gap: 16px;
}

.asset-list,
.native-options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.native-options label,
.confirm-line {
  display: flex;
  align-items: center;
  gap: 7px;
}

.confirm-line {
  line-height: 1.5;
}

.unknown-box {
  padding: 14px;
  border: 1px solid #e6a23c;
  border-radius: 8px;
  background: #fdf6ec;
}

.unknown-box p {
  margin: 8px 0 12px;
  line-height: 1.55;
}

@media (width <= 1080px) {
  .review-grid {
    grid-template-columns: 1fr;
  }
}
</style>
