import { defineStore } from 'pinia'
import { projectApi } from '@/api'

export const useProjectStore = defineStore('project', {
  state: () => ({
    projects: [],
    currentProject: null,
    loading: false,
    total: 0,
    currentPage: 1,
    pageSize: 10,
  }),
  actions: {
    async fetchProjects(page = 1, pageSize = 10) {
      this.loading = true
      this.currentPage = page
      this.pageSize = pageSize
      try {
        const res = await projectApi.list({ page, pageSize })
        this.projects = res.data?.records || []
        this.total = res.data?.total || 0
      } finally {
        this.loading = false
      }
    },
    async fetchProject(id) {
      this.loading = true
      try {
        const res = await projectApi.get(id)
        this.currentProject = res.data
      } finally {
        this.loading = false
      }
    },
    async createProject(formData) {
      const res = await projectApi.create(formData)
      await this.fetchProjects(this.currentPage, this.pageSize)
      return res
    },
    async deleteProject(id) {
      await projectApi.delete(id)
      await this.fetchProjects(this.currentPage, this.pageSize)
    },
  },
})
