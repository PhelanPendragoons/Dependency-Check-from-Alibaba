<template>
  <div class="dashboard">
    <h2 class="page-title">仪表盘</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">项目总数</div>
              <div class="stat-value">{{ stats.totalProjects }}</div>
            </div>
            <el-icon class="stat-icon" color="#409eff" :size="48"><Folder /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">扫描任务</div>
              <div class="stat-value">{{ stats.totalTasks }}</div>
            </div>
            <el-icon class="stat-icon" color="#67c23a" :size="48"><List /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">漏洞总数</div>
              <div class="stat-value" style="color: #f56c6c">{{ stats.totalVulnerabilities }}</div>
            </div>
            <el-icon class="stat-icon" color="#f56c6c" :size="48"><Warning /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">高危漏洞</div>
              <div class="stat-value" style="color: #e6a23c">{{ stats.highVulnerabilities }}</div>
            </div>
            <el-icon class="stat-icon" color="#e6a23c" :size="48"><CircleClose /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近扫描任务 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>最近扫描任务</span>
          <el-button text type="primary" @click="$router.push('/tasks')">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recentTasks" stripe style="width: 100%" v-loading="taskStore.loading">
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
        <el-table-column label="进度" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress || 0"
              :status="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'exception' : undefined"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useTaskStore } from '@/stores/task'
import { useProjectStore } from '@/stores/project'

const taskStore = useTaskStore()
const projectStore = useProjectStore()

const stats = ref({
  totalProjects: 0,
  totalTasks: 0,
  totalVulnerabilities: 0,
  highVulnerabilities: 0,
})

const recentTasks = computed(() => taskStore.tasks.slice(0, 10))

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

onMounted(async () => {
  await Promise.all([
    taskStore.fetchTasks(),
    projectStore.fetchProjects(),
  ])
  // 计算统计数据
  const tasks = taskStore.tasks
  stats.value.totalProjects = projectStore.projects.length
  stats.value.totalTasks = tasks.length
  stats.value.totalVulnerabilities = tasks.reduce(
    (sum, t) => sum + (t.vulnerableDependencies || 0), 0
  )
  stats.value.highVulnerabilities = stats.value.totalVulnerabilities // 简化处理
})
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
  margin: 0 auto;
}

.page-title {
  margin-bottom: 20px;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.stat-cards {
  margin-bottom: 20px;
}

.stat-card {
  cursor: pointer;
}

.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.section-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
