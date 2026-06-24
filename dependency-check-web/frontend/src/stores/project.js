import { defineStore } from 'pinia'
import { projectApi } from '@/api'

export const useProjectStore = defineStore('project', {
  state: () => ({
    projects: [],
    currentProject: null,
    loading: false,
  }),
  actions: {
    async fetchProjects() {
      this.loading = true
      try {
        const res = await projectApi.list()
        this.projects = res.data || []
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
      await this.fetchProjects()
      return res
    },
    async deleteProject(id) {
      await projectApi.delete(id)
      await this.fetchProjects()
    },
  },
})
