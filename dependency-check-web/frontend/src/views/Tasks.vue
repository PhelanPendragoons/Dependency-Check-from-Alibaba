<template>
  <div class="tasks-page">
    <div class="page-header">
      <h2 class="page-title">扫描任务</h2>
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
    </el-card>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useTaskStore } from '@/stores/task'

const taskStore = useTaskStore()

const statusType = (status) => {
  const map = {
    COMPLETED: 'success',
    RUNNING: 'warning',
    PENDING: 'info',
    FAILED: 'danger',
    CANCELLED: 'info',
  }
  return map[status] || 'info'
}

const statusText = (status) => {
  const map = {
    COMPLETED: '已完成',
    RUNNING: '扫描中',
    PENDING: '等待中',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return map[status] || status
}

const handleCancel = async (task) => {
  try {
    await taskStore.cancelTask(task.id)
    ElMessage.success('任务已取消')
  } catch (e) {
    ElMessage.error('取消失败: ' + e.message)
  }
}

onMounted(() => {
  taskStore.fetchTasks()
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

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
</style>
