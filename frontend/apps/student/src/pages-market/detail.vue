<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { state } from '@/state'
import { api } from '@/services/api'
import { go, money, requireLogin } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import DemoNotice from '@/components/DemoNotice.vue'
import EmptyState from '@/components/EmptyState.vue'
const id = ref(''),
  busy = ref(false),
  error = ref('')
onLoad((q) => {
  id.value = q?.id || ''
})
const item = computed(() => state.products.find((p) => p.id === id.value)),
  own = computed(() => item.value?.ownerId === state.user.id),
  saved = computed(() => state.favorites.includes(id.value))
async function action(fn: () => Promise<unknown>) {
  if (!requireLogin() || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await fn()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function contact() {
  await action(async () => {
    const cid = await api.contactSeller(id.value)
    go(`/pages-message/chat?id=${cid}`)
  })
}
</script>
<template>
  <view class="page detail-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="好物详情" back />
    <template v-if="item">
      <image class="hero" :src="item.image" mode="aspectFill" />
      <view class="content">
        <DemoNotice />
        <view class="price-row">
          <text class="price">¥ {{ money(item.priceFen) }}</text>
          <text class="tag">{{ item.type === 'WANTED' ? '求购预算' : item.condition }}</text>
          <button class="save" :disabled="busy" @click="action(() => api.toggleFavorite(id))">
            <AppIcon
              :name="saved ? 'heart-filled' : 'heart'"
              :color="saved ? '#b56852' : '#88998b'"
            />
            <text>{{ saved ? '已收藏' : '收藏' }}</text>
          </button>
        </view>
        <text class="heading product-title">{{ item.title }}</text>
        <text class="caption muted">{{ item.postedAt }} · {{ item.location }}</text>
        <view class="seller">
          <view class="avatar">{{ item.avatar }}</view>
          <view>
            <text>{{ item.seller }}</text>
            <text class="caption muted">{{ state.demo ? '演示用户' : '同校发布者' }}</text>
          </view>
          <text class="tag">{{ item.category }}</text>
        </view>
        <view class="description">
          <text class="title">物品介绍</text>
          <text class="body">{{ item.description }}</text>
          <view class="defects">
            <text class="label">{{ item.type === 'WANTED' ? '补充说明' : '瑕疵说明' }}</text>
            <text>{{ item.defects }}</text>
          </view>
          <view class="location">
            <AppIcon name="location" :size="18" />
            <text>交接地点：{{ item.location }}</text>
          </view>
        </view>
        <view class="status-note" v-if="item.status !== 'AVAILABLE'">
          <text>
            {{
              item.status === 'PENDING_REVIEW'
                ? '信息审核中，通过后才会出现在校园列表。'
                : item.status === 'SOLD'
                  ? '这件好物已经完成交接。'
                  : item.buyerConfirmed
                    ? '你已确认交接，等待卖家确认。'
                    : '这件好物已预约，双方可在消息中约定交接。'
            }}
          </text>
        </view>
        <view class="hint">
          当面检查物品与瑕疵，再确认交接。预约不扣款，平台当前不提供二手担保支付。
        </view>
        <view v-if="error" class="form-error">{{ error }}</view>
      </view>
      <view v-if="!own && item.status !== 'PENDING_REVIEW'" class="action-bar">
        <button class="btn btn-secondary" :disabled="busy" @click="contact">联系同学</button>
        <button
          v-if="item.type === 'SELL' && item.status === 'AVAILABLE'"
          class="btn btn-primary"
          :disabled="busy"
          @click="action(() => api.reserveProduct(id))"
        >
          {{ busy ? '处理中…' : '预约交接' }}
        </button>
        <button
          v-else-if="item.status === 'RESERVED' && !item.buyerConfirmed"
          class="btn btn-primary"
          :disabled="busy"
          @click="action(() => api.confirmProduct(id))"
        >
          我已完成交接
        </button>
      </view>
      <view v-if="item.status === 'RESERVED' && !item.buyerConfirmed" class="content">
        <button
          class="text-button"
          :disabled="busy"
          @click="action(() => api.cancelReservation(id))"
        >
          取消我的预约
        </button>
      </view>
    </template>
    <EmptyState
      v-else
      title="没有找到这件物品"
      description="它可能已被移除，请返回校园查看其他好物。"
    />
  </view>
</template>
<style scoped>
.hero {
  width: 100%;
  height: 320px;
  background: #ecece2;
}
.price-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}
.price {
  font-size: calc(29px * var(--font-scale, 1));
  font-weight: 600;
  color: #af5c3c;
}
.save {
  margin: 0 0 0 auto;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: calc(13px * var(--font-scale, 1));
  color: #809083;
  padding: 8px 0 8px 10px;
}
.product-title {
  display: block;
  margin: 12px 0 7px;
}
.seller {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 20px 0;
  border-bottom: 1px solid #e7ece6;
}
.seller > view:nth-child(2) {
  flex: 1;
}
.seller text {
  display: block;
}
.seller .tag {
  display: inline-flex;
}
.avatar {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e9eee2;
  border-radius: 50%;
  color: #718469;
}
.description {
  padding: 23px 0;
}
.body {
  display: block;
  font-size: calc(16px * var(--font-scale, 1));
  line-height: 1.9;
  margin-top: 12px;
  white-space: pre-wrap;
  color: #566257;
}
.defects {
  margin-top: 20px;
  background: #f0f1e9;
  padding: 13px 14px;
  border-radius: 8px;
  font-size: calc(14px * var(--font-scale, 1));
  color: #737564;
}
.defects .label {
  margin-bottom: 5px;
}
.location {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: calc(14px * var(--font-scale, 1));
  color: #667966;
  margin-top: 20px;
}
.status-note {
  background: #e8f1e8;
  color: #4d7451;
  padding: 13px;
  border-radius: 8px;
  font-size: calc(14px * var(--font-scale, 1));
  margin-bottom: 15px;
}
@media (max-width: 350px) {
  .hero {
    height: 260px;
  }
}
</style>
