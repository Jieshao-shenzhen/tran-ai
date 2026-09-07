import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 清除本地登录态并跳转登录页 */
function clearAuthAndGoLogin() {
  localStorage.removeItem('token')
  localStorage.removeItem('role')
  localStorage.removeItem('username')
  localStorage.removeItem('userId')
  router.push('/login')
}

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

// 请求拦截：自动附加 Bearer token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截：统一 Result 处理。后端 BizException 返回 HTTP 200 + body.code，
// 因此业务码（401/500）必须从 body 判断，不能依赖 HTTP 状态码。
request.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult<unknown>
    if (res.code !== 200) {
      if (res.code === 401) clearAuthAndGoLogin()
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      clearAuthAndGoLogin()
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

/** 带类型的请求工具：自动解包 Result.data */
export const http = {
  get: <T>(url: string, config?: object) => request.get(url, config) as unknown as Promise<T>,
  post: <T>(url: string, data?: unknown, config?: object) =>
    request.post(url, data, config) as unknown as Promise<T>
}

export default request