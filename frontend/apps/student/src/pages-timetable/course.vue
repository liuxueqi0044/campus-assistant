<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import EmptyState from '@/components/EmptyState.vue'
import CourseFields from '@/features/timetable/CourseFields.vue'
import {
  courseConflicts,
  dayNames,
  formFromCourse,
  formatWeeks,
  newCourseForm,
  validateCourse,
} from '@/features/timetable/helpers'
import { state } from '@/state'
import { api } from '@/services/api'
import { back, go, requireLogin, toast } from '@/utils'

const id = ref('')
const loaded = ref(false)
const editing = ref(false)
const busy = ref(false)
const error = ref('')
const errors = ref<Record<string, string>>({})
const form = ref(newCourseForm())
const course = computed(() => state.courses.find((item) => item.id === id.value))
const isNew = computed(() => !id.value)
const showForm = computed(() => isNew.value || editing.value)
const conflicts = computed(() => {
  const result = validateCourse(form.value)
  return result.data ? courseConflicts(result.data, state.courses, id.value) : []
})
onLoad((query) => {
  id.value = typeof query?.id === 'string' ? query.id : ''
  loaded.value = true
  if (course.value) form.value = formFromCourse(course.value)
})
watch(course, (value) => {
  if (value && !editing.value) form.value = formFromCourse(value)
})
function edit() {
  if (!course.value) return
  form.value = formFromCourse(course.value)
  editing.value = true
  error.value = ''
  errors.value = {}
}
function cancelEdit() {
  editing.value = false
  error.value = ''
  errors.value = {}
  if (course.value) form.value = formFromCourse(course.value)
}
async function save() {
  if (busy.value || !requireLogin()) return
  errors.value = {}
  error.value = ''
  const result = validateCourse(form.value)
  errors.value = result.errors
  if (!result.data) {
    error.value = '还有信息需要核对，请查看下方标注。'
    return
  }
  if (conflicts.value.length) {
    const answer = await uni.showModal({
      title: '课程时间重叠',
      content: `与「${conflicts.value.map((item) => item.title).join('、')}」的部分周次重叠，确认仍要保存吗？`,
      confirmText: '仍然保存',
      confirmColor: '#07855D',
    })
    if (!answer.confirm) return
  }
  busy.value = true
  try {
    await api.saveCourse({ ...result.data, ...(id.value ? { id: id.value } : {}) })
    toast(isNew.value ? '课程已添加' : '课程已保存')
    back()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}
async function remove() {
  if (busy.value || !course.value || !requireLogin()) return
  const answer = await uni.showModal({
    title: '删除这门课程？',
    content: `「${course.value.title}」的全部上课周次将从课表移除。`,
    confirmText: '删除课程',
    confirmColor: '#B64E43',
  })
  if (!answer.confirm) return
  busy.value = true
  error.value = ''
  try {
    await api.deleteCourse(id.value)
    toast('课程已删除')
    back()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page detail-page course-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader :title="isNew ? '添加课程' : editing ? '编辑课程' : '课程详情'" back />
    <view class="content">
      <DemoNotice />
      <template v-if="loaded && (isNew || course)">
        <template v-if="showForm">
          <view class="intro">
            <text class="intro-title">{{ isNew ? '添加一门新的课程' : '修改课程安排' }}</text>
            <text class="intro-desc">课程仅自己可见，保存后更新个人课表。</text>
          </view>
          <view v-if="error" class="form-error error-box">{{ error }}</view>
          <CourseFields v-model="form" :errors="errors" />
          <view v-if="conflicts.length" class="conflict-box">
            <AppIcon name="info-circle" :size="17" />
            <text>
              与
              {{
                conflicts.map((item) => item.title).join('、')
              }}
              的部分上课时间重叠，保存前请确认。
            </text>
          </view>
          <view class="foot-note">
            每条课程安排对应一组星期、节次与教室。不同周次在不同教室上课时，可以分别添加。
          </view>
          <view class="form-actions">
            <button class="btn btn-primary" :loading="busy" :disabled="busy" @click="save">
              {{ busy ? '正在保存…' : '保存课程' }}
            </button>
            <button class="cancel-button" :disabled="busy" @click="isNew ? back() : cancelEdit()">
              取消
            </button>
          </view>
        </template>
        <template v-else-if="course">
          <view class="course-hero" :style="{ backgroundColor: course.color }">
            <view class="hero-icon"><AppIcon name="calendar" :size="23" /></view>
            <text class="hero-name">{{ course.title }}</text>
            <text class="hero-sub">
              {{ dayNames[course.day - 1] }} · 第 {{ course.start }}—{{
                course.start + course.duration - 1
              }}
              节
            </text>
          </view>
          <view class="detail-list surface">
            <view class="detail-row">
              <view class="detail-label">
                <AppIcon name="time" :size="18" />
                <text>上课时间</text>
              </view>
              <text class="detail-value">{{ course.time }}</text>
            </view>
            <view class="detail-row">
              <view class="detail-label">
                <AppIcon name="location" :size="18" />
                <text>上课地点</text>
              </view>
              <text class="detail-value">{{ course.room || '待核对' }}</text>
            </view>
            <view class="detail-row">
              <view class="detail-label">
                <AppIcon name="user" :size="18" />
                <text>授课教师</text>
              </view>
              <text class="detail-value">{{ course.teacher || '未填写' }}</text>
            </view>
            <view class="detail-row">
              <view class="detail-label">
                <AppIcon name="calendar" :size="18" />
                <text>上课周次</text>
              </view>
              <text class="detail-value weeks-value">第 {{ formatWeeks(course.weeks) }} 周</text>
            </view>
          </view>
          <view class="private-note">
            <AppIcon name="lock-on" :size="14" />
            <text>这是你的个人课表，不会出现在校园公开页面。</text>
          </view>
          <view v-if="error" class="form-error error-box">{{ error }}</view>
          <view class="form-actions">
            <button class="btn btn-primary" :disabled="busy" @click="edit">编辑课程</button>
            <button class="delete-button" :disabled="busy" :loading="busy" @click="remove">
              删除课程
            </button>
          </view>
        </template>
      </template>
      <view v-else-if="loaded" class="missing-course">
        <EmptyState
          title="课程不存在"
          description="这门课程可能已删除，返回课表查看当前安排。"
          icon="calendar"
        />
        <button class="btn btn-secondary" @click="go('/pages/timetable/index')">返回课表</button>
      </view>
      <view v-else class="foot-note">正在打开课程…</view>
    </view>
  </view>
</template>

<style scoped>
.course-page {
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
}
.intro {
  padding: 21px 0 18px;
}
.intro-title {
  display: block;
  font-size: calc(17px * var(--font-scale, 1));
  font-weight: 500;
}
.intro-desc {
  display: block;
  margin-top: 6px;
  font-size: calc(13px * var(--font-scale, 1));
  color: #819087;
  line-height: 1.6;
}
.form-actions {
  margin-top: 22px;
}
.cancel-button,
.delete-button {
  margin: 10px 0 0;
  background: transparent;
  color: #829088;
  font-size: calc(14px * var(--font-scale, 1));
  line-height: 48px;
  border-radius: 8px;
}
.cancel-button::after,
.delete-button::after {
  border: 0;
}
.delete-button {
  color: #b45e54;
}
.foot-note {
  color: #8b968f;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.8;
  padding: 15px 4px 0;
}
.conflict-box {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: calc(13px * var(--font-scale, 1));
  color: #8d6c37;
  background: #fff3dd;
  padding: 13px;
  border-radius: 9px;
  margin-top: 14px;
  line-height: 1.7;
}
.error-box {
  display: block;
  margin: 12px 0;
  padding: 12px;
  background: #fff0ec;
  border-radius: 8px;
}
.course-hero {
  margin-top: 20px;
  border-radius: 13px;
  padding: 24px 20px 25px;
  color: #365d46;
}
.hero-icon {
  background: #ffffff80;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  margin-bottom: 19px;
}
.hero-name {
  display: block;
  font-size: calc(22px * var(--font-scale, 1));
  font-weight: 500;
  line-height: 1.5;
}
.hero-sub {
  display: block;
  font-size: calc(14px * var(--font-scale, 1));
  margin-top: 10px;
  opacity: 0.8;
}
.detail-list {
  border-radius: 12px;
  padding: 0 16px;
  margin-top: 17px;
}
.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 15px;
  padding: 20px 0;
  border-bottom: 1px solid #eff2ef;
}
.detail-row:last-child {
  border: 0;
}
.detail-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: calc(14px * var(--font-scale, 1));
  color: #829087;
  flex-shrink: 0;
}
.detail-value {
  font-size: calc(14px * var(--font-scale, 1));
  color: #344b3e;
  line-height: 1.6;
  text-align: right;
}
.weeks-value {
  max-width: 62%;
}
.private-note {
  display: flex;
  gap: 6px;
  align-items: center;
  color: #8f9b92;
  font-size: calc(12px * var(--font-scale, 1));
  margin: 19px 4px;
  line-height: 1.7;
}
.missing-course {
  margin-top: 25px;
}
</style>
