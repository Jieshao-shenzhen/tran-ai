<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <el-select
            v-model="filterRoomId"
            placeholder="按实训室筛选"
            clearable
            style="width: 240px"
            @change="load"
          >
            <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
          <div class="flex-1"></div>
          <el-button v-if="isAdmin" type="primary" @click="openDialog()">新增设备</el-button>
        </div>
      </template>
      <el-table :data="devices" v-loading="loading" border stripe>
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="category" label="类别" width="100" />
        <el-table-column label="所属实训室" min-width="130">
          <template #default="{ row }">{{ roomName(row.roomId) }}</template>
        </el-table-column>
        <el-table-column prop="brand" label="品牌" width="110" />
        <el-table-column prop="modelNo" label="型号" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '在用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="buyDate" label="购置日期" width="110" />
        <el-table-column prop="price" label="价格" width="100" />
        <el-table-column v-if="isAdmin" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑设备' : '新增设备'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类别"><el-input v-model="form.category" /></el-form-item>
        <el-form-item label="所属实训室">
          <el-select v-model="form.roomId" style="width: 100%">
            <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌"><el-input v-model="form.brand" /></el-form-item>
        <el-form-item label="型号"><el-input v-model="form.modelNo" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :value="1" label="在用" />
            <el-option :value="0" label="停用" />
          </el-select>
        </el-form-item>
        <el-form-item label="购置日期">
          <el-date-picker
            v-model="form.buyDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import { listRooms, listDevices, saveDevice } from '../api/resource'

interface Room {
  id: number
  name: string
}
interface Device {
  id?: number
  code: string
  name: string
  category: string
  roomId: number
  brand: string
  modelNo: string
  status: number
  buyDate: string
  price: number
}

const store = useUserStore()
const isAdmin = computed(() => ['LAB_ADMIN', 'SYSTEM_ADMIN'].includes(store.role))

const loading = ref(false)
const saving = ref(false)
const rooms = ref<Room[]>([])
const devices = ref<Device[]>([])
const filterRoomId = ref<number | null>(null)
const dialogVisible = ref(false)

const emptyForm = () => ({
  id: undefined as number | undefined,
  code: '',
  name: '',
  category: '',
  roomId: null as number | null,
  brand: '',
  modelNo: '',
  status: 1,
  buyDate: '',
  price: 0
})
const form = reactive<ReturnType<typeof emptyForm>>(emptyForm())

function roomName(id: number) {
  return rooms.value.find((r) => r.id === id)?.name || (id ? `#${id}` : '—')
}

async function load() {
  loading.value = true
  try {
    devices.value = (await listDevices(
      filterRoomId.value ? { roomId: filterRoomId.value } : undefined
    )) as Device[]
  } finally {
    loading.value = false
  }
}

async function loadRooms() {
  rooms.value = (await listRooms()) as Room[]
}

function openDialog(row?: Device) {
  Object.assign(form, emptyForm(), row || {})
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.code || !form.name || !form.roomId) {
    ElMessage.warning('请填写编码、名称并选择实训室')
    return
  }
  saving.value = true
  try {
    await saveDevice({ ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadRooms()
  await load()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.flex-1 {
  flex: 1;
}
</style>
