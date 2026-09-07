<template>
  <div>
    <!-- 非管理员：新建预约表单 -->
    <el-card v-if="!isAdmin" class="mb-12">
      <template #header><span>新建预约</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" style="max-width: 560px">
        <el-form-item label="实训室" prop="roomId">
          <el-select v-model="form.roomId" placeholder="请选择实训室" style="width: 100%">
            <el-option v-for="r in rooms" :key="r.id" :label="`${r.name}（${r.code}）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间段" prop="timeRange">
          <!-- 后端 LocalDateTime 仅接受 ISO 格式，T 分隔 -->
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="人数" prop="peopleNum">
          <el-input-number v-model="form.peopleNum" :min="1" :max="500" />
        </el-form-item>
        <el-form-item label="用途" prop="purpose">
          <el-input v-model="form.purpose" placeholder="请填写预约用途" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleCreate">提交预约</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header><span>预约列表</span></template>
      <el-tabs v-if="isAdmin" v-model="activeTab" class="mb-12">
        <el-tab-pane label="我的预约" name="mine" />
        <el-tab-pane label="审批中心" name="approval" />
      </el-tabs>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="实训室" min-width="130">
          <template #default="{ row }">{{ roomName(row.roomId) }}</template>
        </el-table-column>
        <el-table-column label="申请人" width="110">
          <template #default="{ row }">{{ userName(row.applicantId) }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="peopleNum" label="人数" width="70" />
        <el-table-column prop="purpose" label="用途" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canCancel(row)"
              size="small"
              type="danger"
              plain
              @click="handleCancel(row)"
            >取消</el-button>
            <template v-if="isAdmin && activeTab === 'approval'">
              <el-button size="small" type="success" @click="handleApprove(row)">通过</el-button>
              <el-button size="small" type="danger" plain @click="handleReject(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '../store/user'
import { listRooms } from '../api/resource'
import { listUsers } from '../api/user'
import {
  listReservations,
  createReservation,
  approveReservation,
  rejectReservation,
  cancelReservation
} from '../api/business'

interface Room {
  id: number
  code: string
  name: string
}
interface UserItem {
  id: number
  username: string
  realName: string
  role: string
}
interface ReservationRow {
  id: number
  roomId: number
  applicantId: number
  purpose: string
  startTime: string
  endTime: string
  peopleNum: number
  status: string
}

const store = useUserStore()
const isAdmin = computed(() => ['LAB_ADMIN', 'SYSTEM_ADMIN'].includes(store.role))

const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)
const rooms = ref<Room[]>([])
const userMap = ref<Record<number, string>>({})
const myReservations = ref<ReservationRow[]>([])
const pendingReservations = ref<ReservationRow[]>([])
const activeTab = ref('mine')

const form = reactive({
  roomId: null as number | null,
  timeRange: null as [string, string] | null,
  peopleNum: 1,
  purpose: ''
})
const rules: FormRules = {
  roomId: [{ required: true, message: '请选择实训室', trigger: 'change' }],
  timeRange: [{ required: true, message: '请选择时间段', trigger: 'change' }],
  peopleNum: [{ required: true, message: '请填写人数', trigger: 'blur' }],
  purpose: [{ required: true, message: '请填写用途', trigger: 'blur' }]
}

const rows = computed(() => {
  if (!isAdmin.value || activeTab.value === 'mine') return myReservations.value
  return pendingReservations.value
})

function roomName(id: number) {
  return rooms.value.find((r) => r.id === id)?.name || `#${id}`
}
function userName(id: number) {
  return userMap.value[id] || `#${id}`
}
function fmtTime(t?: string) {
  return t ? t.replace('T', ' ') : '—'
}
function statusText(s: string) {
  const map: Record<string, string> = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
    COMPLETED: '已完成'
  }
  return map[s] || s
}
function statusTag(s: string) {
  const map: Record<string, 'warning' | 'success' | 'danger' | 'info' | 'primary'> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info',
    COMPLETED: 'primary'
  }
  return map[s] || 'info'
}
function canCancel(row: ReservationRow) {
  return !isAdmin.value && row.status === 'PENDING'
}

async function loadRooms() {
  rooms.value = (await listRooms()) as Room[]
}
async function loadUsers() {
  const list = (await listUsers()) as UserItem[]
  userMap.value = Object.fromEntries(list.map((u) => [u.id, u.realName || u.username]))
}
async function loadMine() {
  myReservations.value = (await listReservations({ applicantId: store.userId })) as ReservationRow[]
}
async function loadPending() {
  pendingReservations.value = (await listReservations({ status: 'PENDING' })) as ReservationRow[]
}
async function loadAll() {
  loading.value = true
  try {
    await Promise.all([
      loadMine(),
      isAdmin.value ? loadPending() : Promise.resolve(),
      loadRooms(),
      loadUsers()
    ])
  } finally {
    loading.value = false
  }
}

watch(activeTab, () => {
  if (activeTab.value === 'approval' && !pendingReservations.value.length) loadPending()
})

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.timeRange) return
  submitting.value = true
  try {
    await createReservation({
      roomId: form.roomId,
      purpose: form.purpose,
      startTime: form.timeRange[0],
      endTime: form.timeRange[1],
      peopleNum: form.peopleNum
    })
    ElMessage.success('预约提交成功')
    formRef.value?.resetFields()
    await loadMine()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleCancel(row: ReservationRow) {
  try {
    await ElMessageBox.confirm('确定取消该预约吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await cancelReservation(row.id)
  ElMessage.success('已取消')
  await loadMine()
}

async function handleApprove(row: ReservationRow) {
  await approveReservation(row.id)
  ElMessage.success('已通过')
  await loadAll()
}

async function handleReject(row: ReservationRow) {
  let value = ''
  try {
    const res = await ElMessageBox.prompt('请输入驳回原因', '驳回预约', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '驳回原因',
      inputValidator: (v: string) => (v && v.trim() ? true : '请输入驳回原因')
    })
    value = res.value
  } catch {
    return
  }
  await rejectReservation(row.id, value)
  ElMessage.success('已驳回')
  await loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.mb-12 {
  margin-bottom: 12px;
}
</style>
