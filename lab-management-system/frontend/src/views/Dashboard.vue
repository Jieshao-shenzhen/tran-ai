<template>
  <div>
    <el-card class="welcome mb-12">
      <h2 class="welcome-title">欢迎，{{ store.realName || store.username }}</h2>
      <p class="welcome-sub">当前身份：{{ roleText }}，祝您工作顺利！</p>
    </el-card>

    <el-row :gutter="16">
      <el-col v-for="item in entries" :key="item.path" :span="6" class="mb-12">
        <el-card class="entry" shadow="hover" @click="router.push('/' + item.path)">
          <el-icon :size="36" color="#409eff"><component :is="item.icon" /></el-icon>
          <div class="entry-title">{{ item.title }}</div>
          <div class="entry-desc">点击进入 {{ item.title }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'

const router = useRouter()
const store = useUserStore()

const roleText = computed(() => {
  const map: Record<string, string> = {
    SYSTEM_ADMIN: '系统管理员',
    LAB_ADMIN: '实验室管理员',
    DIRECTOR: '主任',
    DEAN: '院长',
    VICE_DEAN: '副院长',
    TEACHER: '教师',
    STUDENT: '学生'
  }
  return map[store.role] || store.role
})

// 快捷入口：从路由表过滤（与侧边菜单同规则）
const entries = computed(() => {
  const main = router.options.routes.find((r) => r.path === '/')
  const children = (main?.children || []) as {
    path: string
    meta?: { title?: string; icon?: string; roles?: string[] }
  }[]
  return children
    .filter((c) => !c.meta?.roles || c.meta.roles.includes(store.role))
    .map((c) => ({ path: c.path, title: c.meta?.title || c.path, icon: c.meta?.icon || 'Menu' }))
})
</script>

<style scoped>
.mb-12 {
  margin-bottom: 12px;
}
.welcome-title {
  margin: 0 0 8px;
  font-size: 20px;
  color: #303133;
}
.welcome-sub {
  margin: 0;
  color: #909399;
}
.entry {
  text-align: center;
  cursor: pointer;
}
.entry-title {
  margin-top: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.entry-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
