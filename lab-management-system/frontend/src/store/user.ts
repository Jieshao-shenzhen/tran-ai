import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    role: localStorage.getItem('role') || '',
    username: localStorage.getItem('username') || '',
    userId: Number(localStorage.getItem('userId')) || 0
  }),
  actions: {
    setAuth(token: string, role: string, username: string, userId: number) {
      this.token = token
      this.role = role
      this.username = username
      this.userId = userId
      localStorage.setItem('token', token)
      localStorage.setItem('role', role)
      localStorage.setItem('username', username)
      localStorage.setItem('userId', String(userId))
    },
    logout() {
      this.token = ''
      this.role = ''
      this.username = ''
      this.userId = 0
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      localStorage.removeItem('username')
      localStorage.removeItem('userId')
    }
  }
})
