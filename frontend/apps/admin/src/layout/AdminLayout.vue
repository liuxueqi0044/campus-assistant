<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">校</span>
        <div>
          <strong>校园助手</strong>
          <small>运营台</small>
        </div>
      </div>
      <nav aria-label="校园运营">
        <router-link to="/campus/overview">
          <School class="nav-icon" />
          学校范围
        </router-link>
        <router-link to="/campus/verifications">
          <Checked class="nav-icon" />
          认证审核
        </router-link>
      </nav>
      <div class="boundary-note">菜单仅表示入口；所有数据和动作仍由服务端按学校范围授权。</div>
    </aside>
    <section class="workspace">
      <header>
        <SchoolScope />
        <el-button text @click="logout">退出登录</el-button>
      </header>
      <main>
        <router-view />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { Checked, School } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import SchoolScope from '@/components/SchoolScope.vue'
import { logoutAdmin } from '@/services/session'
import { useCampusScopeStore } from '@/stores/campusScope'

const router = useRouter()
const scope = useCampusScopeStore()

async function logout() {
  try {
    await logoutAdmin()
  } finally {
    scope.reset()
    await router.replace({ name: 'Login' })
  }
}
</script>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
  min-height: 100vh;
}

.sidebar {
  display: flex;
  flex-direction: column;
  padding: 22px 16px;
  color: #e8f5ee;
  background: #123c2c;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}

.brand-mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 11px;
  color: #123c2c;
  background: #bde7cc;
  font-weight: 800;
}

.brand strong,
.brand small {
  display: block;
}

.brand small {
  margin-top: 3px;
  color: #b9d2c4;
}

nav {
  display: grid;
  gap: 8px;
}

nav a {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 9px;
  color: #dcebe3;
  text-decoration: none;
}

nav a:hover,
nav a.router-link-active {
  color: white;
  background: #1f6048;
}

.nav-icon {
  width: 18px;
}

.boundary-note {
  margin-top: auto;
  padding: 12px;
  border: 1px solid #346b55;
  border-radius: 9px;
  color: #b9d2c4;
  font-size: 12px;
  line-height: 1.65;
}

.workspace {
  min-width: 0;
}

header {
  display: flex;
  height: 64px;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #e6e9ed;
  background: white;
}

@media (width <= 720px) {
  .shell {
    grid-template-columns: 1fr;
  }

  .sidebar {
    padding: 14px;
  }

  .brand,
  .boundary-note {
    display: none;
  }

  nav {
    grid-template-columns: 1fr 1fr;
  }

  header {
    padding: 0 14px;
  }
}
</style>
