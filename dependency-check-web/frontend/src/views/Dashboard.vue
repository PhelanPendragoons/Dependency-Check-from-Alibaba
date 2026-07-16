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
              <div class="stat-label">高危 / 严重</div>
              <div class="stat-value" style="color: #e6a23c">{{ stats.criticalCount + stats.highCount }}</div>
            </div>
            <el-icon class="stat-icon" color="#e6a23c" :size="48"><CircleClose /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 漏洞等级分布 -->
    <el-row :gutter="20" class="stat-cards" v-if="stats.totalVulnerabilities > 0">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card severity-card critical">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">严重 CRITICAL</div>
              <div class="stat-value" style="color: #8b0000">{{ stats.criticalCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card severity-card high">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">高危 HIGH</div>
              <div class="stat-value" style="color: #f56c6c">{{ stats.highCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card severity-card medium">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">中危 MEDIUM</div>
              <div class="stat-value" style="color: #e6a23c">{{ stats.mediumCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card severity-card low">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">低危 LOW</div>
              <div class="stat-value" style="color: #67c23a">{{ stats.lowCount }}</div>
            </div>
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
import { useStatus } from '@/composables/useStatus'
import { dashboardApi } from '@/api'

const taskStore = useTaskStore()
const projectStore = useProjectStore()
const { statusType, statusText } = useStatus()

const stats = ref({
  totalProjects: 0,
  totalTasks: 0,
  completedTasks: 0,
  totalVulnerabilities: 0,
  criticalCount: 0,
  highCount: 0,
  mediumCount: 0,
  lowCount: 0,
  licenseIssues: 0,
})

const recentTasks = computed(() => taskStore.tasks.slice(0, 10))

onMounted(async () => {
  try {
    // 获取仪表盘聚合统计数据（按severity分级）
    const res = await dashboardApi.getStats()
    if (res.data) {
      stats.value = res.data
    }
  } catch (e) {
    console.error('获取仪表盘统计失败:', e)
  }

  // 获取最近任务列表
  await Promise.all([
    taskStore.fetchTasks(),
    projectStore.fetchProjects(),
  ])
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

.stat-card.severity-card.critical {
  border-left: 4px solid #8b0000;
}

.stat-card.severity-card.high {
  border-left: 4px solid #f56c6c;
}

.stat-card.severity-card.medium {
  border-left: 4px solid #e6a23c;
}

.stat-card.severity-card.low {
  border-left: 4px solid #67c23a;
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
