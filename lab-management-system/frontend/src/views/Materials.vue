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
        </div>
      </template>
      <el-table :data="materials" v-loading="loading" border stripe :row-class-name="rowClassName">
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="stock" label="当前库存" width="100" sortable />
        <el-table-column prop="warnThreshold" label="预警阈值" width="100" />
        <el-table-column label="库存状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.stock <= row.warnThreshold ? 'danger' : 'success'" size="small">
              {{ row.stock <= row.warnThreshold ? '库存告急' : '库存充足' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isAdmin" label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" plain @click="openStock(row, 'IN')">入库</el-button>
            <el-button size="small" type="warning" plain @click="openStock(row, 'OUT')">出库</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="stockTitle" width="420px">
      <el-form label-width="80px">
        <el-form-item label="耗材">
          <span>{{ current?.name }}（当前库存：{{ current?.stock }}）</span>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="stockType" style="width: 100%">
            <el-option value="IN" label="入库" />
            <el-option value="OUT" label="出库" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="stockQuantity" :min="1" :max="100000" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="stocking" @click="handleStock">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import { listMaterials, stockMaterial } from '../api/resource'

interface Material {
  id: number
  code: string
  name: string
  spec: string
  unit: string
  stock: number
  warnThreshold: number
}

const store = useUserStore()
const isAdmin = computed(() => ['LAB_ADMIN', 'SYSTEM_ADMIN'].includes(store.role))

const loading = ref(false)
const stocking = ref(false)
const keyword = ref('')
const materials = ref<Material[]>([])
const dialogVisible = ref(false)
const current = ref<Material | null>(null)
const stockType = ref<'IN' | 'OUT'>('IN')
const stockQuantity = ref(1)

const stockTitle = computed(() =>
  current.value ? `${stockType.value === 'IN' ? '入库' : '出库'} - ${current.value.name}` : ''
)

function rowClassName({ row }: { row: Material }) {
  return row.stock <= row.warnThreshold ? 'warn-row' : ''
}

async function load() {
  loading.value = true
  try {
    materials.value = (await listMaterials(
      keyword.value ? { keyword: keyword.value } : undefined
    )) as Material[]
  } finally {
    loading.value = false
  }
}

function openStock(row: Material, type: 'IN' | 'OUT') {
  current.value = row
  stockType.value = type
  stockQuantity.value = 1
  dialogVisible.value = true
}

async function handleStock() {
  if (!current.value) return
  stocking.value = true
  try {
    await stockMaterial(current.value.id, stockType.value, stockQuantity.value)
    ElMessage.success('操作成功')
    dialogVisible.value = false
    await load()
  } finally {
    stocking.value = false
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
:deep(.warn-row) {
  --el-table-tr-bg-color: #fef0f0;
}
</style>
