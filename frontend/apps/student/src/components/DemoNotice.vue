<script setup lang="ts">
import { state } from '@/state'
function explain() {
  uni.showModal({
    title: '前端体验版',
    content:
      '学校、身份、商品和订单均为演示数据。操作只改变本次演示状态，不会真实付款、报名或发送给其他人。真实后端与微信能力尚待联调。',
    showCancel: false,
    confirmText: '知道了',
    confirmColor: '#07855d',
  })
}
</script>
<template>
  <view v-if="state.demo" class="demo-notice" role="note" @click="explain">
    <view class="demo-light" />
    <text>演示数据</text>
    <text class="demo-caption">仅供体验，不产生真实交易</text>
    <text class="demo-info">ⓘ</text>
  </view>
</template>
<style scoped>
.demo-notice {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: calc(10px * var(--font-scale, 1));
  color: #859187;
  min-height: 28px;
  padding: 5px 0;
  cursor: pointer;
  flex-wrap: wrap;
}
.demo-light {
  height: 5px;
  width: 5px;
  background: #8ba58e;
  border-radius: 50%;
}
.demo-caption {
  color: #a2aba4;
  font-size: calc(10px * var(--font-scale, 1));
  flex: 1;
}
.demo-info {
  font-size: calc(13px * var(--font-scale, 1));
  color: #95a197;
}
</style>
