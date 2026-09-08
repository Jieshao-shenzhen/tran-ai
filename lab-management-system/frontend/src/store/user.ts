import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    role: localStorage.getItem('role') || '',
    username: localStorage.getItem('username') || '',
    realName: localStorage.getItem('realName') || '',
    userId: Number(localStorage.getItem('userId')) || 0
  }),
  actions: {
    setAuth(token: string, role: string, username: string, realName: string, userId: number) {
      this.token = token
      this.role = role
      this.username = username
      this.realName = realName
      this.userId = userId
      localStorage.setItem('token', token)
      localStorage.setItem('role', role)
      localStorage.setItem('username', username)
      localStorage.setItem('realName', realName)
      localStorage.setItem('userId', String(userId))
    },
    logout() {
      this.token = ''
      this.role = ''
      this.username = ''
      this.realName = ''
      this.userId = 0
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      localStorage.removeItem('username')
      localStorage.removeItem('realName')
      localStorage.removeItem('userId')
    }
  }
})
