<template>
  <div class="projects-page">
    <div class="page-header">
      <h2 class="page-title">项目管理</h2>
      <el-button type="primary" @click="showUploadDialog = true">
        <el-icon><Upload /></el-icon>上传项目
      </el-button>
    </div>

    <!-- 项目列表 -->
    <el-card>
      <el-table :data="projectStore.projects" stripe v-loading="projectStore.loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="项目名称" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/projects/${row.id}`)">
              {{ row.name }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column prop="fileType" label="文件类型" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'UPLOADED' ? 'success' : 'info'" size="small">
              {{ row.status === 'UPLOADED' ? '已上传' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="startScan(row)">
              开始扫描
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="projectStore.total > 0">
        <el-pagination
          v-model:current-page="projectStore.currentPage"
          v-model:page-size="projectStore.pageSize"
          :total="projectStore.total"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
          background
        />
      </div>
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传项目" width="500px">
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="项目名称" required>
          <el-input v-model="uploadForm.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入项目描述（可选）"
          />
        </el-form-item>
        <el-form-item label="上传文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".zip"
            :on-change="handleFileChange"
          >
            <el-button type="primary">选择 ZIP 文件</el-button>
            <template #tip>
              <div class="el-upload__tip">仅支持 .zip 格式</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">
          上传并创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useProjectStore } from '@/stores/project'
import { useTaskStore } from '@/stores/task'

const projectStore = useProjectStore()
const taskStore = useTaskStore()

const showUploadDialog = ref(false)
const uploading = ref(false)
const uploadForm = ref({ name: '', description: '' })
const selectedFile = ref(null)

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const handlePageChange = (page) => {
  projectStore.fetchProjects(page, projectStore.pageSize)
}

const handleSizeChange = (size) => {
  projectStore.fetchProjects(1, size)
}

const handleUpload = async () => {
  if (!uploadForm.value.name) {
    ElMessage.warning('请输入项目名称')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请选择 ZIP 文件')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('name', uploadForm.value.name)
    formData.append('description', uploadForm.value.description)

    await projectStore.createProject(formData)
    ElMessage.success('项目创建成功')
    showUploadDialog.value = false
    uploadForm.value = { name: '', description: '' }
    selectedFile.value = null
  } catch (e) {
    ElMessage.error('项目创建失败: ' + e.message)
  } finally {
    uploading.value = false
  }
}

const startScan = async (project) => {
  try {
    await taskStore.createTask(project.id)
    ElMessage.success('扫描任务已创建')
  } catch (e) {
    ElMessage.error('创建扫描任务失败: ' + e.message)
  }
}

const handleDelete = (project) => {
  ElMessageBox.confirm(`确定要删除项目「${project.name}」吗？`, '确认删除', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  }).then(async () => {
    await projectStore.deleteProject(project.id)
    ElMessage.success('项目已删除')
  }).catch(() => {})
}

onMounted(() => {
  projectStore.fetchProjects()
})
</script>

<style scoped>
.projects-page {
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
