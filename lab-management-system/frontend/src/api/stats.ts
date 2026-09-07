import request from './request'

export const getOverview = () => request.get('/stats/overview')
export const getRoomUsage = (params: any) => request.get('/stats/room-usage', { params })
