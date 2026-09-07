<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-num">{{ overview.totalApproved }}</div>
          <div class="stat-label">预约审批通过（次）</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-num">{{ overview.totalUsed }}</div>
          <div class="stat-label">实训室使用（次）</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-num">{{ overview.repairCompleted }}</div>
          <div class="stat-label">报修完成（件）</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="chart-card">
      <template #header>
        <div class="card-header">
          <span>实训室使用统计（2026-09-01 至 2026-09-30）</span>
          <el-button type="primary" size="small" @click="loadUsage">刷新</el-button>
        </div>
      </template>
      <div ref="chartRef" v-loading="usageLoading" class="chart"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { getOverview, getRoomUsage } from '../api/stats'

interface RoomUsage {
  statDate: string
  roomId: number
  usageCount: number
  approvedCount: number
}

const overview = reactive({ totalApproved: 0, totalUsed: 0, repairCompleted: 0 })
const usageLoading = ref(false)
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

async function loadOverview() {
  const data = (await getOverview()) as { totalApproved: number; totalUsed: number; repairCompleted: number }
  overview.totalApproved = data.totalApproved ?? 0
  overview.totalUsed = data.totalUsed ?? 0
  overview.repairCompleted = data.repairCompleted ?? 0
}

async function loadUsage() {
  usageLoading.value = true
  try {
    const rows = (await getRoomUsage({ from: '2026-09-01', to: '2026-09-30' })) as RoomUsage[]
    chart?.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['使用次数', '通过次数'] },
      grid: { left: 40, right: 20, top: 40, bottom: 30 },
      xAxis: {
        type: 'category',
        data: rows.map((r) => r.statDate)
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '使用次数',
          type: 'bar',
          barMaxWidth: 28,
          data: rows.map((r) => r.usageCount)
        },
        {
          name: '通过次数',
          type: 'bar',
          barMaxWidth: 28,
          data: rows.map((r) => r.approvedCount)
        }
      ]
    })
  } finally {
    usageLoading.value = false
  }
}

onMounted(() => {
  loadOverview()
  chart = echarts.init(chartRef.value!)
  loadUsage()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

function handleResize() {
  chart?.resize()
}
</script>

<style scoped>
.stat-card {
  text-align: center;
}
.stat-num {
  font-size: 32px;
  font-weight: 700;
  color: #409eff;
}
.stat-label {
  margin-top: 8px;
  font-size: 14px;
  color: #909399;
}
.chart-card {
  margin-top: 16px;
}
.chart {
  height: 420px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
