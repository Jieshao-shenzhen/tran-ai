<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>操作日志</span>
        <el-button type="primary" size="small" @click="load">刷新</el-button>
      </div>
    </template>
    <el-table :data="logs" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="操作人" min-width="110" />
      <el-table-column prop="action" label="操作" min-width="130">
        <template #default="{ row }">
          <el-tag size="small">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="详情" min-width="240" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="时间" width="180" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listLogs } from '../api/user'

interface OperationLog {
  id: number
  userId: number
  username: string
  action: string
  detail: string
  createdAt: string
}

const loading = ref(false)
const logs = ref<OperationLog[]>([])

async function load() {
  loading.value = true
  try {
    logs.value = (await listLogs()) as OperationLog[]
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
