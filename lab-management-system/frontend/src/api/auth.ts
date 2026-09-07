import { http } from './request'

export interface UserInfo {
  id: number
  username: string
  realName: string
  role: string
}

export function login(username: string, password: string) {
  return http.post<{ token: string }>('/auth/login', { username, password })
}

export function me() {
  return http.get<UserInfo>('/auth/me')
}