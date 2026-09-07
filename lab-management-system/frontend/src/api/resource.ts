import request from './request'

export const listRooms = (params?: any) => request.get('/rooms', { params })
export const saveRoom = (data: any) => request.post('/rooms', data)
export const listDevices = (params?: any) => request.get('/devices', { params })
export const saveDevice = (data: any) => request.post('/devices', data)
export const listMaterials = (params?: any) => request.get('/materials', { params })
export const stockMaterial = (id: number, type: string, quantity: number) =>
  request.post(`/materials/${id}/stock`, null, { params: { type, quantity } })
