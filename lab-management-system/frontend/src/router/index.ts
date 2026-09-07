import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台', icon: 'DataBoard' } },
      { path: 'rooms', name: 'Rooms', component: () => import('../views/Rooms.vue'), meta: { title: '实训室管理', icon: 'OfficeBuilding' } },
      { path: 'devices', name: 'Devices', component: () => import('../views/Devices.vue'), meta: { title: '设备管理', icon: 'Monitor' } },
      { path: 'materials', name: 'Materials', component: () => import('../views/Materials.vue'), meta: { title: '耗材管理', icon: 'Goods', roles: ['LAB_ADMIN', 'SYSTEM_ADMIN'] } },
      { path: 'reservations', name: 'Reservations', component: () => import('../views/Reservations.vue'), meta: { title: '预约管理', icon: 'Calendar' } },
      { path: 'repairs', name: 'Repairs', component: () => import('../views/Repairs.vue'), meta: { title: '报修管理', icon: 'Tools' } },
      { path: 'reports', name: 'Reports', component: () => import('../views/Reports.vue'), meta: { title: '统计报表', icon: 'TrendCharts', roles: ['LAB_ADMIN', 'SYSTEM_ADMIN', 'TEACHER'] } },
      { path: 'system/users', name: 'SystemUsers', component: () => import('../views/SystemUsers.vue'), meta: { title: '用户管理', icon: 'User', roles: ['SYSTEM_ADMIN'] } },
      { path: 'system/logs', name: 'SystemLogs', component: () => import('../views/SystemLogs.vue'), meta: { title: '操作日志', icon: 'Document', roles: ['SYSTEM_ADMIN'] } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.public) return true
  if (!store.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.roles && !(to.meta.roles as string[]).includes(store.role)) {
    return { path: '/dashboard' }
  }
  return true
})

export default router