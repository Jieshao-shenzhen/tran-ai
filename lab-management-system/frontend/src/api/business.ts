import request from './request'

export const listReservations = (params?: any) => request.get('/reservations', { params })
export const createReservation = (data: any) => request.post('/reservations', data)
export const approveReservation = (id: number) => request.post(`/reservations/${id}/approve`)
export const rejectReservation = (id: number, reason: string) =>
  request.post(`/reservations/${id}/reject`, null, { params: { reason } })
export const cancelReservation = (id: number) => request.delete(`/reservations/${id}`)
export const listRepairs = (params?: any) => request.get('/repairs', { params })
export const createRepair = (data: any) => request.post('/repairs', data)
export const approveRepair = (id: number) => request.post(`/repairs/${id}/approve`)
export const assignRepair = (id: number, assigneeId: number) =>
  request.post(`/repairs/${id}/assign`, null, { params: { assigneeId } })
export const rejectRepair = (id: number, reason: string) =>
  request.post(`/repairs/${id}/reject`, null, { params: { reason } })
export const finishRepair = (id: number, result: string) =>
  request.post(`/repairs/${id}/finish`, { result }) // 后端 @RequestBody Map 传 result
export const verifyRepair = (id: number) => request.post(`/repairs/${id}/verify`)
