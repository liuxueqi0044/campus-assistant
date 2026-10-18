<template>
  <main class="login-page">
    <section class="login-card">
      <div class="title">
        <span>校</span>
        <div>
          <h1>校园助手运营台</h1>
          <p>使用已获授权的后台账号登录</p>
        </div>
      </div>
      <el-alert
        title="学校范围与平台租户相互独立"
        description="登录后仍由服务端逐项校验学校、校区和操作权限。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        class="session-alert"
        title="校园后台专用登录"
        :description="adminSessionCapability.reason"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="账号">
          <el-input v-model="form.username" autocomplete="username" autofocus />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <el-button
          class="submit"
          type="primary"
          :disabled="!adminSessionCapability.available"
          :loading="loading"
          @click="submit"
        >
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminSessionCapability, loginAdmin } from '@/services/session'

const router = useRouter()
const route = useRoute()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function submit() {
  if (!form.username.trim() || !form.password) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await loginAdmin({ username: form.username.trim(), password: form.password })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/campus/overview'
    await router.replace(
      redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/campus/overview'
    )
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 20px;
  background:
    radial-gradient(circle at 15% 20%, #d9f1e1 0, transparent 32%),
    linear-gradient(145deg, #eff7f2, #f8faf9 65%);
}

.login-card {
  width: min(100%, 420px);
  padding: 30px;
  border: 1px solid #dce6df;
  border-radius: 18px;
  background: rgb(255 255 255 / 94%);
  box-shadow: 0 22px 70px rgb(21 68 49 / 12%);
}

.title {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}

.title > span {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 14px;
  color: white;
  background: #176c49;
  font-size: 20px;
  font-weight: 800;
}

h1 {
  margin: 0;
  font-size: 22px;
}

p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
}

.el-alert,
.el-form {
  margin-top: 18px;
}

.session-alert {
  margin-top: 10px;
}

.submit {
  width: 100%;
  margin-top: 18px;
}
</style>
