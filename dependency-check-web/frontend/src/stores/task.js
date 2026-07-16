import { defineStore } from 'pinia'
import { taskApi } from '@/api'

export const useTaskStore = defineStore('task', {
  state: () => ({
    tasks: [],
    currentTask: null,
    loading: false,
    total: 0,
    currentPage: 1,
    pageSize: 10,
  }),
  actions: {
    async fetchTasks(params = {}) {
      this.loading = true
      try {
        const merged = { page: this.currentPage, pageSize: this.pageSize, ...params }
        const res = await taskApi.list(merged)
        this.tasks = res.data?.records || []
        this.total = res.data?.total || 0
        if (merged.page) this.currentPage = merged.page
        if (merged.pageSize) this.pageSize = merged.pageSize
      } finally {
        this.loading = false
      }
    },
    async fetchTask(id) {
      this.loading = true
      try {
        const res = await taskApi.get(id)
        this.currentTask = res.data
      } finally {
        this.loading = false
      }
    },
    async createTask(projectId) {
      const res = await taskApi.create(projectId)
      await this.fetchTasks()
      return res
    },
    async cancelTask(id) {
      await taskApi.cancel(id)
      await this.fetchTasks()
    },
  },
})
