<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { state } from '@/state'
import { api } from '@/services/api'
import { back, money, requireLogin } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppIcon from '@/components/AppIcon.vue'
const id = ref('')
const busy = ref(false)
const error = ref('')
const confirmCancel = ref(false)
const feedback = ref('')
onLoad((options) => {
  id.value = options?.id || ''
})
const order = computed(() => state.errands.find((x) => x.id === id.value))
const mine = computed(() => state.user.loggedIn && order.value?.requesterId === state.user.id)
const assigned = computed(() => state.user.loggedIn && order.value?.runnerId === state.user.id)
const labels: Record<string, string> = {
  WAITING_PAYMENT: '等待支付',
  OPEN: '等待同学接单',
  ACCEPTED: '已有同学接单',
  IN_PROGRESS: '正在送达',
  DELIVERED: '等待收件确认',
  COMPLETED: '任务已完成',
  CANCELLED: '任务已取消',
}
const paymentLabels: Record<string, string> = {
  UNPAID: '未支付',
  PAID: '支付完成',
  REFUND_PENDING: '退款处理中',
  REFUNDED: '退款完成',
}
const steps = ['已发布', '已接单', '配送中', '待确认', '已完成']
const currentStep = computed(
  () =>
    ({
      WAITING_PAYMENT: -1,
      OPEN: 0,
      ACCEPTED: 1,
      IN_PROGRESS: 2,
      DELIVERED: 3,
      COMPLETED: 4,
      CANCELLED: -1,
    })[order.value?.status || 'OPEN'],
)
const canCancel = computed(
  () => mine.value && ['WAITING_PAYMENT', 'OPEN'].includes(order.value?.status || ''),
)
const advanceLabel = computed(() =>
  assigned.value && order.value?.status === 'ACCEPTED'
    ? '确认已取件'
    : assigned.value && order.value?.status === 'IN_PROGRESS'
      ? '标记已送达'
      : mine.value && order.value?.status === 'DELIVERED'
        ? '确认收到物品'
        : '',
)
const payoutLabel = computed(() =>
  !order.value || order.value.payoutStatus === 'NONE'
    ? '尚未生成结算'
    : order.value.payoutStatus === 'PENDING'
      ? '待结算 · 尚未到账'
      : state.demo
        ? '模拟结算记录 · 非实际到账'
        : '渠道记录显示已打款',
)
async function act(action: 'pay' | 'accept' | 'advance' | 'cancel') {
  if (busy.value || !order.value || !requireLogin()) return
  busy.value = true
  error.value = ''
  feedback.value = ''
  try {
    if (action === 'pay') {
      if (!state.demo) throw new Error('真实支付尚未接入，此订单不会扣款。')
      await api.simulatePayment(id.value)
    }
    if (action === 'accept') await api.acceptErrand(id.value)
    if (action === 'advance') await api.advanceErrand(id.value)
    if (action === 'cancel') await api.cancelErrand(id.value)
    confirmCancel.value = false
    feedback.value = state.demo ? '演示状态已更新，没有实际扣款或转账。' : '订单状态已更新。'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作没有完成，请重试。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page detail-page errand-detail" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="跑腿详情" :back="true" />
    <DemoNotice />
    <template v-if="order">
      <view class="status-hero">
        <view class="status-symbol">
          <AppIcon :name="order.status === 'COMPLETED' ? 'check' : 'time'" :size="25" />
        </view>
        <view>
          <text class="status-title">{{ labels[order.status] }}</text>
          <text class="status-desc">
            {{
              order.status === 'CANCELLED'
                ? '订单记录已保留，资金进度见下方'
                : order.status === 'COMPLETED'
                  ? '感谢这一次小小的互相帮助'
                  : `${order.deadline} · ${order.category}`
            }}
          </text>
        </view>
      </view>
      <view class="content">
        <view class="surface info-card">
          <view class="title-row">
            <text class="order-title">{{ order.title }}</text>
            <view class="reward">
              <text class="reward-label">接单报酬</text>
              <text class="reward-value">¥{{ money(order.rewardFen) }}</text>
            </view>
          </view>
          <view class="route-line">
            <view class="route-badge">取</view>
            <view>
              <text class="route-caption">取件区域</text>
              <text class="route-place">{{ order.pickup }}</text>
            </view>
          </view>
          <view class="route-line">
            <view class="route-badge destination">送</view>
            <view>
              <text class="route-caption">送达区域</text>
              <text class="route-place">{{ order.destination }}</text>
            </view>
          </view>
          <view class="privacy-note">
            <AppIcon name="lock-on" :size="14" />
            <text>这里仅展示公共区域。请勿在公开备注填写取件码、手机号。</text>
          </view>
        </view>
        <view v-if="order.status !== 'CANCELLED'" class="surface info-card">
          <text class="section-heading">履约进度</text>
          <view class="timeline">
            <view
              v-for="(step, index) in steps"
              :key="step"
              :class="['step', { done: index <= currentStep, active: index === currentStep }]"
            >
              <view class="step-dot"><text v-if="index < currentStep">✓</text></view>
              <text class="step-name">{{ step }}</text>
            </view>
          </view>
          <text v-if="order.status === 'WAITING_PAYMENT'" class="helper">
            支付确认后，任务才会进入接单大厅。
          </text>
        </view>
        <view class="surface info-card">
          <text class="section-heading">任务说明</text>
          <view class="info-row">
            <text class="muted">物品规格</text>
            <text>{{ order.weight }}</text>
          </view>
          <view class="info-row">
            <text class="muted">时间要求</text>
            <text>{{ order.deadline }}</text>
          </view>
          <text class="description">
            {{ order.description || '没有其他备注，按约定区域交接即可。' }}
          </text>
        </view>
        <view class="surface info-card">
          <text class="section-heading">费用与资金进度</text>
          <view class="info-row">
            <text class="muted">跑腿报酬</text>
            <text>¥{{ money(order.rewardFen) }}</text>
          </view>
          <view class="info-row">
            <text class="muted">平台服务费</text>
            <text>¥{{ money(order.feeFen) }}</text>
          </view>
          <view class="info-row total">
            <text>订单总额</text>
            <text>¥{{ money(order.rewardFen + order.feeFen) }}</text>
          </view>
          <view class="info-row">
            <text class="muted">支付状态</text>
            <text>{{ state.demo ? '演示 · ' : '' }}{{ paymentLabels[order.paymentStatus] }}</text>
          </view>
          <view class="info-row">
            <text class="muted">报酬结算</text>
            <text>{{ payoutLabel }}</text>
          </view>
          <text class="helper">订单完成与报酬到账是两个状态，请以资金记录为准。</text>
        </view>
        <view v-if="!mine && !assigned && order.status === 'OPEN'" class="service-note">
          <text class="section-heading">接单前确认</text>
          <text>请确认能够在约定时间完成。本演示仅体验操作流程，不产生真实配送任务。</text>
          <text v-if="!state.user.runner" class="eligibility">
            当前账号尚无接单资格，接单时将由接口校验。
          </text>
        </view>
        <view v-if="confirmCancel" class="cancel-panel">
          <text class="section-heading">确认取消这个任务？</text>
          <text class="helper">已支付订单会按接口结果展示退款进度。演示版不会发生真实退款。</text>
          <view class="cancel-actions">
            <button class="btn btn-secondary" :disabled="busy" @click="confirmCancel = false">
              保留订单
            </button>
            <button class="btn btn-danger" :loading="busy" :disabled="busy" @click="act('cancel')">
              确认取消
            </button>
          </view>
        </view>
        <text v-if="error" class="form-error result-message">{{ error }}</text>
        <text v-if="feedback" class="success-message result-message">{{ feedback }}</text>
        <view class="record-note">订单 {{ order.id }} · 进度由接口返回</view>
      </view>
      <view class="action-bar detail-actions">
        <button
          v-if="!state.user.loggedIn"
          class="btn btn-primary main-action"
          @click="requireLogin()"
        >
          登录后处理任务
        </button>
        <template v-else>
          <button
            v-if="canCancel && !confirmCancel"
            class="btn btn-secondary secondary-action"
            :disabled="busy"
            @click="confirmCancel = true"
          >
            取消任务
          </button>
          <button
            v-if="mine && order.status === 'WAITING_PAYMENT'"
            class="btn btn-primary main-action"
            :disabled="busy || !state.demo"
            :loading="busy"
            @click="act('pay')"
          >
            {{ state.demo ? '模拟支付 · 不扣款' : '真实支付尚未接入' }}
          </button>
          <button
            v-else-if="!mine && order.status === 'OPEN'"
            class="btn btn-primary main-action"
            :disabled="busy"
            :loading="busy"
            @click="act('accept')"
          >
            接下这个任务
          </button>
          <button
            v-else-if="advanceLabel"
            class="btn btn-primary main-action"
            :disabled="busy"
            :loading="busy"
            @click="act('advance')"
          >
            {{ advanceLabel }}
          </button>
          <view v-else class="action-status">
            {{
              mine && order.status === 'OPEN'
                ? '正在等待同学接单'
                : order.status === 'COMPLETED'
                  ? '任务完成，结算进度见上方'
                  : order.status === 'CANCELLED'
                    ? '此任务已取消'
                    : '当前没有需要你处理的操作'
            }}
          </view>
        </template>
      </view>
    </template>
    <view v-else class="content">
      <EmptyState
        title="没有找到这个任务"
        description="记录可能已移除，可以返回校园查看其他任务。"
      />
      <button class="btn btn-secondary" @click="back">返回校园</button>
    </view>
  </view>
</template>

<style scoped>
.errand-detail {
  padding-bottom: 120px;
}
.status-hero {
  display: flex;
  gap: 13px;
  align-items: center;
  padding: 25px 20px 23px;
  background: #edf5f0;
  color: #087d57;
}
.status-symbol {
  width: 44px;
  height: 44px;
  border: 1px solid #cde0d3;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.status-title {
  display: block;
  font-size: calc(20px * var(--font-scale, 1));
  font-weight: 600;
}
.status-desc {
  display: block;
  font-size: calc(12px * var(--font-scale, 1));
  color: #6d8275;
  margin-top: 5px;
  line-height: 1.6;
}
.info-card {
  padding: 19px 17px;
  margin-top: 14px;
  border-radius: 9px;
}
.title-row {
  display: flex;
  justify-content: space-between;
  gap: 15px;
  margin-bottom: 21px;
}
.order-title {
  font-size: calc(19px * var(--font-scale, 1));
  font-weight: 600;
  line-height: 1.5;
  min-width: 0;
}
.reward {
  text-align: right;
  flex-shrink: 0;
}
.reward-label {
  display: block;
  font-size: calc(11px * var(--font-scale, 1));
  color: #889187;
}
.reward-value {
  display: block;
  font-size: calc(24px * var(--font-scale, 1));
  color: #a97946;
  font-weight: 600;
  margin-top: 3px;
}
.route-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 17px;
}
.route-badge {
  width: 29px;
  height: 29px;
  border-radius: 7px;
  background: #edf5ef;
  color: #0d8058;
  font-size: calc(12px * var(--font-scale, 1));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.route-badge.destination {
  color: #9c774d;
  background: #f7f2e9;
}
.route-caption {
  font-size: calc(11px * var(--font-scale, 1));
  color: #88938b;
  display: block;
  margin-bottom: 3px;
}
.route-place {
  display: block;
  font-size: calc(16px * var(--font-scale, 1));
  font-weight: 500;
  overflow-wrap: anywhere;
}
.privacy-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding-top: 13px;
  border-top: 1px solid #eef1ed;
  font-size: calc(12px * var(--font-scale, 1));
  color: #7c877f;
  line-height: 1.6;
}
.section-heading {
  display: block;
  font-size: calc(16px * var(--font-scale, 1));
  font-weight: 600;
  margin-bottom: 13px;
}
.timeline {
  display: flex;
  margin: 23px 0 7px;
}
.step {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  color: #9ba39c;
}
.step:before {
  content: '';
  position: absolute;
  right: 50%;
  top: 7px;
  width: 100%;
  height: 1px;
  background: #e3e9e4;
}
.step:first-child:before {
  display: none;
}
.step.done:before {
  background: #91c6a8;
}
.step-dot {
  width: 15px;
  height: 15px;
  box-sizing: border-box;
  border: 2px solid #dde5de;
  border-radius: 50%;
  background: white;
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: calc(10px * var(--font-scale, 1));
}
.step.done {
  color: #0a815a;
}
.step.done .step-dot {
  background: #07855d;
  border-color: #07855d;
  color: white;
}
.step.active .step-dot {
  box-shadow: 0 0 0 4px #edf6ef;
}
.step-name {
  font-size: calc(11px * var(--font-scale, 1));
  white-space: nowrap;
}
.info-row {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  font-size: calc(14px * var(--font-scale, 1));
  margin: 12px 0;
  line-height: 1.6;
}
.info-row > text:last-child {
  text-align: right;
  overflow-wrap: anywhere;
  max-width: 67%;
}
.description {
  display: block;
  font-size: calc(14px * var(--font-scale, 1));
  line-height: 1.9;
  white-space: pre-wrap;
  color: #637168;
  padding-top: 9px;
  overflow-wrap: anywhere;
}
.total {
  border-top: 1px solid #edf1ed;
  padding-top: 12px;
  font-weight: 600;
}
.helper {
  display: block;
  color: #849087;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.7;
  margin-top: 10px;
}
.service-note {
  padding: 20px 5px;
  font-size: calc(13px * var(--font-scale, 1));
  color: #758178;
  line-height: 1.8;
}
.service-note > text {
  display: block;
}
.eligibility {
  color: #9b7949;
  margin-top: 8px;
}
.record-note {
  font-size: calc(11px * var(--font-scale, 1));
  text-align: center;
  color: #96a096;
  margin: 20px 0;
}
.detail-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.main-action {
  flex: 1;
  min-width: 0;
}
.secondary-action {
  flex: 0 0 auto;
}
.action-status {
  flex: 1;
  text-align: center;
  color: #78877c;
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.7;
  padding: 8px;
}
.cancel-panel {
  margin-top: 15px;
  border: 1px solid #e7d5cf;
  background: #fffaf8;
  border-radius: 8px;
  padding: 16px;
}
.cancel-actions {
  display: flex;
  gap: 10px;
  margin-top: 15px;
}
.cancel-actions button {
  flex: 1;
  font-size: calc(14px * var(--font-scale, 1));
}
.result-message {
  display: block;
  padding: 13px 2px;
  line-height: 1.6;
  font-size: calc(13px * var(--font-scale, 1));
}
.success-message {
  color: #07855d;
}
@media (max-width: 350px) {
  .order-title {
    font-size: calc(17px * var(--font-scale, 1));
  }
  .reward-value {
    font-size: calc(21px * var(--font-scale, 1));
  }
  .info-card {
    padding: 16px 13px;
  }
  .main-action {
    font-size: calc(14px * var(--font-scale, 1));
  }
  .secondary-action {
    font-size: calc(13px * var(--font-scale, 1));
    padding-left: 10px;
    padding-right: 10px;
  }
}
</style>
