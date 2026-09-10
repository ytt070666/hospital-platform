const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const accessKey = 'hospital.patient.access', refreshKey = 'hospital.patient.refresh';
let refreshing: Promise<void> | null = null;
type FailedResponse = { statusCode?: number; data?: { code?: string | number } };
function failedResponse(error: unknown): FailedResponse { return typeof error === 'object' && error !== null ? error as FailedResponse : {}; }
function transport<T>(options: UniApp.RequestOptions): Promise<T> {
  const token = uni.getStorageSync(accessKey);
  return new Promise((resolve, reject) => {
    uni.request({ ...options, url: `${baseUrl}${options.url}`, timeout: 10000,
      header: { ...options.header, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      success: response => {
        const body = response.data as { code?: string | number };
        if (response.statusCode >= 200 && response.statusCode < 300 && (body.code === undefined || String(body.code) === '0'))
          resolve(response.data as T);
        else reject({ statusCode: response.statusCode, data: body });
      }, fail: reject });
  });
}
export async function request<T>(options: UniApp.RequestOptions): Promise<T> {
  try { return await transport<T>(options); }
  catch (error: unknown) {
    if (failedResponse(error).statusCode !== 401 || !options.url.startsWith('/patient/') || options.url.startsWith('/patient/auth/')) throw error;
    if (!refreshing) {
      refreshing = transport<{ data: { accessToken: string; refreshToken: string } }>({
        url: '/patient/auth/refresh', method: 'POST',
        data: { refreshToken: uni.getStorageSync(refreshKey), clientType: 'MINI_PROGRAM_PATIENT' }
      }).then(result => {
        uni.setStorageSync(accessKey, result.data.accessToken);
        uni.setStorageSync(refreshKey, result.data.refreshToken);
      }).catch((refreshError: unknown) => {
        if (failedResponse(refreshError).statusCode === 401 || failedResponse(refreshError).statusCode === 403) clearSession();
        throw refreshError;
      }).finally(() => { refreshing = null; });
    }
    await refreshing;
    // 最多重试一次；刷新接口本身绝不递归触发刷新。
    return transport<T>(options);
  }
}
export function patientSession() { return { accessKey, refreshKey }; }
export function clearSession() { uni.removeStorageSync(accessKey); uni.removeStorageSync(refreshKey); }
