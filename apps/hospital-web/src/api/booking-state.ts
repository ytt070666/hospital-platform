export const appointmentLabels = { HOLDING: '待确认', BOOKED: '已预约', CANCELLED: '已取消', EXPIRED: '已失效' };
export const attemptKey = (schedule: number, slot: number | null, member: number | null) => `hospital.booking.key.${schedule}.${slot ?? 'session'}.${member ?? 'member'}`;
type ApiFailure = { response?: { status?: number; data?: { code?: string } }; data?: { code?: string } };
function failure(error: unknown): ApiFailure { return typeof error === 'object' && error !== null ? error as ApiFailure : {}; }
export function httpStatus(error: unknown): number | undefined { return failure(error).response?.status; }
export function appointmentError(error: unknown): string {
  const code = failure(error).response?.data?.code ?? failure(error).data?.code;
  const messages: Record<string, string> = { QUOTA_001: '该号源刚刚被其他患者预约，请重新选择', APPOINTMENT_003: '该就诊人已预约此场次，可在我的预约查看', APPOINTMENT_004: '号源保留已超时，请重新选择', APPOINTMENT_006: '预约已结束，请刷新查看最新状态', APPOINTMENT_009: '当前不在预约时间窗口', APPOINTMENT_010: '该就诊人暂不符合预约条件，请先完成实名信息', APPOINTMENT_011: '该门诊已停诊或不可预约，请重新选择', APPOINTMENT_012: '该时段已不可预约，请重新选择', APPOINTMENT_008: '本次预约信息已变化，请重新发起' };
  return code ? messages[code] ?? '网络或服务异常，请检查网络后重试' : '网络或服务异常，请检查网络后重试';
}
