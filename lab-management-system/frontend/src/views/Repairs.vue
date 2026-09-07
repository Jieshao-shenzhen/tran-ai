<template>
  <div>
    <el-card class="mb-12">
      <template #header><span>提交报修</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" style="max-width: 560px">
        <el-form-item label="设备" prop="deviceId">
          <el-select v-model="form.deviceId" placeholder="请选择设备" filterable style="width: 100%">
            <el-option v-for="d in devices" :key="d.id" :label="`${d.name}（${d.code}）`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请描述故障现象" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleCreate">提交报修</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header><span>报修工单</span></template>
      <el-table :data="repairs" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="设备" min-width="130">
          <template #default="{ row }">{{ deviceName(row.deviceId) }}</template>
        </el-table-column>
        <el-table-column label="实训室" width="120">
          <template #default="{ row }">{{ roomName(row.roomId) }}</template>
        </el-table-column>
        <el-table-column prop="description" label="故障描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="报修人" width="100">
          <template #default="{ row }">{{ userName(row.reporterId) }}</template>
        </el-table-column>
        <el-table-column label="维修员" width="100">
          <template #default="{ row }">{{ userName(row.assigneeId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理结果" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.result || '—' }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="isAdmin">
              <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="openAssign(row)">派单</el-button>
              <el-button v-if="row.status === 'PENDING'" size="small" type="danger" plain @click="handleReject(row)">驳回</el-button>
              <el-button v-if="row.status === 'ASSIGNED'" size="small" type="success" @click="handleFinish(row)">完工</el-button>
              <el-button v-if="row.status === 'COMPLETED'" size="small" type="info" plain @click="handleVerify(row)">验收</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 派单对话框 -->
    <el-dialog v-model="assignVisible" title="派单" width="420px">
      <el-form label-width="70px">
        <el-form-item label="维修员">
          <el-select v-model="assigneeId" placeholder="请选择维修员" style="width: 100%">
            <el-option
              v-for="u in labAdmins"
              :key="u.id"
              :label="`${u.realName || u.username}（${u.username}）`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssign">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '../store/user'
import { listRooms, listDevices } from '../api/resource'
import { listUsers } from '../api/user'
import {
  listRepairs,
  createRepair,
  assignRepair,
  rejectRepair,
  finishRepair,
  verifyRepair
} from '../api/business'

interface Device {
  id: number
  code: string
  name: string
  roomId: number
}
interface UserItem {
  id: number
  username: string
  realName: string
  role: string
}
interface RepairRow {
  id: number
  deviceId: number
  roomId: number
  reporterId: number
  assigneeId: number
  description: string
  status: string
  result: string
  completedAt: string
}

const store = useUserStore()
const isAdmin = computed(() => ['LAB_ADMIN', 'SYSTEM_ADMIN'].includes(store.role))

const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)
const repairs = ref<RepairRow[]>([])
const devices = ref<Device[]>([])
const rooms = ref<{ id: number; name: string }[]>([])
const userMap = ref<Record<number, string>>({})
const labAdmins = ref<UserItem[]>([])

const form = reactive({
  deviceId: null as number | null,
  description: ''
})
const rules: FormRules = {
  deviceId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  description: [{ required: true, message: '请描述故障', trigger: 'blur' }]
}

// 派单对话框
const assignVisible = ref(false)
const assigning = ref(false)
const assigneeId = ref<number | null>(null)
let currentAssignRow: RepairRow | null = null

function deviceName(id: number) {
  return devices.value.find((d) => d.id === id)?.name || `#${id}`
}
function roomName(id: number) {
  return rooms.value.find((r) => r.id === id)?.name || (id ? `#${id}` : '—')
}
function userName(id: number) {
  return id ? userMap.value[id] || `#${id}` : '—'
}
function fmtTime(t?: string) {
  return t ? t.replace('T', ' ') : '—'
}
function statusText(s: string) {
  const map: Record<string, string> = {
    PENDING: '待处理',
    ASSIGNED: '已派单',
    COMPLETED: '已完成',
    REJECTED: '已驳回',
    VERIFIED: '已验收'
  }
  return map[s] || s
}
function statusTag(s: string) {
  const map: Record<string, 'warning' | 'primary' | 'success' | 'danger' | 'info'> = {
    PENDING: 'warning',
    ASSIGNED: 'primary',
    COMPLETED: 'success',
    REJECTED: 'danger',
    VERIFIED: 'info'
  }
  return map[s] || 'info'
}

async function load() {
  loading.value = true
  try {
    const [rs, ds, rms, users] = await Promise.all([
      listRepairs(),
      listDevices(),
      listRooms(),
      listUsers()
    ])
    repairs.value = rs as RepairRow[]
    devices.value = ds as Device[]
    rooms.value = (rms as { id: number; name: string }[]).map((r) => ({ id: r.id, name: r.name }))
    const userList = users as UserItem[]
    userMap.value = Object.fromEntries(userList.map((u) => [u.id, u.realName || u.username]))
    labAdmins.value = userList.filter((u) => u.role === 'LAB_ADMIN')
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const device = devices.value.find((d) => d.id === form.deviceId)
    await createRepair({
      deviceId: form.deviceId,
      roomId: device?.roomId,
      description: form.description
    })
    ElMessage.success('报修提交成功')
    formRef.value?.resetFields()
    await load()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    submitting.value = false
  }
}

function openAssign(row: RepairRow) {
  currentAssignRow = row
  assigneeId.value = null
  assignVisible.value = true
}

async function handleAssign() {
  if (!currentAssignRow || !assigneeId.value) {
    ElMessage.warning('请选择维修员')
    return
  }
  assigning.value = true
  try {
    await assignRepair(currentAssignRow.id, assigneeId.value)
    ElMessage.success('派单成功')
    assignVisible.value = false
    await load()
  } finally {
    assigning.value = false
  }
}

async function handleReject(row: RepairRow) {
  let value = ''
  try {
    const res = await ElMessageBox.prompt('请输入驳回原因', '驳回报修', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '驳回原因',
      inputValidator: (v: string) => (v && v.trim() ? true : '请输入驳回原因')
    })
    value = res.value
  } catch {
    return
  }
  await rejectRepair(row.id, value)
  ElMessage.success('已驳回')
  await load()
}

async function handleFinish(row: RepairRow) {
  let value = ''
  try {
    const res = await ElMessageBox.prompt('请输入维修结果', '完工', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '维修结果',
      inputValidator: (v: string) => (v && v.trim() ? true : '请输入维修结果')
    })
    value = res.value
  } catch {
    return
  }
  await finishRepair(row.id, value) // 后端 @RequestBody 接收 result
  ElMessage.success('已完工')
  await load()
}

async function handleVerify(row: RepairRow) {
  try {
    await ElMessageBox.confirm('确认验收该工单？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await verifyRepair(row.id)
  ElMessage.success('已验收')
  await load()
}

onMounted(load)
</script>

<style scoped>
.mb-12 {
  margin-bottom: 12px;
}
</style>
