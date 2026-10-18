<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { state } from '@/state'
import { api } from '@/services/api'
import { back, requireLogin } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppIcon from '@/components/AppIcon.vue'
const id = ref('')
const busy = ref(false)
const error = ref('')
const feedback = ref('')
const confirmLeave = ref(false)
onLoad((options) => {
  id.value = options?.id || ''
})
const activity = computed(() => state.activities.find((x) => x.id === id.value))
const remaining = computed(() =>
  activity.value ? Math.max(0, activity.value.capacity - activity.value.occupied) : 0,
)
const percent = computed(() =>
  activity.value
    ? Math.min(
        100,
        Math.round((activity.value.occupied / Math.max(1, activity.value.capacity)) * 100),
      )
    : 0,
)
const participation = computed(() =>
  state.user.loggedIn ? activity.value?.participantStatus || 'NONE' : 'NONE',
)
const canLeave = computed(() => ['CONFIRMED', 'WAITLISTED'].includes(participation.value))
const statusTitle = computed(() =>
  participation.value === 'WAITLISTED'
    ? '已加入候补'
    : participation.value === 'CHECKED_IN'
      ? state.demo
        ? '模拟签到完成'
        : '已签到'
      : '报名成功，期待见面',
)
async function act(action: 'join' | 'cancel' | 'checkin') {
  if (busy.value || !activity.value || !requireLogin()) return
  busy.value = true
  error.value = ''
  feedback.value = ''
  try {
    if (action === 'join') await api.joinActivity(id.value)
    if (action === 'cancel') await api.cancelActivity(id.value)
    if (action === 'checkin') {
      if (!state.demo) throw new Error('现场签到凭证尚未接入，请联系活动组织者。')
      await api.checkinActivity(id.value)
    }
    confirmLeave.value = false
    feedback.value =
      action === 'cancel'
        ? '已退出本次报名，可以重新选择喜欢的活动。'
        : action === 'checkin'
          ? '已完成演示签到，不代表实际到场。'
          : activity.value?.participantStatus === 'WAITLISTED'
            ? '已加入候补，目前还未获得正式名额。'
            : state.demo
              ? '演示报名成功，没有真实活动名额被占用。'
              : '报名成功，请按活动说明参加。'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作没有完成，请再试一次。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page detail-page activity-detail" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="活动详情" :back="true" />
    <DemoNotice />
    <template v-if="activity">
      <view class="poster">
        <image :src="activity.cover" class="poster-image" mode="aspectFill" />
        <view class="poster-tags">
          <text class="poster-tag">{{ activity.category }}</text>
          <text class="poster-tag free">免费参加</text>
        </view>
      </view>
      <view class="content">
        <view class="surface summary-card">
          <text class="activity-title">{{ activity.title }}</text>
          <view class="organizer">
            <view class="avatar">{{ activity.organizer.slice(0, 1) }}</view>
            <view>
              <text class="organizer-name">{{ activity.organizer }}</text>
              <text class="organizer-note">活动发起人</text>
            </view>
          </view>
          <view class="separator" />
          <view class="meta-row">
            <view class="meta-icon"><AppIcon name="time" :size="20" /></view>
            <view>
              <text class="meta-main">{{ activity.date }}</text>
              <text class="meta-sub">{{ activity.time }}</text>
            </view>
          </view>
          <view class="meta-row">
            <view class="meta-icon"><AppIcon name="location" :size="20" /></view>
            <view>
              <text class="meta-main">{{ activity.location }}</text>
              <text class="meta-sub">请按活动说明在公共地点集合</text>
            </view>
          </view>
        </view>
        <view v-if="activity.status === 'PENDING_REVIEW'" class="status-panel review">
          <AppIcon name="time" :size="21" />
          <view>
            <text class="panel-title">活动正在等待审核</text>
            <text class="panel-description">
              提交成功后会保留在我的发布中。审核通过后才开放报名。
            </text>
          </view>
        </view>
        <view v-else-if="activity.status === 'CANCELLED'" class="status-panel review">
          <AppIcon name="info-circle" :size="21" />
          <view>
            <text class="panel-title">活动已取消</text>
            <text class="panel-description">本次活动不再接收报名，原有记录仍可查看。</text>
          </view>
        </view>
        <view v-else-if="participation !== 'NONE'" class="status-panel">
          <AppIcon :name="participation === 'WAITLISTED' ? 'time' : 'check'" :size="22" />
          <view>
            <text class="panel-title">{{ statusTitle }}</text>
            <text class="panel-description">
              {{
                participation === 'WAITLISTED'
                  ? `当前候补第 ${activity.waitlistPosition ?? '—'} 位。名额确认前请勿将候补视为报名成功。`
                  : participation === 'CHECKED_IN'
                    ? state.demo
                      ? '这是演示记录，没有验证现场签到凭证。'
                      : '你的签到状态已记录。'
                    : `${activity.date} ${activity.time}，记得预留一点路上的时间。`
              }}
            </text>
          </view>
        </view>
        <view class="surface detail-card">
          <view class="section-row">
            <text class="section-heading">一起参加</text>
            <text class="spots">{{ activity.occupied }} / {{ activity.capacity }} 人</text>
          </view>
          <view class="capacity-track">
            <view class="capacity-fill" :style="{ width: `${percent}%` }" />
          </view>
          <text class="helper">
            {{
              remaining
                ? `还有 ${remaining} 个名额，报名结果以提交后返回的状态为准。`
                : '正式名额已满，可以加入候补等待空位。'
            }}
          </text>
          <text v-if="state.demo" class="demo-small">参与人数为演示数据</text>
        </view>
        <view class="surface detail-card">
          <text class="section-heading">关于这次相聚</text>
          <text class="description">
            {{ activity.description || '活动详情由发起人补充，请在参加前确认时间与集合地点。' }}
          </text>
        </view>
        <view class="surface detail-card">
          <text class="section-heading">参加前了解</text>
          <view class="rule">
            <text class="rule-number">01</text>
            <text>本次为免费活动，报名不会收取费用。</text>
          </view>
          <view class="rule">
            <text class="rule-number">02</text>
            <text>如行程有变，请在签到前取消报名，把名额留给其他同学。</text>
          </view>
          <view class="rule">
            <text class="rule-number">03</text>
            <text>候补需要等正式名额确认；请留意消息中的活动通知。</text>
          </view>
        </view>
        <view v-if="confirmLeave" class="leave-panel">
          <text class="section-heading">
            {{ participation === 'WAITLISTED' ? '要退出候补吗？' : '要取消本次报名吗？' }}
          </text>
          <text class="helper">取消后不会保留原名额或候补顺序。</text>
          <view class="leave-actions">
            <button class="btn btn-secondary" :disabled="busy" @click="confirmLeave = false">
              继续参加
            </button>
            <button class="btn btn-danger" :loading="busy" :disabled="busy" @click="act('cancel')">
              确认退出
            </button>
          </view>
        </view>
        <text v-if="error" class="form-error result-message">{{ error }}</text>
        <text v-if="feedback" class="result-message success-message">{{ feedback }}</text>
        <view class="footer-note">相聚不必很盛大，开心就很好。</view>
      </view>
      <view class="action-bar activity-actions">
        <template v-if="activity.status === 'OPEN'">
          <button
            v-if="canLeave"
            class="btn btn-secondary leave-button"
            :disabled="busy"
            @click="confirmLeave = true"
          >
            {{ participation === 'WAITLISTED' ? '退出候补' : '取消报名' }}
          </button>
          <button
            v-if="participation === 'NONE'"
            class="btn btn-primary primary-action"
            :loading="busy"
            :disabled="busy"
            @click="act('join')"
          >
            {{ remaining ? '免费报名参加' : '加入候补名单' }}
          </button>
          <button
            v-else-if="participation === 'CONFIRMED'"
            class="btn btn-primary primary-action"
            :loading="busy"
            :disabled="busy || !state.demo"
            @click="act('checkin')"
          >
            {{ state.demo ? '体验模拟签到' : '现场签到待接入' }}
          </button>
          <text v-else class="footer-state">
            {{
              participation === 'WAITLISTED'
                ? '等待空位，结果会在消息中更新'
                : state.demo
                  ? '已完成演示签到'
                  : '已签到，享受活动吧'
            }}
          </text>
        </template>
        <text v-else class="footer-state">
          {{ activity.status === 'PENDING_REVIEW' ? '审核通过后开放报名' : '活动已取消，无法报名' }}
        </text>
      </view>
    </template>
    <view v-else class="content">
      <EmptyState
        title="没有找到这场活动"
        description="活动可能已移除，可以返回校园看看其他活动。"
        icon="calendar"
      />
      <button class="btn btn-secondary" @click="back">返回校园</button>
    </view>
  </view>
</template>

<style scoped>
.activity-detail {
  padding-bottom: 118px;
}
.poster {
  position: relative;
  height: 225px;
  background: #e7eee5;
}
.poster-image {
  height: 100%;
  width: 100%;
  display: block;
}
.poster-tags {
  position: absolute;
  left: 18px;
  bottom: 17px;
  display: flex;
  gap: 8px;
}
.poster-tag {
  font-size: calc(12px * var(--font-scale, 1));
  padding: 5px 10px;
  border-radius: 4px;
  color: #314c3b;
  background: #ffffffec;
}
.poster-tag.free {
  color: #fff;
  background: #274c38df;
}
.summary-card {
  position: relative;
  margin-top: 14px;
  border-radius: 10px;
  padding: 20px 18px;
}
.activity-title {
  font-size: calc(23px * var(--font-scale, 1));
  line-height: 1.5;
  font-weight: 600;
  display: block;
  letter-spacing: 0.2px;
}
.organizer {
  display: flex;
  gap: 9px;
  align-items: center;
  margin-top: 18px;
}
.avatar {
  height: 34px;
  width: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #edf1e5;
  color: #80906c;
  border-radius: 50%;
  font-size: calc(14px * var(--font-scale, 1));
}
.organizer-name {
  display: block;
  font-size: calc(13px * var(--font-scale, 1));
  color: #4e6054;
}
.organizer-note {
  display: block;
  font-size: calc(11px * var(--font-scale, 1));
  color: #919b8c;
  margin-top: 3px;
}
.separator {
  height: 1px;
  background: #edf1e9;
  margin: 20px 0 17px;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-top: 17px;
}
.meta-icon {
  width: 24px;
  flex-shrink: 0;
  color: #819477;
}
.meta-main {
  font-size: calc(15px * var(--font-scale, 1));
  font-weight: 500;
  display: block;
  overflow-wrap: anywhere;
}
.meta-sub {
  font-size: calc(12px * var(--font-scale, 1));
  color: #869180;
  display: block;
  line-height: 1.6;
  margin-top: 3px;
}
.status-panel {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 17px;
  background: #eaf5eb;
  border: 1px solid #dceadc;
  border-radius: 9px;
  color: #09835a;
  margin-top: 14px;
}
.status-panel > view:last-child {
  flex: 1;
}
.status-panel.review {
  background: #f6f2e8;
  border-color: #eee6d4;
  color: #967b45;
}
.panel-title {
  display: block;
  font-size: calc(15px * var(--font-scale, 1));
  font-weight: 600;
}
.panel-description {
  display: block;
  color: #6c826d;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.8;
  margin-top: 5px;
}
.review .panel-description {
  color: #928466;
}
.detail-card {
  padding: 19px 18px;
  border-radius: 9px;
  margin-top: 14px;
}
.section-heading {
  display: block;
  font-size: calc(16px * var(--font-scale, 1));
  font-weight: 600;
}
.section-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.spots {
  color: #07855d;
  font-size: calc(14px * var(--font-scale, 1));
  font-weight: 500;
}
.capacity-track {
  height: 5px;
  border-radius: 5px;
  background: #ecf1e8;
  margin-top: 18px;
  overflow: hidden;
}
.capacity-fill {
  height: 100%;
  background: #6eae8b;
  border-radius: 5px;
}
.helper {
  display: block;
  font-size: calc(12px * var(--font-scale, 1));
  line-height: 1.8;
  color: #83917f;
  margin-top: 12px;
}
.demo-small {
  display: block;
  font-size: calc(11px * var(--font-scale, 1));
  color: #9c9f83;
  margin-top: 8px;
}
.description {
  display: block;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: calc(15px * var(--font-scale, 1));
  line-height: 1.95;
  color: #697863;
  margin-top: 14px;
}
.rule {
  display: flex;
  gap: 11px;
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.8;
  color: #7d8875;
  margin-top: 14px;
}
.rule-number {
  font-size: calc(11px * var(--font-scale, 1));
  color: #a6b49c;
  padding-top: 2px;
  flex-shrink: 0;
}
.activity-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.primary-action {
  flex: 1;
  min-width: 0;
}
.leave-button {
  flex: 0 0 auto;
}
.footer-state {
  flex: 1;
  text-align: center;
  color: #7d8b74;
  font-size: calc(13px * var(--font-scale, 1));
  line-height: 1.7;
}
.footer-note {
  text-align: center;
  color: #99a58f;
  font-size: calc(12px * var(--font-scale, 1));
  padding: 23px 0;
}
.leave-panel {
  margin-top: 15px;
  border: 1px solid #e7d5cf;
  background: #fffaf8;
  border-radius: 8px;
  padding: 16px;
}
.leave-actions {
  display: flex;
  gap: 10px;
  margin-top: 15px;
}
.leave-actions button {
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
  .poster {
    height: 190px;
  }
  .activity-title {
    font-size: calc(21px * var(--font-scale, 1));
  }
  .primary-action,
  .leave-button {
    font-size: calc(13px * var(--font-scale, 1));
  }
  .leave-button {
    padding-left: 10px;
    padding-right: 10px;
  }
}
</style>
