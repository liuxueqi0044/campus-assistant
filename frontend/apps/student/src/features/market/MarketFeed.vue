<script setup lang="ts">
import { computed } from 'vue'
import { state } from '@/state'
import { go, money } from '@/utils'
import EmptyState from '@/components/EmptyState.vue'
const props = defineProps<{ query: string; category: string }>()
const items = computed(() =>
  state.products.filter(
    (p) =>
      p.campusId === state.campusId &&
      p.status !== 'PENDING_REVIEW' &&
      p.status !== 'SOLD' &&
      (props.category === '全部' || p.category === props.category) &&
      (!props.query.trim() || (p.title + ' ' + p.description).includes(props.query.trim())),
  ),
)
</script>
<template>
  <view>
    <view class="feed-heading">
      <text>闲置流转，好物继续陪伴</text>
      <text>{{ items.length }} 件好物</text>
    </view>
    <view class="goods-grid">
      <view
        v-for="p in items"
        :key="p.id"
        class="goods-card"
        @click="go(`/pages-market/detail?id=${p.id}`)"
      >
        <view class="picture">
          <image :src="p.image" mode="aspectFill" />
          <text class="condition">{{ p.type === 'WANTED' ? '求购' : p.condition }}</text>
          <text v-if="p.status === 'RESERVED'" class="reserved">已预约</text>
        </view>
        <view class="goods-copy">
          <text class="goods-title">{{ p.title }}</text>
          <view class="price">
            <text class="currency">¥</text>
            {{ money(p.priceFen) }}
            <text v-if="p.type === 'WANTED'" class="budget">预算</text>
          </view>
          <view class="seller">
            <view class="avatar">{{ p.avatar }}</view>
            <text>{{ p.seller }}</text>
            <text class="dot">·</text>
            <text class="location">{{ p.location }}</text>
          </view>
        </view>
      </view>
    </view>
    <EmptyState
      v-if="!items.length"
      title="还没有找到这件好物"
      description="换个关键词或分类试试，也可以发布求购。"
    />
    <text v-else class="list-end">同校好物，见面交接更方便</text>
  </view>
</template>
<style scoped>
.feed-heading {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: calc(12px * var(--font-scale, 1));
  color: #87938b;
  margin: 2px 0 15px;
}
.goods-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px;
}
.goods-card {
  background: white;
  border: 1px solid #e8ede9;
  border-radius: 11px;
  overflow: hidden;
  cursor: pointer;
}
.picture {
  position: relative;
  aspect-ratio: 1/1;
}
.picture image {
  width: 100%;
  height: 100%;
}
.condition {
  position: absolute;
  left: 9px;
  top: 9px;
  background: #ffffffd9;
  color: #687164;
  padding: 3px 6px;
  border-radius: 4px;
  font-size: calc(11px * var(--font-scale, 1));
}
.reserved {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
  background: #324439b8;
  color: white;
  font-size: calc(12px * var(--font-scale, 1));
  padding: 4px;
}
.goods-copy {
  padding: 12px 11px;
}
.goods-title {
  font-size: calc(15px * var(--font-scale, 1));
  font-weight: 500;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 46px;
}
.price {
  font-size: calc(22px * var(--font-scale, 1));
  font-weight: 600;
  color: #b15c3d;
  margin: 7px 0 9px;
  line-height: 1.25;
}
.currency {
  font-size: calc(13px * var(--font-scale, 1));
  margin-right: 3px;
}
.budget {
  font-size: calc(11px * var(--font-scale, 1));
  color: #8c968f;
  margin-left: 5px;
  font-weight: 400;
}
.seller {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #879189;
  font-size: calc(10px * var(--font-scale, 1));
  white-space: nowrap;
}
.avatar {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #eef1ea;
  color: #84947c;
  text-align: center;
  font-size: calc(10px * var(--font-scale, 1));
  flex-shrink: 0;
}
.location {
  overflow: hidden;
  text-overflow: ellipsis;
}
.dot {
  color: #bbc2bc;
}
.list-end {
  display: block;
  text-align: center;
  font-size: calc(12px * var(--font-scale, 1));
  color: #929c94;
  padding: 24px 0 12px;
}
@media (max-width: 350px) {
  .goods-grid {
    gap: 10px;
  }
  .goods-copy {
    padding: 10px;
  }
  .goods-title {
    font-size: calc(14px * var(--font-scale, 1));
  }
  .seller {
    font-size: calc(10px * var(--font-scale, 1));
  }
  .location,
  .dot {
    display: none;
  }
}
</style>
