<template>
  <div class="tasks-page">
    <div class="page-header">
      <h2 class="page-title">扫描任务</h2>
      <div class="header-right">
        <el-tag v-if="isPolling" type="warning" size="small">
          <el-icon class="pulse-icon"><Loading /></el-icon> 自动刷新中
        </el-tag>
        <el-button @click="refreshTasks" :loading="taskStore.loading">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <el-card>
      <el-table :data="taskStore.tasks" stripe v-loading="taskStore.loading">
        <el-table-column prop="id" label="任务ID" width="80" />
        <el-table-column prop="projectId" label="项目ID" width="80" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalDependencies" label="依赖总数" width="100" />
        <el-table-column prop="vulnerableDependencies" label="漏洞数" width="100" />
        <el-table-column label="进度" width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress || 0"
              :status="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'exception' : undefined"
            />
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="180" />
        <el-table-column prop="completedAt" label="完成时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              @click="$router.push(`/tasks/${row.id}`)"
            >
              详情
            </el-button>
            <el-button
              size="small"
              type="warning"
              :disabled="row.status !== 'RUNNING' && row.status !== 'PENDING'"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="taskStore.total > 0">
        <el-pagination
          v-model:current-page="taskStore.currentPage"
          v-model:page-size="taskStore.pageSize"
          :total="taskStore.total"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
          background
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useTaskStore } from '@/stores/task'
import { useStatus } from '@/composables/useStatus'
import { useTaskPolling } from '@/composables/useTaskPolling'

const taskStore = useTaskStore()
const { statusType, statusText, isActive } = useStatus()

const refreshTasks = () => taskStore.fetchTasks()

const hasActiveTasks = () => taskStore.tasks.some(t => isActive(t.status))

const { startPolling, isPolling } = useTaskPolling(refreshTasks, hasActiveTasks)

const handlePageChange = (page) => {
  taskStore.fetchTasks({ page, pageSize: taskStore.pageSize })
}

const handleSizeChange = (size) => {
  taskStore.fetchTasks({ page: 1, pageSize: size })
}

const handleCancel = async (task) => {
  try {
    await taskStore.cancelTask(task.id)
    ElMessage.success('任务已取消')
  } catch (e) {
    ElMessage.error('取消失败: ' + e.message)
  }
}

onMounted(async () => {
  await taskStore.fetchTasks()
  // 如果有活跃任务，启动自动轮询
  if (hasActiveTasks()) {
    startPolling()
  }
})
</script>

<style scoped>
.tasks-page {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.pulse-icon {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
