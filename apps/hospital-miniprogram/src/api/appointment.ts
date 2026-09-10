import { request } from './http';

export type AppointmentStatus = 'HOLDING' | 'BOOKED' | 'CANCELLED' | 'EXPIRED';
export interface Appointment {
  id: number; appointmentNo: string; status: AppointmentStatus; memberId: number; scheduleId: number; slotId: number | null;
  memberName: string | null; doctorName: string; originalDoctorName: string | null; substituted: boolean; departmentName: string; campusName: string; clinicType: string | null;
  scheduleDate: string; sessionName: string; startTime: string; endTime: string; createdAt: string | null;
  holdExpiresAt: string | null; bookedAt: string | null; cancelledAt: string | null; expiredAt: string | null; serverNow: string;
}
export interface ApiResponse<T> { code: number | string; message: string; data: T; traceId: string }
export interface Page<T> { page: number; pageSize: number; total: number; records: T[] }
export const appointmentApi = {
  hold: (data: { memberId: number; scheduleId: number; slotId?: number | null }, idempotencyKey: string) => request<ApiResponse<Appointment>>({ url: '/patient/appointments/holds', method: 'POST', data, header: { 'Idempotency-Key': idempotencyKey } }),
  detail: (id: number) => request<ApiResponse<Appointment>>({ url: `/patient/appointments/${id}`, method: 'GET' }),
  list: (data?: { status?: AppointmentStatus; page?: number; pageSize?: number }) => request<ApiResponse<Page<Appointment>>>({ url: '/patient/appointments', method: 'GET', data }),
  confirm: (id: number) => request<ApiResponse<Appointment>>({ url: `/patient/appointments/${id}/confirm`, method: 'POST' }),
  cancel: (id: number, data?: { reasonCode?: string; reason?: string }) => request<ApiResponse<Appointment>>({ url: `/patient/appointments/${id}/cancel`, method: 'POST', data }),
  checkIn: (id: number) => request<ApiResponse<{ queueNo:string; queueStatus:string }>>({ url: `/patient/appointments/${id}/check-in`, method: 'POST' }),
  queues: () => request<ApiResponse<Array<{ queueNo:string; status:string; roomName?:string; doctorName?:string; departmentName?:string; aheadCount:number }>>>({ url: '/patient/visits/queues', method: 'GET' })
};
