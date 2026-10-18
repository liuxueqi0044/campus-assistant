<script setup lang="ts">
import { computed } from 'vue'
import { courseColors, courseTime, dayNames, type CourseForm } from './helpers'

const props = defineProps<{ modelValue: CourseForm; errors?: Record<string, string> }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: CourseForm): void }>()
const time = computed(() => courseTime(props.modelValue.start, props.modelValue.duration))
const starts = Array.from({ length: 12 }, (_, i) => `第 ${i + 1} 节`)
const durations = ['1 节课', '2 节课', '3 节课', '4 节课']
function update<K extends keyof CourseForm>(key: K, value: CourseForm[K]) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
type UniValueEvent = { detail: { value: string | number } }
function onTextInput(
  key: 'title' | 'room' | 'teacher' | 'weeksText',
  event: Event | UniValueEvent,
) {
  if ('detail' in event) update(key, String(event.detail.value))
}
function onPickerChange(key: 'day' | 'start' | 'duration', event: Event | UniValueEvent) {
  if ('detail' in event) update(key, Number(event.detail.value) + 1)
}
function setWeeks(kind: 'all' | 'odd' | 'even') {
  update(
    'weeksText',
    kind === 'all'
      ? '1-16'
      : Array.from({ length: 8 }, (_, i) => i * 2 + (kind === 'odd' ? 1 : 2)).join(', '),
  )
}
</script>

<template>
  <view class="course-fields">
    <view class="field">
      <text class="label">
        课程名称
        <text class="required">*</text>
      </text>
      <input
        class="form-input"
        :value="modelValue.title"
        placeholder="如：高等数学 A"
        :maxlength="40"
        @input="onTextInput('title', $event)"
      />
      <text v-if="errors?.title" class="form-error">{{ errors.title }}</text>
    </view>
    <view class="field">
      <text class="label">
        上课地点
        <text class="required">*</text>
      </text>
      <input
        class="form-input"
        :value="modelValue.room"
        placeholder="如：教学楼 A302"
        :maxlength="60"
        @input="onTextInput('room', $event)"
      />
      <text v-if="errors?.room" class="form-error">{{ errors.room }}</text>
    </view>
    <view class="field">
      <text class="label">
        授课教师
        <text class="optional">选填</text>
      </text>
      <input
        class="form-input"
        :value="modelValue.teacher"
        placeholder="教师姓名"
        :maxlength="30"
        @input="onTextInput('teacher', $event)"
      />
    </view>
    <view class="field">
      <text class="label">上课时间</text>
      <view class="time-pickers">
        <picker
          mode="selector"
          :range="dayNames"
          :value="modelValue.day - 1"
          @change="onPickerChange('day', $event)"
        >
          <view class="picker-cell">
            {{ dayNames[modelValue.day - 1] }}
            <text class="picker-arrow">⌄</text>
          </view>
        </picker>
        <picker
          mode="selector"
          :range="starts"
          :value="modelValue.start - 1"
          @change="onPickerChange('start', $event)"
        >
          <view class="picker-cell">
            第 {{ modelValue.start }} 节
            <text class="picker-arrow">⌄</text>
          </view>
        </picker>
        <picker
          mode="selector"
          :range="durations"
          :value="modelValue.duration - 1"
          @change="onPickerChange('duration', $event)"
        >
          <view class="picker-cell">
            连续 {{ modelValue.duration }} 节
            <text class="picker-arrow">⌄</text>
          </view>
        </picker>
      </view>
      <text class="helper">{{ time }} · 当前演示作息</text>
      <text v-if="errors?.day || errors?.period" class="form-error">
        {{ errors.day || errors.period }}
      </text>
    </view>
    <view class="field">
      <text class="label">上课周次</text>
      <view class="week-presets">
        <button class="preset" @click="setWeeks('all')">1—16 周</button>
        <button class="preset" @click="setWeeks('odd')">单周</button>
        <button class="preset" @click="setWeeks('even')">双周</button>
      </view>
      <input
        class="form-input"
        :value="modelValue.weeksText"
        placeholder="如：1-8, 10-16"
        :maxlength="100"
        @input="onTextInput('weeksText', $event)"
      />
      <text class="helper">可填写 1—20 周；逗号分隔，支持不连续周次。</text>
      <text v-if="errors?.weeks" class="form-error">{{ errors.weeks }}</text>
    </view>
    <view class="field color-field">
      <text class="label">课程颜色</text>
      <view class="colors">
        <button
          v-for="(color, index) in courseColors"
          :key="color"
          class="color-choice"
          :class="{ selected: modelValue.color === color }"
          :style="{ backgroundColor: color }"
          :aria-label="`课程颜色 ${index + 1}`"
          @click="update('color', color)"
        >
          <text v-if="modelValue.color === color">✓</text>
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.course-fields {
  padding: 18px 16px 4px;
  background: #fff;
  border-radius: 12px;
}
.field {
  margin-bottom: 22px;
}
.label {
  display: block;
  margin-bottom: 9px;
  font-size: calc(15px * var(--font-scale, 1));
  font-weight: 500;
}
.required {
  color: #07855d;
}
.optional {
  color: #939b96;
  font-size: calc(13px * var(--font-scale, 1));
  font-weight: 400;
  margin-left: 5px;
}
.time-pickers {
  display: flex;
  gap: 7px;
}
.time-pickers picker {
  flex: 1;
  min-width: 0;
}
.picker-cell {
  min-height: 46px;
  padding: 0 9px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid #e6eae7;
  border-radius: 8px;
  font-size: calc(13px * var(--font-scale, 1));
  white-space: nowrap;
}
.picker-arrow {
  color: #87928b;
  margin-left: 4px;
}
.helper {
  display: block;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.6;
  color: #7a857e;
  margin-top: 7px;
}
.week-presets {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.preset {
  flex: 1;
  margin: 0;
  padding: 0 9px;
  line-height: 42px;
  font-size: calc(13px * var(--font-scale, 1));
  background: #f4f7f5;
  border-radius: 7px;
  color: #53655a;
}
.preset::after {
  border: 0;
}
.colors {
  display: flex;
  justify-content: space-between;
  gap: 4px;
}
.color-choice {
  width: 40px;
  height: 44px;
  border-radius: 10px;
  margin: 0;
  padding: 0;
  line-height: 44px;
  color: #275d46;
  font-size: calc(20px * var(--font-scale, 1));
}
.color-choice::after {
  border: 0;
}
.color-choice.selected {
  outline: 2px solid #07855d;
  outline-offset: 2px;
}
.color-field {
  margin-bottom: 17px;
}
</style>
