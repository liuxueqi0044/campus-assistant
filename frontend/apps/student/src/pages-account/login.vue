<script setup lang="ts">
import { computed, ref } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { back } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import AppIcon from '@/components/AppIcon.vue'

const busy = ref(false)
const error = ref('')
const wechatLoginAvailable = computed(() => state.demo || api.canWechatLogin())
async function login() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.login()
    back()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录没有完成，可以再试一次。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page detail-page login-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="登录" back />
    <view class="content">
      <DemoNotice />
      <view class="welcome">
        <view class="welcome-mark">
          <view class="leaf leaf-one" />
          <view class="leaf leaf-two" />
          <view class="leaf-stem" />
        </view>
        <text class="welcome-eyebrow">你的校园日常</text>
        <text class="welcome-title">
          熟悉的校园，
          <text class="line-break">多一点方便。</text>
        </text>
        <text class="welcome-description">
          让闲置遇见需要的人，
          <text class="line-break">也让每件小事都有着落。</text>
        </text>
      </view>
      <view class="login-benefits">
        <view class="benefit">
          <AppIcon name="chat" :size="20" />
          <text>和同学直接聊</text>
        </view>
        <view class="benefit">
          <AppIcon name="calendar" :size="20" />
          <text>安排校园生活</text>
        </view>
        <view class="benefit">
          <AppIcon name="star" :size="20" />
          <text>收藏心动闲置</text>
        </view>
      </view>
      <view v-if="state.user.loggedIn" class="signed-in surface">
        <text class="signed-in-title">你已登录{{ state.demo ? '演示账号' : '' }}</text>
        <text class="muted">
          {{ state.user.name || '已登录账号' }} · {{ state.user.school || '未选择学校' }}
        </text>
        <button class="btn btn-primary login-action" @click="back">返回继续浏览</button>
      </view>
      <view v-else class="login-actions">
        <view v-if="error" class="form-error section">{{ error }}</view>
        <button
          class="btn btn-primary login-action"
          :disabled="busy || !wechatLoginAvailable"
          @click="login"
        >
          <AppIcon name="user" :size="20" />
          <text>
            {{
              busy
                ? '正在登录…'
                : state.demo
                  ? '进入演示账号'
                  : wechatLoginAvailable
                    ? '微信登录'
                    : '请在微信小程序中登录'
            }}
          </text>
        </button>
        <button class="guest-button" :disabled="busy" @click="back">先随便看看</button>
      </view>
      <view class="login-explanation">
        <text v-if="state.demo">
          使用预设演示身份，不会调用微信授权。
          <text class="line-break">请勿在演示中提交真实证件或联系方式。</text>
        </text>
        <text v-else-if="wechatLoginAvailable">
          微信登录仅用于建立账号。
          <text class="line-break">学生认证需要单独提交，不代表学校官方背书。</text>
        </text>
        <text v-else>
          H5 当前只支持公开浏览。
          <text class="line-break">不会生成或伪造微信 code，请在微信小程序中完成登录。</text>
        </text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.line-break {
  display: block;
}
.login-page {
  background: #f6f8f4;
  min-height: 100vh;
}
.welcome {
  padding: 43px 14px 29px;
}
.welcome-mark {
  position: relative;
  width: 58px;
  height: 59px;
  margin-bottom: 30px;
}
.leaf {
  position: absolute;
  background: #92b297;
  border-radius: 2px 24px 2px 24px;
  transform: rotate(-10deg);
  height: 28px;
  width: 27px;
}
.leaf-one {
  left: 0;
  top: 7px;
}
.leaf-two {
  right: 0;
  top: 0;
  transform: rotate(90deg);
  background: #648d6e;
}
.leaf-stem {
  position: absolute;
  width: 3px;
  height: 29px;
  background: #65876b;
  left: 27px;
  top: 24px;
  border-radius: 3px;
  transform: rotate(9deg);
}
.welcome-eyebrow {
  font-size: calc(12px * var(--font-scale, 1));
  color: #6c886c;
  letter-spacing: 3px;
  display: block;
  margin-bottom: 12px;
}
.welcome-title {
  display: block;
  font-size: calc(30px * var(--font-scale, 1));
  line-height: 1.5;
  font-weight: 600;
  color: #2e4735;
  letter-spacing: 1px;
}
.welcome-description {
  display: block;
  margin-top: 17px;
  font-size: calc(15px * var(--font-scale, 1));
  color: #83907d;
  line-height: 1.85;
}
.login-benefits {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin: 8px 10px 35px;
}
.benefit {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 10px;
  color: #658069;
  font-size: calc(11px * var(--font-scale, 1));
}
.login-action {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  width: 100%;
}
.guest-button {
  background: transparent;
  color: #84937d;
  margin-top: 13px;
  font-size: calc(14px * var(--font-scale, 1));
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.guest-button::after {
  border: 0;
}
.login-explanation {
  text-align: center;
  color: #9aa491;
  font-size: calc(11px * var(--font-scale, 1));
  line-height: 1.9;
  padding: 20px 0;
}
.signed-in {
  padding: 21px 17px;
  border-radius: 12px;
  text-align: center;
  font-size: calc(13px * var(--font-scale, 1));
}
.signed-in-title {
  display: block;
  font-size: calc(16px * var(--font-scale, 1));
  font-weight: 500;
  margin-bottom: 7px;
}
.signed-in .login-action {
  margin-top: 21px;
}
.benefit,
.login-explanation {
  font-size: calc(12px * var(--font-scale, 1));
}
</style>
