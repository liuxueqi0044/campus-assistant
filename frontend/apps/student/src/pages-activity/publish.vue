<script setup lang="ts">
import { computed, ref } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { go, requireLogin } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import AppIcon from '@/components/AppIcon.vue'
const themes = [
  { category: '运动', name: '球场见', cover: '/static/activities/badminton.png' },
  { category: '桌游', name: '一起玩', cover: '/static/activities/boardgame.png' },
  { category: '电影', name: '看场电影', cover: '/static/activities/film.png' },
  { category: '户外', name: '出去走走', cover: '/static/activities/walk.png' },
]
const selected = ref(0)
const title = ref('')
const date = ref('')
const startTime = ref('')
const endTime = ref('')
const location = ref('')
const capacity = ref('8')
const description = ref('')
const busy = ref(false)
const error = ref('')
const createdId = ref('')
const theme = computed(() => themes[selected.value]!)
const campus = computed(() => state.campuses.find((x) => x.id === state.campusId))
const dateValid = (value: string) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const d = new Date(`${value}T12:00:00`)
  return (
    !Number.isNaN(d.getTime()) &&
    d.getFullYear() === Number(value.slice(0, 4)) &&
    d.getMonth() + 1 === Number(value.slice(5, 7)) &&
    d.getDate() === Number(value.slice(8, 10))
  )
}
async function submit() {
  if (busy.value || createdId.value || !requireLogin()) return
  error.value = ''
  if (!title.value.trim() || !location.value.trim() || !description.value.trim()) {
    error.value = '请填写活动名称、集合地点和活动介绍。'
    return
  }
  if (
    !dateValid(date.value) ||
    !/^([01]\d|2[0-3]):[0-5]\d$/.test(startTime.value) ||
    !/^([01]\d|2[0-3]):[0-5]\d$/.test(endTime.value) ||
    startTime.value >= endTime.value
  ) {
    error.value = '请选择有效日期和当天的开始、结束时间，结束时间需晚于开始时间。'
    return
  }
  if (!/^\d+$/.test(capacity.value) || Number(capacity.value) < 2 || Number(capacity.value) > 100) {
    error.value = '活动人数请填写 2–100 之间的整数。'
    return
  }
  busy.value = true
  try {
    const result = await api.publishActivity({
      campusId: state.campusId,
      title: title.value.trim(),
      category: theme.value.category,
      cover: theme.value.cover,
      date: date.value,
      time: `${startTime.value}–${endTime.value}`,
      location: location.value.trim(),
      capacity: Number(capacity.value),
      description: description.value.trim(),
    })
    createdId.value = result.id
  } catch (e) {
    error.value = e instanceof Error ? e.message : '提交失败，填写内容已保留，请重试。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page detail-page publish-activity" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="发起活动" :back="true" />
    <DemoNotice />
    <view v-if="createdId" class="content">
      <view class="created">
        <view class="created-check"><AppIcon name="check" :size="30" /></view>
        <text class="created-title">期待这场相聚</text>
        <text class="created-copy">
          活动已提交，正在等待审核。通过前不会出现在公开活动列表，也不会开放报名。
        </text>
        <button class="btn btn-primary" @click="go(`/pages-activity/detail?id=${createdId}`)">
          查看活动与审核状态
        </button>
      </view>
    </view>
    <template v-else>
      <view class="content">
        <view class="intro">
          <text class="intro-title">有个好点子？约大家一起</text>
          <text class="intro-sub">{{ campus?.school }} · {{ campus?.name }}</text>
        </view>
        <view class="surface form-card">
          <view class="section-row">
            <text class="section-title">选一个活动主题</text>
            <text class="optional">示例封面</text>
          </view>
          <view class="theme-grid">
            <view
              v-for="(item, index) in themes"
              :key="item.category"
              :class="['theme-option', { chosen: selected === index }]"
              @click="selected = index"
            >
              <image :src="item.cover" mode="aspectFill" class="theme-cover" />
              <view class="theme-caption">
                <text>{{ item.category }}</text>
                <view :class="['theme-dot', { checked: selected === index }]">
                  {{ selected === index ? '✓' : '' }}
                </view>
              </view>
            </view>
          </view>
          <text class="helper">选择主题会一并使用对应示例海报，可先专注写好活动内容。</text>
        </view>
        <view class="surface form-card">
          <view class="field first">
            <text class="label">活动名称</text>
            <input
              v-model="title"
              class="form-input"
              placeholder="例如：周五傍晚，一起打羽毛球"
              :maxlength="40"
            />
          </view>
          <view class="field">
            <text class="label">活动日期</text>
            <picker mode="date" :value="date" @change="date = $event.detail.value">
              <view :class="['picker-field', { placeholder: !date }]">
                <text>{{ date || '选择活动日期' }}</text>
                <AppIcon name="calendar" :size="17" />
              </view>
            </picker>
          </view>
          <view class="field">
            <text class="label">活动时间</text>
            <view class="time-row">
              <picker
                mode="time"
                :value="startTime || '18:00'"
                @change="startTime = $event.detail.value"
              >
                <view :class="['picker-field', { placeholder: !startTime }]">
                  <text>{{ startTime || '开始时间' }}</text>
                  <AppIcon name="time" :size="16" />
                </view>
              </picker>
              <text class="time-separator">—</text>
              <picker
                mode="time"
                :value="endTime || '20:00'"
                @change="endTime = $event.detail.value"
              >
                <view :class="['picker-field', { placeholder: !endTime }]">
                  <text>{{ endTime || '结束时间' }}</text>
                  <AppIcon name="time" :size="16" />
                </view>
              </picker>
            </view>
          </view>
          <view class="field">
            <text class="label">集合地点</text>
            <input
              v-model="location"
              class="form-input"
              placeholder="例如：东区体育馆一楼入口"
              :maxlength="60"
            />
            <text class="helper">选一个同学容易找到的校内公共地点。</text>
          </view>
        </view>
        <view class="surface form-card">
          <view class="field first">
            <text class="label">人数上限</text>
            <view class="capacity-row">
              <input
                v-model="capacity"
                type="number"
                class="form-input capacity-input"
                :maxlength="3"
              />
              <text>人</text>
            </view>
            <text class="helper">2–100 人，正式名额满后可加入候补。</text>
          </view>
          <view class="field">
            <text class="label">活动介绍</text>
            <textarea
              v-model="description"
              class="form-textarea"
              placeholder="会做些什么？需要带什么？新手能否参加？把这些告诉同学吧。"
              :maxlength="1000"
            />
            <text class="count">{{ description.length }}/1000</text>
          </view>
          <view class="free-note">
            <AppIcon name="check" :size="18" />
            <view>
              <text class="free-title">免费活动，轻松相聚</text>
              <text class="free-copy">本次仅支持免费活动，不向报名同学收款。</text>
            </view>
          </view>
        </view>
        <text v-if="error" class="form-error inline-error">{{ error }}</text>
        <text class="publish-note">
          活动提交后进入审核。请提前确认场地和时间；不要在介绍中公开手机号或其他同学的私人信息。
        </text>
      </view>
      <view class="action-bar">
        <button class="btn btn-primary submit" :loading="busy" :disabled="busy" @click="submit">
          {{ busy ? '正在提交…' : '提交活动审核' }}
        </button>
      </view>
    </template>
  </view>
</template>

<style scoped>
.publish-activity {
  padding-bottom: 112px;
}
.intro {
  padding: 24px 2px 19px;
}
.intro-title {
  display: block;
  font-size: calc(21px * var(--font-scale, 1));
  font-weight: 600;
  line-height: 1.5;
}
.intro-sub {
  display: block;
  font-size: calc(13px * var(--font-scale, 1));
  color: #889180;
  margin-top: 7px;
}
.form-card {
  background: #fff;
  border-radius: 9px;
  padding: 19px 17px;
  margin-bottom: 14px;
}
.section-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.section-title {
  font-size: calc(15px * var(--font-scale, 1));
  font-weight: 600;
}
.optional {
  font-size: calc(11px * var(--font-scale, 1));
  color: #94a087;
}
.theme-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 11px;
  margin-top: 15px;
}
.theme-option {
  border: 1px solid #e9eee3;
  border-radius: 7px;
  overflow: hidden;
  box-sizing: border-box;
  background: #fafbf7;
}
.theme-option.chosen {
  border-color: #07855d;
  background: #f0f7ef;
}
.theme-cover {
  width: 100%;
  height: 75px;
  display: block;
}
.theme-caption {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 9px 10px;
  font-size: calc(12px * var(--font-scale, 1));
  color: #6f7d62;
}
.chosen .theme-caption {
  color: #07855d;
}
.theme-dot {
  height: 14px;
  width: 14px;
  border: 1px solid #cad5bd;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: calc(9px * var(--font-scale, 1));
  box-sizing: border-box;
}
.theme-dot.checked {
  background: #07855d;
  border-color: #07855d;
  color: #fff;
}
.helper {
  display: block;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.7;
  color: #8c9783;
  margin-top: 10px;
}
.field {
  margin-top: 23px;
}
.field.first {
  margin-top: 0;
}
.label {
  display: block;
  font-size: calc(14px * var(--font-scale, 1));
  color: #3b4c33;
  margin-bottom: 10px;
}
.picker-field {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  border: 1px solid #e7ece1;
  background: #fafbf8;
  border-radius: 6px;
  min-height: 45px;
  padding: 0 12px;
  box-sizing: border-box;
  font-size: calc(15px * var(--font-scale, 1));
  color: #36482f;
}
.picker-field.placeholder {
  color: #a2ad96;
}
.time-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.time-row picker {
  flex: 1;
  min-width: 0;
}
.time-row .picker-field {
  padding: 0 10px;
  font-size: calc(14px * var(--font-scale, 1));
}
.time-separator {
  color: #c1c9b8;
  font-size: calc(12px * var(--font-scale, 1));
}
.capacity-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: calc(14px * var(--font-scale, 1));
  color: #8a977e;
}
.capacity-input {
  max-width: 92px;
}
.count {
  display: block;
  font-size: calc(11px * var(--font-scale, 1));
  color: #a0aa94;
  text-align: right;
  margin-top: 6px;
}
.free-note {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: #07855d;
  background: #f0f6ed;
  border-radius: 7px;
  padding: 13px;
  margin-top: 19px;
}
.free-title {
  font-size: calc(13px * var(--font-scale, 1));
  display: block;
  font-weight: 500;
}
.free-copy {
  font-size: calc(12px * var(--font-scale, 1));
  display: block;
  color: #839376;
  line-height: 1.7;
  margin-top: 3px;
}
.publish-note {
  font-size: calc(12px * var(--font-scale, 1));
  display: block;
  line-height: 1.9;
  color: #8c9880;
  padding: 2px 5px 22px;
}
.inline-error {
  display: block;
  padding: 2px 3px 12px;
}
.submit {
  width: 100%;
}
.created {
  padding: 40px 16px;
  background: #fff;
  border-radius: 10px;
  margin-top: 25px;
  text-align: center;
}
.created-check {
  width: 65px;
  height: 65px;
  border-radius: 50%;
  background: #edf6ee;
  color: #07855d;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
}
.created-title {
  display: block;
  font-size: calc(22px * var(--font-scale, 1));
  font-weight: 600;
}
.created-copy {
  display: block;
  font-size: calc(14px * var(--font-scale, 1));
  line-height: 1.9;
  color: #849082;
  margin: 15px 0 27px;
}
</style>
