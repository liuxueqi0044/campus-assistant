<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import CourseFields from '@/features/timetable/CourseFields.vue'
import {
  courseConflicts,
  dayNames,
  demoImportCourses,
  formFromCourse,
  validateCourse,
  type CourseForm,
} from '@/features/timetable/helpers'
import { api } from '@/services/api'
import { state } from '@/state'
import { go, requireLogin } from '@/utils'
import type { Course } from '@/types'

const stage = ref<'source' | 'review' | 'success'>('source')
const error = ref('')
const fileName = ref('')
const fileKind = ref<'image' | 'file'>('image')
const selecting = ref(false)
const busy = ref(false)
const checked = ref(false)
const submitted = ref(false)
const expanded = ref(-1)
const savedCount = ref(0)
const drafts = ref<{ id: string; form: CourseForm }[]>([])
const validations = computed(() => drafts.value.map((draft) => validateCourse(draft.form)))
const missingCount = computed(() => validations.value.filter((item) => !item.data).length)
const candidates = computed(() =>
  validations.value.flatMap((result, index) =>
    result.data ? [{ ...result.data, id: drafts.value[index]!.id } as Course] : [],
  ),
)
const collisions = computed(() =>
  candidates.value.filter(
    (course, i, all) => courseConflicts(course, [...state.courses, ...all.slice(0, i)]).length > 0,
  ),
)
function selectionFailure(cause: { errMsg?: string }) {
  if (!cause.errMsg?.includes('cancel'))
    error.value = '文件选择失败，请重试，或使用下方示例体验核对流程。'
  selecting.value = false
}
function pickImage() {
  if (selecting.value) return
  selecting.value = true
  error.value = ''
  uni.chooseImage({
    count: 1,
    sizeType: ['original'],
    sourceType: ['album'],
    success(result) {
      const files = result.tempFiles
      const file = (Array.isArray(files) ? files : [files])[0]
      if (file && typeof file !== 'string' && file.size > 10 * 1024 * 1024) {
        error.value = '请选择不超过 10 MB 的课表截图。'
        return
      }
      fileKind.value = 'image'
      fileName.value = '已选择 1 张课表截图'
    },
    fail: selectionFailure,
    complete() {
      selecting.value = false
    },
  })
}
function pickFile() {
  if (selecting.value) return
  selecting.value = true
  error.value = ''
  const handleFiles = (files: { name?: string; size?: number }[]) => {
    const file = files[0]
    if (!file) return
    if ((file.size || 0) > 10 * 1024 * 1024) {
      error.value = '请选择不超过 10 MB 的课表文件。'
      return
    }
    if (!/\.(xlsx|csv)$/i.test(file.name || '')) {
      error.value = '演示选择器仅接受 .xlsx 或 .csv 文件。'
      return
    }
    fileKind.value = 'file'
    fileName.value = file.name || '已选择课表文件'
  }
  // #ifdef H5
  uni.chooseFile({
    count: 1,
    extension: ['.xlsx', '.csv'],
    success: (result) =>
      handleFiles(Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]),
    fail: selectionFailure,
    complete() {
      selecting.value = false
    },
  })
  // #endif
  // #ifdef MP-WEIXIN
  uni.chooseMessageFile({
    count: 1,
    type: 'file',
    extension: ['xlsx', 'csv'],
    success: (result) =>
      handleFiles(Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]),
    fail: selectionFailure,
    complete() {
      selecting.value = false
    },
  })
  // #endif
  // #ifndef H5
  // #ifndef MP-WEIXIN
  selecting.value = false
  error.value = '当前平台暂不支持文件选择，请选择截图或查看示例。'
  // #endif
  // #endif
}
function loadExample() {
  drafts.value = demoImportCourses().map((course) => ({
    id: course.id,
    form: formFromCourse(course),
  }))
  stage.value = 'review'
  expanded.value = -1
  submitted.value = false
  checked.value = false
  error.value = ''
}
function updateDraft(index: number, value: CourseForm) {
  drafts.value[index]!.form = value
  checked.value = false
  error.value = ''
}
async function removeDraft(index: number) {
  const result = await uni.showModal({
    title: '移除这条草稿？',
    content: '只移除本次导入候选，已有课表不会改变。',
    confirmText: '移除',
    confirmColor: '#07855D',
  })
  if (result.confirm) {
    drafts.value.splice(index, 1)
    expanded.value = -1
    checked.value = false
  }
}
async function changeSource() {
  if (busy.value) return
  const result = await uni.showModal({
    title: '返回选择来源？',
    content: '当前示例草稿的修改将被丢弃，已有课表不受影响。',
    confirmText: '返回',
    confirmColor: '#07855D',
  })
  if (result.confirm) {
    stage.value = 'source'
    drafts.value = []
    error.value = ''
  }
}
async function commit() {
  if (busy.value) return
  error.value = ''
  submitted.value = true
  if (!state.demo) {
    error.value = '真实模式暂未接通课表解析，示例仅供预览，请使用手动添加课程。'
    return
  }
  if (!requireLogin()) return
  if (!drafts.value.length) {
    error.value = '草稿已清空，请返回重新加载示例。'
    return
  }
  if (missingCount.value) {
    expanded.value = validations.value.findIndex((item) => !item.data)
    error.value = '请先补全标注的课程信息，再确认导入。'
    return
  }
  if (!checked.value) {
    error.value = '请先核对课程信息，并勾选下方确认项。'
    return
  }
  if (collisions.value.length) {
    const result = await uni.showModal({
      title: '部分时间与已有安排重叠',
      content: '请确认这些课程确实需要同时保留。导入将追加，不覆盖已有课表。',
      confirmText: '保留并导入',
      confirmColor: '#07855D',
    })
    if (!result.confirm) return
  }
  busy.value = true
  try {
    await api.commitImport(candidates.value)
    savedCount.value = candidates.value.length
    stage.value = 'success'
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}
function openTimetable() {
  uni.reLaunch({ url: '/pages/timetable/index' })
}
</script>

<template>
  <view class="page detail-page import-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="导入课表" back />
    <view class="content">
      <DemoNotice />
      <view class="steps">
        <view class="step" :class="{ current: stage === 'source', done: stage !== 'source' }">
          <text class="step-number">1</text>
          <text>选择来源</text>
        </view>
        <view class="step-line" />
        <view class="step" :class="{ current: stage === 'review', done: stage === 'success' }">
          <text class="step-number">2</text>
          <text>核对课程</text>
        </view>
        <view class="step-line" />
        <view class="step" :class="{ current: stage === 'success' }">
          <text class="step-number">3</text>
          <text>完成</text>
        </view>
      </view>
      <template v-if="stage === 'source'">
        <view class="intro">
          <text class="intro-title">从现有课表开始</text>
          <text class="intro-description">准备一张清晰截图，或一份课表文件。</text>
        </view>
        <view class="source-options">
          <button class="source-card" :disabled="selecting" @click="pickImage">
            <view class="source-icon image-icon"><AppIcon name="picture" :size="24" /></view>
            <view class="source-content">
              <text class="source-title">选择课表截图</text>
              <text class="source-detail">相册中的图片 · 最大 10 MB</text>
            </view>
            <AppIcon name="arrow-right" :size="16" color="#98a59b" />
          </button>
          <button class="source-card" :disabled="selecting" @click="pickFile">
            <view class="source-icon file-icon"><AppIcon name="file" :size="23" /></view>
            <view class="source-content">
              <text class="source-title">选择课表文件</text>
              <text class="source-detail">XLSX / CSV · 最大 10 MB</text>
            </view>
            <AppIcon name="arrow-right" :size="16" color="#98a59b" />
          </button>
        </view>
        <view v-if="fileName" class="selected-file">
          <AppIcon :name="fileKind === 'image' ? 'picture' : 'file'" :size="19" />
          <view>
            <text class="file-name">{{ fileName }}</text>
            <text class="file-caption">仅在本机选择，尚未上传或解析</text>
          </view>
          <button class="remove-file" aria-label="移除所选文件" @click="fileName = ''">
            <AppIcon name="close" :size="15" />
          </button>
        </view>
        <view class="capability-note">
          <view class="note-heading">
            <AppIcon name="info-circle" :size="17" />
            <text>当前未接入 OCR 和文件解析服务</text>
          </view>
          <text>
            选择文件后不会自动生成课程，也不会上传你的文件。你可以主动加载示例，体验核对、纠错和确认流程。
          </text>
        </view>
        <view v-if="error" class="form-error error-box">{{ error }}</view>
        <button class="btn btn-primary example-button" @click="loadExample">
          加载示例课表，体验核对
        </button>
        <button class="manual-button" @click="go('/pages-timetable/course')">
          也可以手动添加课程
          <AppIcon name="arrow-right" :size="13" />
        </button>
        <view class="privacy-note">
          <AppIcon name="lock-on" :size="14" />
          <text>无需提供教务账号或密码</text>
        </view>
      </template>
      <template v-else-if="stage === 'review'">
        <view class="review-heading">
          <view>
            <text class="intro-title">核对这 {{ drafts.length }} 门课程</text>
            <text class="intro-description">示例草稿 · 与选择的文件内容无关</text>
          </view>
          <text class="review-badge">待确认</text>
        </view>
        <view class="review-notice">
          <text>请检查课程、周次和教室。示例中有一处地点缺失，请填写后再确认。</text>
          <text class="append-note">
            将追加到当前课表，不覆盖已有 {{ state.courses.length }} 门课程。
          </text>
        </view>
        <view v-if="error" class="form-error error-box">{{ error }}</view>
        <view v-for="(draft, index) in drafts" :key="draft.id" class="draft-card">
          <view class="draft-head">
            <view class="draft-color" :style="{ backgroundColor: draft.form.color }">
              <text>{{ index + 1 }}</text>
            </view>
            <view class="draft-summary">
              <text class="draft-title">{{ draft.form.title || '未命名课程' }}</text>
              <text class="draft-time">
                {{ dayNames[draft.form.day - 1] }} · 第 {{ draft.form.start }}—{{
                  draft.form.start + draft.form.duration - 1
                }}
                节
              </text>
            </view>
            <button class="edit-link" @click="expanded = expanded === index ? -1 : index">
              {{ expanded === index ? '收起' : '修改' }}
            </button>
          </view>
          <view class="draft-meta">
            <text :class="{ missing: !draft.form.room.trim() }">
              {{ draft.form.room || '地点缺失 · 请填写' }}
            </text>
            <text>第 {{ draft.form.weeksText }} 周</text>
          </view>
          <template v-if="expanded === index">
            <CourseFields
              :model-value="draft.form"
              :errors="submitted ? validations[index]?.errors : {}"
              @update:model-value="updateDraft(index, $event)"
            />
            <button class="remove-draft" @click="removeDraft(index)">移除这条草稿</button>
          </template>
        </view>
        <view v-if="!drafts.length" class="no-drafts">草稿已清空，可以返回重新加载示例。</view>
        <view v-if="collisions.length" class="collision-note">
          {{ collisions.length }} 门课程与已有安排存在时间重叠，确认时将再次提示。
        </view>
        <button
          class="confirm-check"
          :class="{ checked }"
          :disabled="busy"
          @click="checked = !checked"
        >
          <view class="check-box"><text v-if="checked">✓</text></view>
          <text>
            我已核对课程、上课地点和周次，确认将示例课程加入{{ state.demo ? '演示' : '当前' }}课表。
          </text>
        </button>
        <view v-if="!state.demo" class="form-error error-box">
          真实模式仅可预览示例，暂不支持提交示例草稿。
        </view>
        <button
          class="btn btn-primary"
          :loading="busy"
          :disabled="busy || !state.demo || !drafts.length"
          @click="commit"
        >
          {{ busy ? '正在保存…' : `确认追加 ${drafts.length} 门课程` }}
        </button>
        <button class="return-source" :disabled="busy" @click="changeSource">返回选择来源</button>
      </template>
      <view v-else class="success-content">
        <view class="success-icon"><AppIcon name="check" :size="34" /></view>
        <text class="success-title">课程已加入课表</text>
        <view class="success-description">
          <view>已追加 {{ savedCount }} 门示例课程。</view>
          <view>原有课程保持不变，随时可以编辑或删除。</view>
        </view>
        <button class="btn btn-primary" @click="openTimetable">查看我的课表</button>
        <text class="success-caption">本次为演示草稿导入，未读取所选文件。</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.import-page {
  padding-bottom: calc(25px + env(safe-area-inset-bottom));
}
.steps {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 25px 1px 21px;
}
.step {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: calc(12px * var(--font-scale, 1));
  color: #9aa59d;
  white-space: nowrap;
}
.step-number {
  width: 22px;
  height: 22px;
  background: #e8ece9;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: calc(11px * var(--font-scale, 1));
}
.step.current,
.step.done {
  color: #07855d;
}
.step.current .step-number {
  color: white;
  background: #07855d;
}
.step.done .step-number {
  color: #07855d;
  background: #e2eee7;
}
.step-line {
  height: 1px;
  background: #e0e7e2;
  flex: 1;
}
.intro {
  padding: 8px 0 21px;
}
.intro-title {
  font-size: calc(18px * var(--font-scale, 1));
  font-weight: 500;
  display: block;
}
.intro-description {
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.7;
  margin-top: 6px;
  display: block;
  color: #87948b;
}
.source-options {
  background: #fff;
  border-radius: 12px;
  padding: 0 15px;
}
.source-card {
  display: flex;
  align-items: center;
  text-align: left;
  width: 100%;
  padding: 20px 0;
  margin: 0;
  background: transparent;
  gap: 13px;
  border-bottom: 1px solid #edf1ee;
  line-height: 1.5;
  border-radius: 0;
}
.source-card:last-child {
  border-bottom: 0;
}
.source-card::after {
  border: 0;
}
.source-icon {
  flex-shrink: 0;
  width: 46px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
}
.image-icon {
  background: #eaf3ed;
  color: #408162;
}
.file-icon {
  background: #ebeff7;
  color: #7487ad;
}
.source-content {
  flex: 1;
  min-width: 0;
}
.source-title {
  display: block;
  font-size: calc(16px * var(--font-scale, 1));
  color: #2c4537;
}
.source-detail {
  display: block;
  font-size: calc(11px * var(--font-scale, 1));
  color: #8b9890;
  margin-top: 5px;
}
.capability-note {
  margin: 20px 0;
  padding: 15px;
  border: 1px solid #e1e9e3;
  background: #f0f5f1;
  border-radius: 10px;
  font-size: calc(13px * var(--font-scale, 1));
  color: #7c8d80;
  line-height: 1.9;
}
.note-heading {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: calc(13px * var(--font-scale, 1));
  color: #4c6b56;
  font-weight: 500;
  margin-bottom: 7px;
}
.example-button {
  margin-top: 23px;
}
.manual-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: transparent;
  color: #678674;
  font-size: calc(13px * var(--font-scale, 1));
  margin: 8px 0 0;
  padding: 10px;
}
.manual-button::after {
  border: 0;
}
.privacy-note {
  margin: 33px 0 0;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 5px;
  font-size: calc(12px * var(--font-scale, 1));
  color: #9aa59d;
}
.selected-file {
  margin-top: 15px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  padding: 13px;
  border-radius: 9px;
  color: #49795b;
}
.selected-file > view {
  flex: 1;
  min-width: 0;
}
.file-name {
  display: block;
  font-size: calc(13px * var(--font-scale, 1));
  word-break: break-all;
}
.file-caption {
  display: block;
  font-size: calc(11px * var(--font-scale, 1));
  color: #94a096;
  margin-top: 5px;
}
.remove-file {
  background: transparent;
  padding: 0;
  margin: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.remove-file::after {
  border: 0;
}
.error-box {
  display: block;
  background: #fff0ec;
  padding: 13px;
  border-radius: 8px;
  margin: 12px 0;
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.7;
}
.review-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 8px 0 13px;
}
.review-badge {
  color: #957644;
  background: #f8efd9;
  padding: 4px 8px;
  border-radius: 5px;
  font-size: calc(11px * var(--font-scale, 1));
  white-space: nowrap;
}
.review-notice {
  font-size: calc(13px * var(--font-scale, 1));
  color: #849087;
  line-height: 1.8;
  margin-bottom: 18px;
}
.append-note {
  display: block;
  font-size: calc(12px * var(--font-scale, 1));
  margin-top: 5px;
  color: #658170;
}
.draft-card {
  background: #fff;
  border-radius: 11px;
  margin-bottom: 13px;
  overflow: hidden;
}
.draft-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 15px 11px;
}
.draft-color {
  width: 34px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  color: #56715e;
  font-size: calc(14px * var(--font-scale, 1));
  flex-shrink: 0;
}
.draft-summary {
  flex: 1;
  min-width: 0;
}
.draft-title {
  display: block;
  font-size: calc(15px * var(--font-scale, 1));
  color: #31493a;
  line-height: 1.6;
}
.draft-time {
  font-size: calc(12px * var(--font-scale, 1));
  color: #919e94;
  display: block;
  margin-top: 4px;
}
.edit-link {
  flex-shrink: 0;
  background: transparent;
  padding: 0 0 0 4px;
  margin: 0;
  color: #07855d;
  font-size: calc(13px * var(--font-scale, 1));
  min-height: 44px;
}
.edit-link::after {
  border: 0;
}
.draft-meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 0 15px;
  padding: 0 0 16px 44px;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.6;
  color: #8b9890;
}
.draft-meta .missing {
  color: #ae7840;
}
.remove-draft {
  margin: 0 16px 12px;
  padding: 0;
  background: #faf7f6;
  line-height: 44px;
  font-size: calc(12px * var(--font-scale, 1));
  color: #b16e61;
}
.remove-draft::after {
  border: 0;
}
.no-drafts {
  text-align: center;
  padding: 35px 10px;
  font-size: calc(14px * var(--font-scale, 1));
  color: #819087;
}
.confirm-check {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  background: transparent;
  margin: 14px 0;
  padding: 10px 0;
  text-align: left;
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.8;
  color: #7f8f83;
}
.confirm-check::after {
  border: 0;
}
.check-box {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border: 1px solid #c6d3c9;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 3px;
  font-size: calc(12px * var(--font-scale, 1));
}
.checked .check-box {
  border-color: #07855d;
  background: #07855d;
  color: #fff;
}
.return-source {
  background: transparent;
  color: #8b9890;
  font-size: calc(13px * var(--font-scale, 1));
  margin-top: 9px;
  line-height: 46px;
}
.return-source::after {
  border: 0;
}
.collision-note {
  font-size: calc(12px * var(--font-scale, 1));
  color: #987a45;
  padding: 13px;
  background: #fff4de;
  line-height: 1.8;
  border-radius: 8px;
}
.success-content {
  padding: 44px 12px 20px;
  text-align: center;
}
.success-icon {
  width: 74px;
  height: 74px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #07855d;
  background: #e4f1e9;
  margin: 0 auto 24px;
}
.success-title {
  display: block;
  font-size: calc(21px * var(--font-scale, 1));
  font-weight: 500;
  color: #2e4a38;
}
.success-description {
  display: block;
  font-size: calc(14px * var(--font-scale, 1));
  color: #829087;
  line-height: 1.9;
  margin: 15px 0 30px;
}
.success-caption {
  display: block;
  color: #9aa59d;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.8;
  margin-top: 17px;
}
</style>
