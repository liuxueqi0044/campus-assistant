<script setup lang="ts">
import { ref, computed } from 'vue'
import { state } from '@/state'
import { api } from '@/services/api'
import { go, requireLogin } from '@/utils'
import PageHeader from '@/components/PageHeader.vue'
import DemoNotice from '@/components/DemoNotice.vue'
const title = ref(''),
  description = ref(''),
  defects = ref(''),
  price = ref(''),
  location = ref(''),
  type = ref<'SELL' | 'WANTED'>('SELL'),
  category = ref(0),
  condition = ref(0),
  image = ref('/static/products/book.png'),
  busy = ref(false),
  error = ref(''),
  published = ref('')
const categories = ['教材书籍', '数码配件', '宿舍生活', '运动户外'],
  conditions = ['九成新', '八成新', '七成新', '功能正常'],
  images = ['book', 'lamp', 'keyboard', 'racket', 'camera']
const ready = computed(
  () =>
    !!title.value.trim() &&
    !!description.value.trim() &&
    /^\d+(\.\d{1,2})?$/.test(price.value) &&
    !!location.value.trim(),
)
async function submit() {
  if (!requireLogin() || busy.value) return
  busy.value = true
  error.value = ''
  try {
    if (!state.demo) throw new Error('真实发布还需接入图片上传与审核接口，请先完成后端联调。')
    if (!ready.value) throw new Error('请完整填写标题、描述、价格和交接地点。')
    const p = await api.publishProduct({
      title: title.value,
      description: description.value,
      defects: defects.value || '卖家未补充，请当面检查',
      priceFen: Math.round(Number(price.value) * 100),
      location: location.value,
      type: type.value,
      category: categories[category.value],
      condition: conditions[condition.value],
      image: image.value,
    })
    published.value = p.id
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <view class="page detail-page" :class="{ 'large-type': state.fontScale > 1 }">
    <PageHeader title="发布闲置" subtitle="给好物一个继续发光的机会" back />
    <view class="content">
      <DemoNotice />
      <view v-if="published" class="success">
        <text class="heading">发布已提交</text>
        <text class="muted">信息正在等待审核，通过后才会在校园中展示。</text>
        <button class="btn btn-primary" @click="go(`/pages-market/detail?id=${published}`)">
          查看我的发布
        </button>
      </view>
      <template v-else>
        <view class="types">
          <button :class="{ selected: type === 'SELL' }" @click="type = 'SELL'">出售闲置</button>
          <button :class="{ selected: type === 'WANTED' }" @click="type = 'WANTED'">
            发布求购
          </button>
        </view>
        <view class="field">
          <text class="label">演示封面</text>
          <view class="images">
            <image
              v-for="img in images"
              :key="img"
              :src="`/static/products/${img}.png`"
              :class="{ selected: image.includes(img) }"
              @click="image = `/static/products/${img}.png`"
            />
          </view>
          <text class="hint">本版使用示意插画；真实商品图片上传将在后端联调后开放。</text>
        </view>
        <view class="field">
          <text class="label">标题</text>
          <input
            v-model="title"
            class="form-input"
            maxlength="40"
            placeholder="例如：高数上下册，少量笔记"
          />
        </view>
        <view class="field">
          <text class="label">物品描述</text>
          <textarea
            v-model="description"
            class="form-textarea"
            maxlength="1000"
            placeholder="说说物品的使用情况、配件和交接偏好"
          />
        </view>
        <view class="pair">
          <view class="field">
            <text class="label">分类</text>
            <picker
              :range="categories"
              :value="category"
              @change="category = Number($event.detail.value)"
            >
              <view class="form-input choice">{{ categories[category] }}⌄</view>
            </picker>
          </view>
          <view class="field">
            <text class="label">成色</text>
            <picker
              :range="conditions"
              :value="condition"
              @change="condition = Number($event.detail.value)"
            >
              <view class="form-input choice">{{ conditions[condition] }}⌄</view>
            </picker>
          </view>
        </view>
        <view class="field">
          <text class="label">{{ type === 'SELL' ? '售价' : '预算' }}（元）</text>
          <input v-model="price" class="form-input" type="digit" maxlength="8" placeholder="0.00" />
        </view>
        <view class="field">
          <text class="label">瑕疵说明</text>
          <input
            v-model="defects"
            class="form-input"
            maxlength="160"
            placeholder="如划痕、缺件；没有也请如实说明"
          />
        </view>
        <view class="field">
          <text class="label">公共交接地点</text>
          <input
            v-model="location"
            class="form-input"
            maxlength="60"
            placeholder="例如：图书馆南门"
          />
        </view>
        <text class="hint">
          发布到
          {{
            state.campuses.find((c) => c.id === state.campusId)?.name
          }}。请勿填写手机号、寝室号等个人信息。
        </text>
        <view v-if="error" class="form-error">{{ error }}</view>
        <view class="action-bar">
          <button class="btn btn-primary" :disabled="busy || !ready" @click="submit">
            {{ busy ? '提交中…' : '提交发布' }}
          </button>
        </view>
      </template>
    </view>
  </view>
</template>
<style scoped>
.types {
  display: flex;
  gap: 12px;
  margin: 17px 0 24px;
}
.types button {
  flex: 1;
  background: #eff2ed;
  color: #83917e;
  font-size: calc(15px * var(--font-scale, 1));
  padding: 10px;
}
.types button.selected {
  background: #e4eee1;
  color: #437149;
  border: 1px solid #cdddc9;
}
.images {
  display: flex;
  gap: 8px;
  margin-bottom: 9px;
}
.images image {
  width: calc((100% - 32px) / 5);
  height: 65px;
  border: 2px solid transparent;
  border-radius: 8px;
}
.images image.selected {
  border-color: #07855d;
}
.pair {
  display: flex;
  gap: 15px;
}
.pair .field {
  flex: 1;
  min-width: 0;
  margin-bottom: 0;
}
.choice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: calc(14px * var(--font-scale, 1));
}
.success {
  padding: 60px 0;
  display: flex;
  flex-direction: column;
  gap: 22px;
  text-align: center;
}
</style>
