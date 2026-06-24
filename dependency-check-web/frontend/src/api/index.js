import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 如果后端返回了 Result 包装格式
    if (res.code !== undefined && res.code !== 200) {
      console.error('API Error:', res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    console.error('Network Error:', error.message)
    return Promise.reject(error)
  }
)

// ==================== 项目管理 API ====================

export const projectApi = {
  /** 获取项目列表 */
  list() {
    return request.get('/projects')
  },
  /** 获取项目详情 */
  get(id) {
    return request.get(`/projects/${id}`)
  },
  /** 创建项目（上传 ZIP） */
  create(data) {
    return request.post('/projects', data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  /** 删除项目 */
  delete(id) {
    return request.delete(`/projects/${id}`)
  },
}

// ==================== 扫描任务 API ====================

export const taskApi = {
  /** 获取任务列表 */
  list(params) {
    return request.get('/tasks', { params })
  },
  /** 获取任务详情 */
  get(id) {
    return request.get(`/tasks/${id}`)
  },
  /** 创建扫描任务 */
  create(projectId) {
    return request.post('/tasks', null, { params: { projectId } })
  },
  /** 取消扫描任务 */
  cancel(id) {
    return request.post(`/tasks/${id}/cancel`)
  },
}

// ==================== 报告 API ====================

export const reportApi = {
  /** 查看/下载报告 */
  getReportUrl(taskId, format = 'html') {
    return `/api/reports/${taskId}?format=${format}`
  },
  /** 检查报告是否存在 */
  exists(taskId) {
    return request.get(`/reports/${taskId}/exists`)
  },
  /** 获取可用格式列表 */
  getFormats(taskId) {
    return request.get(`/reports/${taskId}/formats`)
  },
}

export default request
