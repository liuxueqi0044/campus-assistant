<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import BottomNav from '@/components/BottomNav.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import EmptyState from '@/components/EmptyState.vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { go } from '@/utils'
import type { Course } from '@/types'
import { dayNames, periods, sameDate, weekDates } from '@/features/timetable/helpers'

const mode = ref<'day' | 'week'>('day')
const now = new Date()
const selectedDay = ref(now.getDay() === 0 ? 7 : now.getDay())
const error = ref('')
const refreshing = ref(false)
const dates = computed(() => weekDates(state.termStart, state.week))
const selectedDate = computed(() => dates.value[selectedDay.value - 1]!)
const weekCourses = computed(() =>
  state.courses.filter((course) => course.weeks.includes(state.week)),
)
const dailyCourses = computed(() =>
  weekCourses.value
    .filter((course) => course.day === selectedDay.value)
    .sort((a, b) => a.start - b.start),
)
const periodNumbers = Array.from({ length: 12 }, (_, i) => i + 1)
const monthLabel = computed(
  () =>
    `${dates.value[0]!.getMonth() + 1}月${dates.value[0]!.getDate()}日 — ${dates.value[6]!.getMonth() + 1}月${dates.value[6]!.getDate()}日`,
)
const dateLabel = computed(
  () =>
    `${selectedDate.value.getMonth() + 1}月${selectedDate.value.getDate()}日 · ${dayNames[selectedDay.value - 1]}`,
)
function shiftWeek(delta: number) {
  state.week = Math.min(20, Math.max(1, state.week + delta))
}
function returnToday() {
  const [year, month, day] = state.termStart.split('-').map(Number)
  const today = new Date()
  const diff =
    Date.UTC(today.getFullYear(), today.getMonth(), today.getDate()) -
    Date.UTC(year!, month! - 1, day!)
  state.week = Math.min(20, Math.max(1, Math.floor(diff / 604800000) + 1))
  selectedDay.value = today.getDay() || 7
}
async function retry() {
  refreshing.value = true
  error.value = ''
  try {
    await api.refresh()
  } catch (cause) {
    error.value = (cause as Error).message
  } finally {
    refreshing.value = false
  }
}
function openCourse(id: string) {
  go(`/pages-timetable/course?id=${encodeURIComponent(id)}`)
}
function gridCourses(day: number) {
  return weekCourses.value.filter((course) => course.day === day)
}
function cardStyle(course: Course) {
  return {
    top: `${(course.start - 1) * 66 + 3}px`,
    minHeight: `${course.duration * 66 - 6}px`,
    backgroundColor: course.color,
  }
}
const collisionCount = computed(
  () =>
    weekCourses.value.filter((a, i, all) =>
      all.some(
        (b, j) =>
          j < i &&
          a.day === b.day &&
          a.start < b.start + b.duration &&
          b.start < a.start + a.duration,
      ),
    ).length,
)
const conflictingCourses = computed(() =>
  weekCourses.value.filter((a, i, all) =>
    all.some(
      (b, j) =>
        j !== i &&
        a.day === b.day &&
        a.start < b.start + b.duration &&
        b.start < a.start + a.duration,
    ),
  ),
)
</script>

<template>
  <view class="page timetable-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="课表" subtitle="把每一天，安排得清楚一点">
      <template #right>
        <button class="header-import" @click="go('/pages-timetable/import')">
          <AppIcon name="download" :size="16" />
          <text>导入</text>
        </button>
      </template>
    </PageHeader>
    <view class="content">
      <DemoNotice />
      <view class="term-row">
        <text>
          {{ state.termStart.slice(0, 4) }}—{{ Number(state.termStart.slice(0, 4)) + 1 }} 学年 ·
          秋季学期
        </text>
        <view class="private-label">
          <AppIcon name="lock-on" :size="12" />
          仅自己可见
        </view>
      </view>
      <view class="week-toolbar">
        <view class="week-switch">
          <button
            class="nav-arrow"
            :disabled="state.week <= 1"
            aria-label="上一周"
            @click="shiftWeek(-1)"
          >
            <AppIcon name="arrow-left" :size="17" />
          </button>
          <view class="week-title">
            <text class="week-number">第 {{ state.week }} 周</text>
            <text class="week-range">{{ monthLabel }}</text>
          </view>
          <button
            class="nav-arrow"
            :disabled="state.week >= 20"
            aria-label="下一周"
            @click="shiftWeek(1)"
          >
            <AppIcon name="arrow-right" :size="17" />
          </button>
        </view>
        <button class="today-link" @click="returnToday">回到今天</button>
      </view>
      <view class="view-tools">
        <view class="segmented">
          <button :class="{ active: mode === 'day' }" @click="mode = 'day'">今日</button>
          <button :class="{ active: mode === 'week' }" @click="mode = 'week'">周课表</button>
        </view>
        <button class="add-course" @click="go('/pages-timetable/course')">
          <AppIcon name="add" :size="16" />
          <text>添加课程</text>
        </button>
      </view>
      <view v-if="state.networkState === 'error' || error" class="network-error">
        <text>{{ error || '课表暂时无法刷新，已保留当前内容' }}</text>
        <button :disabled="refreshing" @click="retry">{{ refreshing ? '重试中…' : '重试' }}</button>
      </view>
      <view v-if="state.networkState === 'loading'" class="loading-hint">正在加载课表…</view>
      <template v-if="mode === 'day'">
        <view class="day-strip">
          <button
            v-for="(date, index) in dates"
            :key="index"
            class="day-item"
            :class="{ selected: selectedDay === index + 1, today: sameDate(date, now) }"
            @click="selectedDay = index + 1"
          >
            <text class="day-name">{{ dayNames[index]?.replace('周', '') }}</text>
            <text class="day-date">{{ date.getDate() }}</text>
            <view class="day-dot" :class="{ has: weekCourses.some((c) => c.day === index + 1) }" />
          </button>
        </view>
        <view class="day-summary">
          <text class="day-heading">
            {{ sameDate(selectedDate, now) ? '今天的安排' : dateLabel }}
          </text>
          <text class="muted caption">{{ dailyCourses.length }} 门课</text>
        </view>
        <view v-if="dailyCourses.length" class="daily-list">
          <view
            v-for="course in dailyCourses"
            :key="course.id"
            class="course-row"
            @click="openCourse(course.id)"
          >
            <view class="time-column">
              <text class="time-start">{{ periods[course.start - 1]?.[0] }}</text>
              <text class="time-end">{{ periods[course.start + course.duration - 2]?.[1] }}</text>
              <view class="timeline-dot" />
              <view class="timeline-line" />
            </view>
            <view class="course-card" :style="{ borderLeftColor: course.color }">
              <view class="between row">
                <text class="course-title">{{ course.title }}</text>
                <AppIcon name="arrow-right" :size="15" color="#8c9790" />
              </view>
              <view class="course-place">
                <AppIcon name="location" :size="14" />
                <text>{{ course.room || '地点待核对' }}</text>
              </view>
              <view class="course-bottom">
                <text>{{ course.teacher || '教师未填写' }}</text>
                <text>第 {{ course.start }}—{{ course.start + course.duration - 1 }} 节</text>
              </view>
            </view>
          </view>
          <view class="day-end">
            <view class="end-line" />
            <text>课余时间，留给喜欢的事</text>
            <view class="end-line" />
          </view>
        </view>
        <view v-else class="empty-card">
          <EmptyState
            title="这一天没有课程"
            description="享受空闲，也可以添加新的课程安排。"
            icon="calendar"
          />
          <button class="btn btn-secondary" @click="go('/pages-timetable/course')">添加课程</button>
        </view>
      </template>
      <template v-else>
        <view class="week-hint">
          <text>本周 {{ weekCourses.length }} 门课</text>
          <text>左右滑动查看 · 点击课程编辑</text>
        </view>
        <view v-if="collisionCount" class="conflict-hint">
          <text>有 {{ collisionCount }} 处课程时间重叠，以下课程均已保留，点击逐项核对。</text>
          <view
            v-for="course in conflictingCourses"
            :key="course.id"
            style="padding-top: 9px; text-decoration: underline"
            @click="openCourse(course.id)"
          >
            {{ dayNames[course.day - 1] }} · {{ course.title }}
          </view>
        </view>
        <scroll-view scroll-x class="week-scroll">
          <view class="grid-content">
            <view class="grid-header">
              <view class="grid-corner">节次</view>
              <view
                v-for="(date, index) in dates"
                :key="index"
                class="grid-day"
                :class="{ 'grid-today': sameDate(date, now) }"
              >
                <text>{{ dayNames[index] }}</text>
                <text class="grid-date">{{ date.getMonth() + 1 }}/{{ date.getDate() }}</text>
              </view>
            </view>
            <view class="grid-body">
              <view class="period-column">
                <view v-for="period in periodNumbers" :key="period" class="period-cell">
                  <text>{{ period }}</text>
                  <text class="period-time">{{ periods[period - 1]?.[0] }}</text>
                </view>
              </view>
              <view v-for="day in 7" :key="day" class="grid-column">
                <view v-for="period in periodNumbers" :key="period" class="grid-cell" />
                <view
                  v-for="course in gridCourses(day)"
                  :key="course.id"
                  class="grid-course"
                  :style="cardStyle(course)"
                  @click="openCourse(course.id)"
                >
                  <text class="grid-course-name">{{ course.title }}</text>
                  <text class="grid-room">{{ course.room || '地点待定' }}</text>
                </view>
              </view>
            </view>
          </view>
        </scroll-view>
      </template>
      <view class="term-note">学期第 1 周从 {{ state.termStart }} 开始 · 作息时间为演示设置</view>
    </view>
    <BottomNav active="timetable" />
  </view>
</template>

<style scoped>
.header-import {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 0;
  padding: 0 8px;
  height: 44px;
  background: transparent;
  color: #07855d;
  font-size: calc(14px * var(--font-scale, 1));
}
.header-import::after {
  border: 0;
}
.term-row {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  color: #7c8780;
  font-size: calc(12px * var(--font-scale, 1));
  margin: 19px 0 12px;
}
.private-label {
  display: flex;
  align-items: center;
  gap: 3px;
}
.week-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.week-switch {
  display: flex;
  align-items: center;
  gap: 5px;
}
.nav-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 46px;
  margin: 0;
  padding: 0;
  border-radius: 9px;
  background: #fff;
  color: #4a5b50;
}
.nav-arrow::after {
  border: 0;
}
.nav-arrow[disabled] {
  opacity: 0.35;
}
.week-title {
  min-width: 126px;
  text-align: center;
}
.week-number {
  display: block;
  font-size: calc(20px * var(--font-scale, 1));
  font-weight: 600;
  letter-spacing: 0.4px;
}
.week-range {
  display: block;
  font-size: calc(11px * var(--font-scale, 1));
  margin-top: 4px;
  color: #89928c;
}
.today-link {
  margin: 0;
  padding: 0 4px;
  height: 44px;
  line-height: 44px;
  color: #07855d;
  background: transparent;
  font-size: calc(13px * var(--font-scale, 1));
  white-space: nowrap;
}
.today-link::after {
  border: 0;
}
.view-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 22px 0 16px;
  gap: 10px;
}
.segmented {
  display: flex;
  padding: 3px;
  border-radius: 9px;
  background: #e9eeeb;
}
.segmented button {
  font-size: calc(14px * var(--font-scale, 1));
  line-height: 38px;
  margin: 0;
  padding: 0 18px;
  color: #748079;
  background: transparent;
  border-radius: 7px;
}
.segmented button::after {
  border: 0;
}
.segmented button.active {
  color: #1c4430;
  background: #fff;
  box-shadow: 0 1px 3px #1f44300a;
}
.add-course {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: calc(13px * var(--font-scale, 1));
  color: #07855d;
  background: transparent;
  padding: 0;
  margin: 0;
  height: 44px;
}
.add-course::after {
  border: 0;
}
.day-strip {
  display: flex;
  justify-content: space-between;
  padding: 9px 5px;
  background: #fff;
  border-radius: 12px;
}
.day-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 13%;
  margin: 0;
  padding: 6px 0;
  border-radius: 9px;
  background: transparent;
  line-height: 1.5;
  color: #536159;
}
.day-item::after {
  border: 0;
}
.day-name {
  font-size: calc(12px * var(--font-scale, 1));
  color: #8b958f;
}
.day-date {
  font-size: calc(18px * var(--font-scale, 1));
  margin: 4px 0;
  font-weight: 500;
}
.day-item.today .day-date {
  color: #07855d;
}
.day-item.selected {
  background: #07855d;
  color: #fff;
}
.day-item.selected .day-name,
.day-item.selected .day-date {
  color: #fff;
}
.day-dot {
  width: 3px;
  height: 3px;
  border-radius: 100%;
  background: transparent;
}
.day-dot.has {
  background: #a8b8ae;
}
.selected .day-dot.has {
  background: #c6f1d9;
}
.day-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 23px 0 17px;
}
.day-heading {
  font-size: calc(15px * var(--font-scale, 1));
  font-weight: 500;
}
.course-row {
  display: flex;
  align-items: stretch;
  gap: 15px;
  margin-bottom: 16px;
}
.time-column {
  width: 48px;
  flex-shrink: 0;
  position: relative;
  padding-top: 13px;
}
.time-start {
  display: block;
  font-size: calc(15px * var(--font-scale, 1));
  font-weight: 600;
}
.time-end {
  display: block;
  margin-top: 4px;
  font-size: calc(12px * var(--font-scale, 1));
  color: #97a099;
}
.timeline-dot {
  position: absolute;
  top: 17px;
  right: -11px;
  width: 5px;
  height: 5px;
  background: #b4c4b9;
  border: 3px solid #f5f6f7;
  border-radius: 100%;
  z-index: 1;
}
.timeline-line {
  position: absolute;
  width: 1px;
  top: 27px;
  bottom: -22px;
  right: -6px;
  background: #dfe6e1;
}
.course-row:last-of-type .timeline-line {
  bottom: 8px;
}
.course-card {
  flex: 1;
  min-width: 0;
  padding: 15px 14px;
  background: #fff;
  border-radius: 10px;
  border-left: 4px solid;
}
.course-title {
  font-size: calc(16px * var(--font-scale, 1));
  font-weight: 500;
  line-height: 1.5;
  flex: 1;
  margin-right: 8px;
}
.course-place {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 10px;
  font-size: calc(13px * var(--font-scale, 1));
  color: #637269;
}
.course-bottom {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  color: #89938d;
  font-size: calc(12px * var(--font-scale, 1));
}
.day-end {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 11px;
  font-size: calc(12px * var(--font-scale, 1));
  color: #a0aaa3;
  padding: 17px 0 5px;
}
.end-line {
  width: 20px;
  height: 1px;
  background: #dce4de;
}
.term-note {
  text-align: center;
  font-size: calc(11px * var(--font-scale, 1));
  color: #97a29b;
  line-height: 1.7;
  padding: 25px 5px 14px;
}
.empty-card {
  padding: 12px 18px 22px;
  background: #fff;
  border-radius: 12px;
}
.empty-card button {
  max-width: 220px;
  margin: 0 auto;
}
.week-hint {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
  font-size: calc(11px * var(--font-scale, 1));
  color: #7b8b81;
}
.week-scroll {
  width: 100%;
  background: #fff;
  border-radius: 10px;
}
.grid-content {
  width: 604px;
}
.grid-header {
  display: flex;
  height: 58px;
  background: #f0f5f2;
  align-items: stretch;
}
.grid-corner {
  width: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #94a097;
  font-size: calc(11px * var(--font-scale, 1));
}
.grid-day {
  width: 80px;
  flex-shrink: 0;
  text-align: center;
  font-size: calc(13px * var(--font-scale, 1));
  padding-top: 8px;
  color: #5c6e62;
}
.grid-date {
  display: block;
  font-size: calc(11px * var(--font-scale, 1));
  color: #91a096;
  margin-top: 3px;
}
.grid-today {
  color: #07855d;
  background: #e1eee6;
}
.grid-body {
  display: flex;
}
.period-column {
  width: 44px;
  flex-shrink: 0;
}
.period-cell {
  height: 66px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: calc(12px * var(--font-scale, 1));
  color: #7b8b81;
  border-bottom: 1px solid #eef2ef;
  box-sizing: border-box;
}
.period-time {
  margin-top: 4px;
  font-size: calc(9px * var(--font-scale, 1));
  color: #a9b3ac;
}
.grid-column {
  position: relative;
  width: 80px;
  flex-shrink: 0;
}
.grid-cell {
  height: 66px;
  box-sizing: border-box;
  border-left: 1px solid #f0f3f1;
  border-bottom: 1px solid #eef2ef;
}
.grid-course {
  position: absolute;
  left: 3px;
  right: 3px;
  box-sizing: border-box;
  border-radius: 7px;
  padding: 9px 7px;
  color: #365343;
  overflow: hidden;
}
.grid-course-name {
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.5;
  display: block;
  font-weight: 500;
}
.grid-room {
  display: block;
  margin-top: 7px;
  font-size: calc(10px * var(--font-scale, 1));
  line-height: 1.4;
  opacity: 0.72;
}
.network-error {
  background: #fff1ec;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: calc(13px * var(--font-scale, 1));
  color: #9c523e;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 14px;
}
.network-error button {
  font-size: calc(13px * var(--font-scale, 1));
  padding: 0;
  margin: 0;
  color: #9c523e;
  background: transparent;
  flex-shrink: 0;
}
.network-error button::after {
  border: 0;
}
.loading-hint {
  color: #819087;
  font-size: calc(13px * var(--font-scale, 1));
  padding: 10px 0;
}
.conflict-hint {
  background: #fff3da;
  font-size: calc(12px * var(--font-scale, 1));
  color: #8b6a2e;
  padding: 10px;
  border-radius: 7px;
  margin-bottom: 10px;
}
@media (max-width: 340px) {
  .week-title {
    min-width: 112px;
  }
  .nav-arrow {
    width: 30px;
  }
  .segmented button {
    padding: 0 14px;
  }
  .term-row {
    font-size: calc(11px * var(--font-scale, 1));
  }
  .course-row {
    gap: 13px;
  }
  .course-card {
    padding: 13px 10px;
  }
}
</style>
