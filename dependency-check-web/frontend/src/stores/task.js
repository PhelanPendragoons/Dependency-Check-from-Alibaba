import { defineStore } from 'pinia'
import { taskApi } from '@/api'

export const useTaskStore = defineStore('task', {
  state: () => ({
    tasks: [],
    currentTask: null,
    loading: false,
  }),
  actions: {
    async fetchTasks(params) {
      this.loading = true
      try {
        const res = await taskApi.list(params)
        this.tasks = res.data || []
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
