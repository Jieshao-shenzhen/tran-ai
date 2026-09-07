<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo" :class="{ collapsed }">实训室管理系统</div>
      <el-menu
        class="menu"
        :default-active="route.path"
        router
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#001529"
        text-color="rgba(255,255,255,0.75)"
        active-text-color="#409eff"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="'/' + item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title><span>{{ item.title }}</span></template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <el-icon class="collapse-btn" @click="collapsed = !collapsed">
          <Expand v-if="collapsed" />
          <Fold v-else />
        </el-icon>
        <div class="header-right">
          <el-tag size="small" type="info">{{ roleText }}</el-tag>
          <span class="username">{{ store.username }}</span>
          <el-button size="small" @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const collapsed = ref(false)

const roleText = computed(() => {
  const map: Record<string, string> = {
    SYSTEM_ADMIN: '系统管理员',
    LAB_ADMIN: '实验室管理员',
    TEACHER: '教师',
    STUDENT: '学生'
  }
  return map[store.role] || store.role
})

// 菜单从路由表过滤：meta.roles 存在时当前角色必须命中
const menus = computed(() => {
  const main = router.options.routes.find((r) => r.path === '/')
  const children = (main?.children || []) as {
    path: string
    meta?: { title?: string; icon?: string; roles?: string[] }
  }[]
  return children
    .filter((c) => !c.meta?.roles || c.meta.roles.includes(store.role))
    .map((c) => ({ path: c.path, title: c.meta?.title || c.path, icon: c.meta?.icon || 'Menu' }))
})

function handleLogout() {
  store.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background-color: #001529;
  transition: width 0.2s;
}
.logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
}
.logo.collapsed {
  font-size: 0;
}
.menu {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.main {
  background: #f5f7fa;
}
</style>