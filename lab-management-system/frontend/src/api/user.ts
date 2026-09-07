import request from './request'

export const listUsers = (params?: any) => request.get('/users', { params })
export const createUser = (data: any) => request.post('/users', data)
export const listLogs = () => request.get('/logs')
export const importUsers = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/users/import', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}
