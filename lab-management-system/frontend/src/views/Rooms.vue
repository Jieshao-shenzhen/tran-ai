<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <el-input
            v-model="keyword"
            placeholder="搜索名称/编码"
            clearable
            style="width: 260px"
            @keyup.enter="load"
          />
          <el-button type="primary" @click="load">查询</el-button>
          <div class="flex-1"></div>
          <el-button v-if="isAdmin" type="primary" @click="openDialog()">新增实训室</el-button>
        </div>
      </template>
      <el-table :data="rooms" v-loading="loading" border stripe>
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="building" label="楼栋" width="110" />
        <el-table-column prop="floor" label="楼层" width="70" />
        <el-table-column prop="capacity" label="容量" width="70" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="isAdmin" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑实训室' : '新增实训室'" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="楼栋"><el-input v-model="form.building" /></el-form-item>
        <el-form-item label="楼层"><el-input-number v-model="form.floor" :min="0" /></el-form-item>
        <el-form-item label="容量"><el-input-number v-model="form.capacity" :min="0" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.type" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="停用" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
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
import { listRooms, saveRoom } from '../api/resource'

interface Room {
  id?: number
  code: string
  name: string
  building: string
  floor: number
  capacity: number
  type: string
  status: number
  remark: string
}

const store = useUserStore()
const isAdmin = computed(() => ['LAB_ADMIN', 'SYSTEM_ADMIN'].includes(store.role))

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rooms = ref<Room[]>([])
const dialogVisible = ref(false)

const emptyForm = () => ({
  id: undefined as number | undefined,
  code: '',
  name: '',
  building: '',
  floor: 1,
  capacity: 40,
  type: '',
  status: 1,
  remark: ''
})
const form = reactive<ReturnType<typeof emptyForm>>(emptyForm())

async function load() {
  loading.value = true
  try {
    rooms.value = (await listRooms(keyword.value ? { keyword: keyword.value } : undefined)) as Room[]
  } finally {
    loading.value = false
  }
}

function openDialog(row?: Room) {
  Object.assign(form, emptyForm(), row || {})
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.code || !form.name) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  saving.value = true
  try {
    await saveRoom({ ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
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
.flex-1 {
  flex: 1;
}
</style>
