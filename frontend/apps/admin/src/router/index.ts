import { createRouter, createWebHistory } from 'vue-router'
import { hasAdminSession } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' }
    },
    {
      path: '/',
      component: () => import('@/layout/AdminLayout.vue'),
      redirect: '/campus/overview',
      children: [
        {
          path: 'campus/overview',
          name: 'CampusOverview',
          component: () => import('@/views/campus/OverviewView.vue'),
          meta: { title: '学校范围' }
        },
        {
          path: 'campus/verifications',
          name: 'CampusVerificationReview',
          component: () => import('@/views/campus/ReviewView.vue'),
          meta: { title: '认证审核' }
        }
      ]
    },
    {
      path: '/403',
      name: 'Forbidden',
      component: () => import('@/views/StatusView.vue'),
      props: { status: '403', title: '没有权限访问此页面' },
      meta: { public: true, title: '403' }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/StatusView.vue'),
      props: { status: '404', title: '页面不存在' },
      meta: { public: true, title: '404' }
    }
  ]
})

router.beforeEach((to) => {
  document.title = `${String(to.meta.title || '运营台')} · 校园助手`
  if (to.meta.public || hasAdminSession()) return true
  return { name: 'Login', query: { redirect: to.fullPath } }
})

export default router
