<template>
  <div class="project-detail">
    <div class="page-header">
      <el-button text @click="$router.back()">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h2 class="page-title">项目详情</h2>
    </div>

    <el-card v-if="projectStore.currentProject" class="detail-card">
      <template #header>
        <div class="card-header">
          <span>{{ projectStore.currentProject.name }}</span>
          <el-tag>{{ projectStore.currentProject.status }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="项目ID">
          {{ projectStore.currentProject.id }}
        </el-descriptions-item>
        <el-descriptions-item label="文件类型">
          {{ projectStore.currentProject.fileType }}
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">
          {{ projectStore.currentProject.description || '无' }}
        </el-descriptions-item>
        <el-descriptions-item label="文件路径" :span="2">
          {{ projectStore.currentProject.filePath }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ projectStore.currentProject.createdAt }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ projectStore.currentProject.updatedAt }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 关联的扫描任务 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>扫描历史</span>
          <el-button type="primary" size="small" @click="startScan">
            开始扫描
          </el-button>
        </div>
      </template>
      <el-table :data="projectTasks" stripe v-loading="taskStore.loading">
        <el-table-column prop="id" label="任务ID" width="80" />
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
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150">
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
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useProjectStore } from '@/stores/project'
import { useTaskStore } from '@/stores/task'

const route = useRoute()
const projectStore = useProjectStore()
const taskStore = useTaskStore()

const projectTasks = computed(() =>
  taskStore.tasks.filter(t => t.projectId === Number(route.params.id))
)

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

const startScan = async () => {
  try {
    await taskStore.createTask(route.params.id)
    ElMessage.success('扫描任务已创建')
  } catch (e) {
    ElMessage.error('创建扫描任务失败: ' + e.message)
  }
}

onMounted(async () => {
  await Promise.all([
    projectStore.fetchProject(route.params.id),
    taskStore.fetchTasks(),
  ])
})
</script>

<style scoped>
.project-detail {
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
</style>
