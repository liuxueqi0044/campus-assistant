<script setup lang="ts">
import { computed, ref } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { go, money, requireLogin } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import AppIcon from '@/components/AppIcon.vue'
const categories = ['快递代取', '校内代送', '其他']
const weights = ['小件 · 2kg以内', '中件 · 2–5kg', '大件 · 需提前确认']
const category = ref('快递代取')
const title = ref('')
const pickup = ref('')
const destination = ref('')
const weight = ref(weights[0])
const deadline = ref('')
const description = ref('')
const reward = ref('3.20')
const busy = ref(false)
const error = ref('')
const createdId = ref('')
const feeFen = 80
const rewardFen = computed(() => Math.round(Number(reward.value || 0) * 100))
const totalFen = computed(() =>
  Number.isFinite(rewardFen.value) ? rewardFen.value + feeFen : feeFen,
)
const campus = computed(() => state.campuses.find((x) => x.id === state.campusId))
async function submit() {
  if (busy.value || createdId.value || !requireLogin()) return
  error.value = ''
  if (
    !title.value.trim() ||
    !pickup.value.trim() ||
    !destination.value.trim() ||
    !deadline.value.trim()
  ) {
    error.value = '请填写任务名称、取送区域和时间要求。'
    return
  }
  if (!/^\d+(\.\d{1,2})?$/.test(reward.value) || rewardFen.value < 100 || rewardFen.value > 10000) {
    error.value = '演示报酬请填写 1–100 元，最多两位小数。'
    return
  }
  busy.value = true
  try {
    const item = await api.createErrand({
      campusId: state.campusId,
      title: title.value.trim(),
      category: category.value,
      pickup: pickup.value.trim(),
      destination: destination.value.trim(),
      weight: weight.value,
      deadline: deadline.value.trim(),
      description: description.value.trim(),
      rewardFen: rewardFen.value,
      feeFen,
    })
    createdId.value = item.id
  } catch (e) {
    error.value = e instanceof Error ? e.message : '没有创建成功，填写的内容已保留，请重试。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page detail-page publish-errand" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="发布跑腿" :back="true" />
    <DemoNotice />
    <view v-if="createdId" class="content">
      <view class="created">
        <view class="created-check"><AppIcon name="check" :size="30" /></view>
        <text class="created-title">任务已经创建</text>
        <text class="created-copy">
          下一步查看订单并体验模拟支付。当前没有实际扣款，也没有真实任务发出。
        </text>
        <button class="btn btn-primary" @click="go(`/pages-errand/detail?id=${createdId}`)">
          查看任务与支付进度
        </button>
      </view>
    </view>
    <template v-else>
      <view class="content">
        <view class="intro">
          <text class="intro-title">把小事交给顺路的同学</text>
          <text class="intro-sub">{{ campus?.school }} · {{ campus?.name }}</text>
        </view>
        <view class="surface form-card">
          <text class="label">需要哪种帮助</text>
          <view class="choices">
            <button
              v-for="c in categories"
              :key="c"
              :class="['choice', { selected: category === c }]"
              @click="category = c"
            >
              {{ c }}
            </button>
          </view>
          <view class="field">
            <text class="label">任务名称</text>
            <input
              v-model="title"
              class="form-input"
              placeholder="例如：帮取一个小包裹"
              :maxlength="40"
            />
          </view>
          <view class="field">
            <text class="label">取件区域</text>
            <input
              v-model="pickup"
              class="form-input"
              placeholder="例如：东门菜鸟驿站"
              :maxlength="50"
            />
          </view>
          <view class="field">
            <text class="label">送达区域</text>
            <input
              v-model="destination"
              class="form-input"
              placeholder="例如：8号宿舍楼下"
              :maxlength="50"
            />
          </view>
          <view class="privacy">
            <AppIcon name="lock-on" :size="16" />
            <text>请填写公共交接区域。此演示不收集取件码、具体寝室或手机号。</text>
          </view>
        </view>
        <view class="surface form-card">
          <text class="label">物品规格</text>
          <view class="weight-options">
            <button
              v-for="w in weights"
              :key="w"
              :class="['weight-option', { selected: weight === w }]"
              @click="weight = w"
            >
              <text>{{ w }}</text>
              <view class="radio"><view v-if="weight === w" /></view>
            </button>
          </view>
          <view class="field">
            <text class="label">时间要求</text>
            <input
              v-model="deadline"
              class="form-input"
              placeholder="例如：今天 18:00 前送达"
              :maxlength="30"
            />
          </view>
          <view class="field">
            <view class="label-row">
              <text class="label">补充说明</text>
              <text class="optional">选填</text>
            </view>
            <textarea
              v-model="description"
              class="form-textarea"
              placeholder="物品有什么需要注意的？请勿填写私人信息。"
              :maxlength="300"
            />
            <text class="count">{{ description.length }}/300</text>
          </view>
        </view>
        <view class="surface form-card">
          <view class="label-row">
            <text class="section-title">费用预览</text>
            <text class="preview-tag">演示报价</text>
          </view>
          <view class="reward-input-row">
            <text>接单同学报酬</text>
            <view class="reward-input">
              <text>¥</text>
              <input v-model="reward" type="digit" :maxlength="6" />
            </view>
          </view>
          <view class="cost-row">
            <text>平台服务费</text>
            <text>¥{{ money(feeFen) }}</text>
          </view>
          <view class="cost-row total">
            <text>合计</text>
            <text>¥{{ money(totalFen) }}</text>
          </view>
          <text class="cost-note">
            当前为演示金额，不会扣款。正式报价由服务端规则计算，最终以订单确认页为准。
          </text>
        </view>
        <text v-if="error" class="form-error inline-error">{{ error }}</text>
        <view class="publish-note">
          创建后先进入待支付状态，支付确认后才开放接单。你可以在任务详情取消。
        </view>
      </view>
      <view class="action-bar">
        <button class="btn btn-primary submit" :loading="busy" :disabled="busy" @click="submit">
          {{ busy ? '正在创建…' : '创建任务，查看费用' }}
        </button>
      </view>
    </template>
  </view>
</template>

<style scoped>
.publish-errand {
  padding-bottom: 110px;
}
.intro {
  padding: 24px 2px 18px;
}
.intro-title {
  display: block;
  font-size: calc(21px * var(--font-scale, 1));
  font-weight: 600;
}
.intro-sub {
  display: block;
  color: #87938a;
  font-size: calc(13px * var(--font-scale, 1));
  margin-top: 7px;
}
.form-card {
  padding: 19px 17px;
  border-radius: 9px;
  margin-bottom: 14px;
}
.choices {
  display: flex;
  gap: 8px;
  margin-top: 11px;
}
.choice {
  flex: 1;
  background: #f5f7f4;
  color: #737f75;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 42px;
  margin: 0;
  padding: 0 4px;
}
.choice:after,
.weight-option:after {
  border: 0;
}
.selected {
  background: #edf6ef !important;
  color: #07855d !important;
  border-color: #abcdb6 !important;
}
.field {
  margin-top: 21px;
}
.label {
  font-size: calc(14px * var(--font-scale, 1));
  display: block;
  margin-bottom: 9px;
  color: #394b3f;
}
.privacy {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  background: #f5f8f4;
  border-radius: 6px;
  padding: 11px;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.7;
  color: #7e8c81;
  margin-top: 16px;
}
.weight-options {
  margin-top: 11px;
}
.weight-option {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  line-height: 1.5;
  min-height: 46px;
  text-align: left;
  font-size: calc(14px * var(--font-scale, 1));
  background: #fafbf9;
  border: 1px solid #eff2ec;
  border-radius: 6px;
  padding: 10px 12px;
  margin: 8px 0;
}
.radio {
  height: 15px;
  width: 15px;
  border: 1px solid #cbd6ca;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-left: 10px;
}
.radio > view {
  height: 7px;
  width: 7px;
  border-radius: 50%;
  background: #07855d;
}
.selected .radio {
  border-color: #07855d;
}
.label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.optional,
.count {
  font-size: calc(12px * var(--font-scale, 1));
  color: #99a194;
}
.count {
  display: block;
  text-align: right;
  margin-top: 5px;
}
.section-title {
  font-size: calc(16px * var(--font-scale, 1));
  font-weight: 600;
}
.preview-tag {
  font-size: calc(11px * var(--font-scale, 1));
  color: #8b8a64;
  background: #f6f4e9;
  padding: 3px 7px;
  border-radius: 4px;
}
.reward-input-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 19px;
  font-size: calc(14px * var(--font-scale, 1));
}
.reward-input {
  display: flex;
  align-items: center;
  gap: 5px;
  background: #f6f8f4;
  border: 1px solid #e6ece3;
  border-radius: 6px;
  padding: 9px 10px;
}
.reward-input input {
  width: 60px;
  font-size: calc(18px * var(--font-scale, 1));
  text-align: right;
  color: #07855d;
}
.cost-row {
  display: flex;
  justify-content: space-between;
  gap: 15px;
  font-size: calc(14px * var(--font-scale, 1));
  color: #7e897f;
  margin-top: 16px;
}
.cost-row.total {
  border-top: 1px solid #ecf0e8;
  padding-top: 16px;
  font-size: calc(17px * var(--font-scale, 1));
  color: #304738;
  font-weight: 600;
}
.cost-note,
.publish-note {
  display: block;
  font-size: calc(12px * var(--font-scale, 1));
  color: #85917f;
  line-height: 1.8;
  margin-top: 14px;
}
.publish-note {
  padding: 1px 5px 20px;
}
.submit {
  width: 100%;
}
.inline-error {
  display: block;
  padding: 2px 3px 10px;
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
