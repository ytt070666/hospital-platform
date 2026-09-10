<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { appointmentApi, type Appointment } from '../api/appointment';
import { appointmentError, appointmentLabels, attemptKey, httpStatus } from '../api/booking-state';
import RegistrationPaymentStatus from '../components/RegistrationPaymentStatus.vue';
const route = useRoute(), router = useRouter();
const item = ref<Appointment | null>(null), message = ref(''), loading = ref(false), busy = ref(false), queue = ref<{ queueNo:string; queueStatus:string } | null>(null);
const now = ref(performance.now()), receivedAt = ref(performance.now());
let refreshing = false, lastPoll = 0;
const left = computed(() => item.value?.holdExpiresAt
  ? Math.max(0, Date.parse(item.value.holdExpiresAt) - Date.parse(item.value.serverNow) - (now.value - receivedAt.value)) : 0);
const clock = computed(() => `${String(Math.floor(left.value / 60000)).padStart(2, '0')}:${String(Math.floor(left.value / 1000) % 60).padStart(2, '0')}`);
function accept(value: Appointment) {
  item.value = value; now.value = receivedAt.value = performance.now();
  if (value.status !== 'HOLDING') localStorage.removeItem(attemptKey(value.scheduleId, value.slotId, value.memberId));
}
async function refresh() {
  if (refreshing) return;
  refreshing = true;
  try { accept((await appointmentApi.detail(Number(route.params.id))).data.data); message.value = ''; }
  catch (e: unknown) {
    if (httpStatus(e) === 401 || !localStorage.getItem('hospital.patient.access')) {
      item.value = null; await router.replace('/patient?returnTo=' + encodeURIComponent(route.fullPath));
    } else message.value = httpStatus(e) === 403 ? '无权查看该预约' : '预约不存在或网络异常，请重试';
  } finally { refreshing = false; }
}
async function load() { loading.value = true; await refresh(); loading.value = false; }
async function confirm() {
  if (busy.value || item.value?.status !== 'HOLDING' || left.value === 0) return;
  busy.value = true;
  try { accept((await appointmentApi.confirm(item.value.id)).data.data); message.value = ''; }
  catch (e) { await refresh(); if ((item.value as Appointment | null)?.status !== 'BOOKED') message.value = appointmentError(e); }
  finally { busy.value = false; }
}
async function cancel() {
  if (busy.value || !item.value) return;
  const a = item.value;
  if (!window.confirm(`确定取消 ${a.memberName} 在 ${a.scheduleDate} ${a.startTime}-${a.endTime} 的 ${a.doctorName} 预约吗？号源将释放。`)) return;
  busy.value = true;
  try { accept((await appointmentApi.cancel(a.id, { reasonCode: 'PATIENT_CANCEL', reason: '患者主动取消' })).data.data); message.value = ''; }
  catch { await refresh(); if (item.value?.status !== 'CANCELLED') message.value = '取消结果尚未确认，请联网后刷新查看'; }
  finally { busy.value = false; }
}
async function checkIn() {
  if (!item.value || busy.value) return;
  busy.value = true;
  try { queue.value = (await appointmentApi.checkIn(item.value.id)).data.data; message.value = `已签到，候诊号 ${queue.value.queueNo}`; }
  catch { message.value = '当前暂不能签到，请以医院服务时间和缴费状态为准。'; }
  finally { busy.value = false; }
}
function resume() { if (document.visibilityState === 'visible') void refresh(); }
const timer = window.setInterval(() => {
  now.value = performance.now();
  // 等待服务端过期任务确认，客户端不自行伪造 EXPIRED 状态。
  if (item.value?.status === 'HOLDING' && left.value === 0 && now.value - lastPoll >= 3000) {
    lastPoll = now.value; void refresh();
  }
}, 500);
watch(() => route.params.id, load);
onMounted(() => { void load(); window.addEventListener('online', refresh); document.addEventListener('visibilitychange', resume); });
onBeforeUnmount(() => { clearInterval(timer); window.removeEventListener('online', refresh); document.removeEventListener('visibilitychange', resume); });
</script>
<template>
  <section class="page appointment">
    <p v-if="message" role="alert" class="notice">{{ message }} <button :disabled="loading" @click="load">刷新重试</button></p>
    <div v-if="loading" class="detail">正在加载预约…</div>
    <article v-else-if="item" class="detail">
      <h1>{{ item.status === 'BOOKED' ? '预约成功' : '预约详情' }}</h1>
      <p class="state">{{ appointmentLabels[item.status] }}</p>
      <p>预约编号：{{ item.appointmentNo }}</p><p>就诊人：{{ item.memberName || '就诊人' }}</p>
      <p>{{ item.doctorName }}<template v-if="item.substituted">（替诊医生，原出诊：{{ item.originalDoctorName }}）</template> · {{ item.departmentName }} · {{ item.campusName }} · {{ item.clinicType }}</p>
      <p>{{ item.scheduleDate }} · {{ item.sessionName }} · {{ item.startTime }}-{{ item.endTime }}</p>
      <p v-if="item.createdAt">创建时间：{{ item.createdAt }}</p>
      <template v-if="item.status === 'HOLDING'">
        <h2>号源已为您保留 {{ clock }}</h2><p>{{ left === 0 ? '保留时间已到，正在同步最终状态…' : '请在倒计时结束前确认预约；逾期将自动释放。' }}</p>
        <button :disabled="busy || left === 0" @click="confirm">{{ busy ? '正在确认…' : '确认预约' }}</button>
        <button :disabled="busy" @click="cancel">放弃本次预约</button>
      </template>
      <p v-if="item.status === 'EXPIRED'">号源保留已超时，请重新选择。</p>
      <button v-if="item.status === 'EXPIRED' || item.status === 'CANCELLED'" @click="router.push('/booking')">重新选号</button>
      <p v-if="item.bookedAt">确认时间：{{ item.bookedAt }}</p><p v-if="item.cancelledAt">取消时间：{{ item.cancelledAt }}</p>
      <RegistrationPaymentStatus v-if="item.status!=='HOLDING'" :appointment-id="item.id" :status="item.status" />
      <button v-if="item.status === 'BOOKED'" :disabled="busy" @click="checkIn">{{ busy ? '正在签到…' : '到院签到' }}</button>
      <p v-if="queue">候诊号：{{ queue.queueNo }} · 当前状态：{{ queue.queueStatus }}</p>
      <button v-if="item.status === 'BOOKED'" :disabled="busy" @click="cancel">取消预约</button>
      <p><router-link to="/appointments">查看我的预约</router-link></p>
    </article>
  </section>
</template>
<style scoped>.appointment{max-width:680px}.state{display:inline-block;padding:6px 12px;border-radius:20px;background:#e0f5fb;color:#0d6186;font-weight:bold}.detail button{margin:8px 8px 8px 0;min-height:44px}.notice{color:#9a4a00}</style>
