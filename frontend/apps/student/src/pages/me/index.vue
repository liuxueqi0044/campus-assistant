<script setup lang="ts">
import { computed } from 'vue'
import { state } from '@/state'
import { go } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import BottomNav from '@/components/BottomNav.vue'
import AppIcon from '@/components/AppIcon.vue'
import DemoNotice from '@/components/DemoNotice.vue'

const campus = computed(() => state.campuses.find((c) => c.id === state.user.campusId))
const published = computed(
  () =>
    state.products.filter((p) => p.ownerId === state.user.id).length +
    state.activities.filter((a) => a.ownerId === state.user.id).length,
)
const errands = computed(
  () =>
    state.errands.filter((e) => e.requesterId === state.user.id || e.runnerId === state.user.id)
      .length,
)
const activities = computed(
  () => state.activities.filter((a) => a.participantStatus !== 'NONE').length,
)
const verificationText = computed(
  () => ({ NONE: '未认证', PENDING: '审核中', VERIFIED: '已认证' })[state.user.verification],
)
function openRecords(type: string) {
  go(state.user.loggedIn ? `/pages-account/records?type=${type}` : '/pages-account/login')
}
</script>

<template>
  <view class="page profile-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="我" subtitle="把校园里的小事，安排好">
      <template #right>
        <button class="icon-button" aria-label="设置" @click="go('/pages-account/settings')">
          <AppIcon name="setting" :size="23" />
        </button>
      </template>
    </PageHeader>
    <view class="content">
      <DemoNotice />
      <view v-if="state.user.loggedIn && state.privateState === 'error'" class="form-error section">
        {{ state.privateError }}
      </view>
      <view class="profile-card surface">
        <view class="profile-main" @click="!state.user.loggedIn && go('/pages-account/login')">
          <view class="profile-avatar">
            {{ state.user.loggedIn ? state.user.name.slice(0, 1) || '你' : '你' }}
          </view>
          <view class="profile-copy">
            <text class="profile-name">
              {{ state.user.loggedIn ? state.user.name || '已登录账号' : '欢迎来到校园' }}
            </text>
            <text class="profile-campus">
              {{
                state.user.loggedIn
                  ? `${state.user.school} · ${campus?.name || '未选择校区'}`
                  : '登录后，保存你的校园日常'
              }}
            </text>
            <view v-if="state.user.loggedIn" class="profile-labels">
              <text
                :class="['verification-pill', { verified: state.user.verification === 'VERIFIED' }]"
              >
                {{ state.demo ? '演示身份 · ' : '' }}{{ verificationText }}
              </text>
              <text v-if="state.user.runner" class="runner-pill">
                {{ state.demo ? '演示跑腿员' : '跑腿员' }}
              </text>
            </view>
            <text v-else class="sign-in-link">
              {{ state.demo ? '进入演示账号' : '立即登录' }} →
            </text>
          </view>
        </view>
        <view class="profile-stats">
          <view class="stat" @click="openRecords('published')">
            <text class="stat-value">{{ state.user.loggedIn ? published : '—' }}</text>
            <text class="stat-label">我的发布</text>
          </view>
          <view class="stat" @click="openRecords('favorites')">
            <text class="stat-value">{{ state.user.loggedIn ? state.favorites.length : '—' }}</text>
            <text class="stat-label">我的收藏</text>
          </view>
          <view class="stat" @click="openRecords('activities')">
            <text class="stat-value">{{ state.user.loggedIn ? activities : '—' }}</text>
            <text class="stat-label">参与活动</text>
          </view>
        </view>
      </view>
      <view
        class="identity-card"
        @click="go(state.user.loggedIn ? '/pages-account/verify' : '/pages-account/login')"
      >
        <view class="identity-symbol"><AppIcon name="check" :size="22" /></view>
        <view class="identity-copy">
          <text class="identity-title">
            {{
              state.user.loggedIn && state.user.verification === 'VERIFIED'
                ? '学生身份，让交流更放心'
                : state.user.verification === 'PENDING' && state.user.loggedIn
                  ? '认证资料已提交'
                  : '完善你的学生身份'
            }}
          </text>
          <text class="identity-description">
            {{ state.demo ? '演示认证状态，不代表真实校方核验' : '认证资料不向其他同学公开' }}
          </text>
        </view>
        <AppIcon name="arrow-right" :size="16" />
      </view>
      <text class="section-label">我的校园生活</text>
      <view class="menu-group surface">
        <view class="menu-row" @click="openRecords('published')">
          <view class="menu-icon"><AppIcon name="edit" :size="21" /></view>
          <text class="menu-label">我的发布</text>
          <text class="menu-hint">闲置与活动</text>
          <AppIcon name="arrow-right" :size="16" />
        </view>
        <view class="menu-row" @click="openRecords('favorites')">
          <view class="menu-icon"><AppIcon name="star" :size="21" /></view>
          <text class="menu-label">我的收藏</text>
          <text class="menu-hint">留给心动的东西</text>
          <AppIcon name="arrow-right" :size="16" />
        </view>
        <view class="menu-row" @click="openRecords('errands')">
          <view class="menu-icon"><AppIcon name="cart" :size="21" /></view>
          <text class="menu-label">跑腿订单</text>
          <text class="menu-hint">
            {{ state.user.loggedIn && errands ? `${errands} 笔订单` : '我发起的 / 我接的' }}
          </text>
          <AppIcon name="arrow-right" :size="16" />
        </view>
        <view class="menu-row" @click="openRecords('activities')">
          <view class="menu-icon"><AppIcon name="calendar" :size="21" /></view>
          <text class="menu-label">我的活动</text>
          <text class="menu-hint">报名与候补</text>
          <AppIcon name="arrow-right" :size="16" />
        </view>
      </view>
      <text class="section-label">账号与偏好</text>
      <view class="menu-group surface">
        <view class="menu-row" @click="go('/pages-account/settings')">
          <view class="menu-icon"><AppIcon name="setting" :size="21" /></view>
          <text class="menu-label">设置</text>
          <text class="menu-hint">字号、首页与通知</text>
          <AppIcon name="arrow-right" :size="16" />
        </view>
        <view
          class="menu-row"
          @click="go(state.user.loggedIn ? '/pages-message/notices' : '/pages-account/login')"
        >
          <view class="menu-icon"><AppIcon name="notification" :size="21" /></view>
          <text class="menu-label">服务通知</text>
          <AppIcon name="arrow-right" :size="16" />
        </view>
      </view>
      <view class="profile-note">
        <text>校园里的事，简单一点。</text>
        <text v-if="state.demo" class="caption">当前为前端演示，所有身份与交易均为示例</text>
      </view>
    </view>
    <BottomNav active="me" />
  </view>
</template>

<style scoped>
.icon-button {
  width: 44px;
  height: 44px;
  display: flex;
  justify-content: center;
  align-items: center;
  background: transparent;
  padding: 0;
  margin: 0;
}
.icon-button::after {
  border: 0;
}
.profile-card {
  border-radius: 13px;
  margin-top: 18px;
  padding: 22px 18px 0;
}
.profile-main {
  display: flex;
  gap: 14px;
  align-items: center;
}
.profile-avatar {
  width: 62px;
  height: 62px;
  flex-shrink: 0;
  border-radius: 18px;
  background: #e0ece3;
  color: #4c7157;
  font-size: calc(29px * var(--font-scale, 1));
  display: flex;
  align-items: center;
  justify-content: center;
}
.profile-copy {
  min-width: 0;
  flex: 1;
}
.profile-name {
  font-size: calc(21px * var(--font-scale, 1));
  font-weight: 600;
  display: block;
}
.profile-campus {
  font-size: calc(13px * var(--font-scale, 1));
  color: var(--color-muted);
  display: block;
  margin-top: 5px;
  line-height: 1.5;
}
.profile-labels {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}
.verification-pill,
.runner-pill {
  font-size: calc(10px * var(--font-scale, 1));
  padding: 3px 6px;
  background: #f0f1ec;
  color: #7c8063;
  border-radius: 4px;
}
.verification-pill.verified {
  background: #ecf4ed;
  color: #477e58;
}
.runner-pill {
  background: #edf1f7;
  color: #668099;
}
.sign-in-link {
  font-size: calc(14px * var(--font-scale, 1));
  color: var(--color-primary);
  display: block;
  margin-top: 8px;
}
.profile-stats {
  display: flex;
  border-top: 1px solid var(--color-line);
  margin-top: 21px;
  padding: 17px 0;
}
.stat {
  flex: 1;
  text-align: center;
  min-height: 48px;
}
.stat + .stat {
  border-left: 1px solid var(--color-line);
}
.stat-value {
  font-size: calc(22px * var(--font-scale, 1));
  font-weight: 600;
  display: block;
  line-height: 1.2;
}
.stat-label {
  font-size: calc(12px * var(--font-scale, 1));
  color: var(--color-muted);
  display: block;
  margin-top: 7px;
}
.identity-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 13px;
  background: #eaf1e9;
  border-radius: 11px;
  margin-top: 13px;
  color: #457453;
}
.identity-symbol {
  width: 31px;
  flex-shrink: 0;
}
.identity-copy {
  flex: 1;
  min-width: 0;
}
.identity-title {
  font-size: calc(14px * var(--font-scale, 1));
  font-weight: 500;
  display: block;
  line-height: 1.4;
}
.identity-description {
  font-size: calc(11px * var(--font-scale, 1));
  color: #748774;
  display: block;
  margin-top: 5px;
  line-height: 1.5;
}
.section-label {
  display: block;
  font-size: calc(12px * var(--font-scale, 1));
  color: #7e8a81;
  padding: 24px 2px 10px;
}
.menu-group {
  border-radius: 12px;
  overflow: hidden;
}
.menu-row {
  min-height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 15px;
  border-bottom: 1px solid var(--color-line);
}
.menu-row:last-child {
  border-bottom: 0;
}
.menu-icon {
  color: #64786a;
  display: flex;
  align-items: center;
  width: 26px;
  flex-shrink: 0;
}
.menu-label {
  font-size: calc(15px * var(--font-scale, 1));
  flex: 1;
}
.menu-hint {
  font-size: calc(12px * var(--font-scale, 1));
  color: #8c958f;
  max-width: 45%;
  text-align: right;
}
.profile-note {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  color: #8d998f;
  padding: 29px 0 13px;
  font-size: calc(13px * var(--font-scale, 1));
}
.profile-note .caption {
  text-align: center;
  font-size: calc(11px * var(--font-scale, 1));
}
.verification-pill,
.runner-pill,
.identity-description,
.profile-note .caption {
  font-size: calc(12px * var(--font-scale, 1));
}
.menu-label {
  font-size: calc(16px * var(--font-scale, 1));
}
.profile-campus,
.identity-title {
  font-size: calc(14px * var(--font-scale, 1));
}
</style>
