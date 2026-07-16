<template>
  <div class="reports-page">
    <div class="page-header">
      <h2 class="page-title">报告中心</h2>
    </div>

    <el-card>
      <template #header>
        <span>已完成扫描任务</span>
      </template>
      <el-table :data="pagedCompletedTasks" stripe v-loading="taskStore.loading">
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

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="completedTasks.length > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="completedTasks.length"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next"
          background
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useTaskStore } from '@/stores/task'
import { reportApi } from '@/api'

const taskStore = useTaskStore()

const currentPage = ref(1)
const pageSize = ref(10)

const completedTasks = computed(() =>
  taskStore.tasks.filter(t => t.status === 'COMPLETED')
)

const pagedCompletedTasks = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return completedTasks.value.slice(start, start + pageSize.value)
})

const viewReport = (taskId, format) => {
  const url = reportApi.getReportUrl(taskId, format)
  window.open(url, '_blank')
}

onMounted(() => {
  // 获取较多数据用于报告列表（completed tasks 在客户端分页）
  taskStore.fetchTasks({ page: 1, pageSize: 100 })
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
