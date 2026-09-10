import patient from './patient';

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
  hold: (body: { memberId: number; scheduleId: number; slotId?: number | null }, idempotencyKey: string) => patient.post<ApiResponse<Appointment>>('/patient/appointments/holds', body, { headers: { 'Idempotency-Key': idempotencyKey } }),
  detail: (id: number) => patient.get<ApiResponse<Appointment>>(`/patient/appointments/${id}`),
  list: (params?: { status?: AppointmentStatus; page?: number; pageSize?: number }) => patient.get<ApiResponse<Page<Appointment>>>('/patient/appointments', { params }),
  confirm: (id: number) => patient.post<ApiResponse<Appointment>>(`/patient/appointments/${id}/confirm`),
  cancel: (id: number, body?: { reasonCode?: string; reason?: string }) => patient.post<ApiResponse<Appointment>>(`/patient/appointments/${id}/cancel`, body),
  checkIn: (id: number) => patient.post<ApiResponse<{ encounterNo: string; queueNo: string; queueStatus: string; encounterStatus: string }>>(`/patient/appointments/${id}/check-in`)
};
