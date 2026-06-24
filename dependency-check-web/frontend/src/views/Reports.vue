<template>
  <div class="reports-page">
    <div class="page-header">
      <h2 class="page-title">报告中心</h2>
    </div>

    <el-card>
      <template #header>
        <span>已完成扫描任务</span>
      </template>
      <el-table :data="completedTasks" stripe v-loading="taskStore.loading">
        <el-table-column prop="id" label="任务ID" width="80" />
        <el-table-column prop="projectId" label="项目ID" width="80" />
        <el-table-column prop="totalDependencies" label="依赖总数" width="100" />
        <el-table-column prop="vulnerableDependencies" label="漏洞数" width="100" />
        <el-table-column prop="completedAt" label="完成时间" width="180" />
        <el-table-column label="报告格式" width="200">
          <template #default="{ row }">
            <el-button text type="primary" @click="viewReport(row.id, 'html')">
              HTML
            </el-button>
            <el-button text type="success" @click="viewReport(row.id, 'excel')">
              Excel
            </el-button>
            <el-button text type="warning" @click="viewReport(row.id, 'pdf')">
              PDF
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="$router.push(`/tasks/${row.id}`)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useTaskStore } from '@/stores/task'
import { reportApi } from '@/api'

const taskStore = useTaskStore()

const completedTasks = computed(() =>
  taskStore.tasks.filter(t => t.status === 'COMPLETED')
)

const viewReport = (taskId, format) => {
  const url = reportApi.getReportUrl(taskId, format)
  window.open(url, '_blank')
}

onMounted(() => {
  taskStore.fetchTasks()
})
</script>

<style scoped>
.reports-page {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
</style>
