<template>
  <div class="task-detail">
    <div class="page-header">
      <el-button text @click="$router.back()">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h2 class="page-title">任务详情</h2>
    </div>

    <el-card v-if="taskStore.currentTask" class="detail-card">
      <template #header>
        <div class="card-header">
          <span>任务 #{{ taskStore.currentTask.id }}</span>
          <div class="header-actions">
            <el-button
              size="small"
              type="warning"
              :disabled="taskStore.currentTask.status !== 'RUNNING' && taskStore.currentTask.status !== 'PENDING'"
              @click="handleCancel"
            >
              取消任务
            </el-button>
          </div>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="项目ID">
          {{ taskStore.currentTask.projectId }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(taskStore.currentTask.status)">
            {{ statusText(taskStore.currentTask.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总依赖数">
          {{ taskStore.currentTask.totalDependencies || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="漏洞依赖数">
          {{ taskStore.currentTask.vulnerableDependencies || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="进度" :span="2">
          <el-progress
            :percentage="taskStore.currentTask.progress || 0"
            :status="taskStore.currentTask.status === 'COMPLETED' ? 'success' : taskStore.currentTask.status === 'FAILED' ? 'exception' : undefined"
          />
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ taskStore.currentTask.startedAt || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="完成时间">
          {{ taskStore.currentTask.completedAt || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2" v-if="taskStore.currentTask.errorMessage">
          <el-alert :title="taskStore.currentTask.errorMessage" type="error" show-icon />
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 报告下载 -->
    <el-card class="section-card" v-if="taskStore.currentTask?.status === 'COMPLETED'">
      <template #header>
        <span>报告下载</span>
      </template>
      <div class="report-actions">
        <el-button type="primary" @click="downloadReport('html')">
          <el-icon><View /></el-icon>查看 HTML 报告
        </el-button>
        <el-button type="success" @click="downloadReport('excel')">
          <el-icon><Download /></el-icon>下载 Excel 报告
        </el-button>
        <el-button type="warning" @click="downloadReport('pdf')">
          <el-icon><Download /></el-icon>下载 PDF 报告
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useTaskStore } from '@/stores/task'
import { reportApi } from '@/api'

const route = useRoute()
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

const downloadReport = (format) => {
  const url = reportApi.getReportUrl(route.params.id, format)
  window.open(url, '_blank')
}

const handleCancel = async () => {
  try {
    await taskStore.cancelTask(route.params.id)
    ElMessage.success('任务已取消')
  } catch (e) {
    ElMessage.error('取消失败: ' + e.message)
  }
}

onMounted(() => {
  taskStore.fetchTask(route.params.id)
})
</script>

<style scoped>
.task-detail {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.detail-card {
  margin-bottom: 20px;
}

.section-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.report-actions {
  display: flex;
  gap: 12px;
}
</style>
