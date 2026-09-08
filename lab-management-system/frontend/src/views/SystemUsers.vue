<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <el-input
            v-model="keyword"
            placeholder="搜索用户名/姓名"
            clearable
            style="width: 260px"
          />
          <el-button type="primary" @click="load">刷新</el-button>
          <div class="spacer" />
          <el-button type="success" @click="dialogVisible = true">新增用户</el-button>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".xlsx,.xls"
            :on-change="handleImport"
          >
            <el-button type="warning" plain>导入用户(Excel)</el-button>
          </el-upload>
        </div>
      </template>
      <el-table :data="filteredUsers" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增用户" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="登录账号" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" style="width: 100%">
            <el-option value="STUDENT" label="学生" />
            <el-option value="TEACHER" label="教师" />
            <el-option value="LAB_ADMIN" label="实验室管理员" />
            <el-option value="DIRECTOR" label="主任" />
            <el-option value="DEAN" label="院长/副院长" />
            <el-option value="SYSTEM_ADMIN" label="系统管理员" />
          </el-select>
        </el-form-item>
        <el-alert type="info" :closable="false" title="初始密码默认为 123456，请用户登录后自行修改。" />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createUser, importUsers, listUsers } from '../api/user'

interface SysUser {
  id: number
  username: string
  realName: string
  role: string
  status: number
  createdAt: string
}

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const users = ref<SysUser[]>([])
const dialogVisible = ref(false)
const form = reactive({ username: '', realName: '', role: 'STUDENT' })

const roleTextMap: Record<string, string> = {
  SYSTEM_ADMIN: '系统管理员',
  LAB_ADMIN: '实验室管理员',
  DIRECTOR: '主任',
  DEAN: '院长/副院长',
  TEACHER: '教师',
  STUDENT: '学生'
}
const roleTagMap: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  SYSTEM_ADMIN: 'danger',
  LAB_ADMIN: 'warning',
  DIRECTOR: 'primary',
  DEAN: 'danger',
  TEACHER: 'primary',
  STUDENT: 'success'
}

function roleText(role: string) {
  return roleTextMap[role] || role
}
function roleTagType(role: string) {
  return roleTagMap[role] || 'info'
}

// 后端 GET /users 仅支持 role 参数，关键词过滤在前端做
const filteredUsers = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return users.value
  return users.value.filter(
    (u) =>
      u.username.toLowerCase().includes(kw) ||
      (u.realName || '').toLowerCase().includes(kw)
  )
})

async function load() {
  loading.value = true
  try {
    users.value = (await listUsers()) as SysUser[]
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!form.username || !form.realName || !form.role) {
    ElMessage.warning('请填写完整信息')
    return
  }
  saving.value = true
  try {
    // 后端 create 仅接收 username/realName/role，密码固定 123456
    await createUser({ ...form })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    form.username = ''
    form.realName = ''
    form.role = 'STUDENT'
    await load()
  } finally {
    saving.value = false
  }
}

async function handleImport(uploadFile: any) {
  const file = uploadFile.raw
  if (!file) return
  try {
    const res = (await importUsers(file)) as string
    ElMessage.success(res || '导入完成')
    await load()
  } catch {
    ElMessage.error('导入失败，请检查文件格式')
  }
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.spacer {
  flex: 1;
}
</style>
