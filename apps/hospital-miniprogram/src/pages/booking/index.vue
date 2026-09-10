<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request, patientSession } from '../../api/http';
import { appointmentApi } from '../../api/appointment';
type Slot = { id: number; startTime: string; endTime: string; availableQuota: number; status: string };
type Plan = { id: number; scheduleDate: string; doctorName: string; departmentName: string; campusName: string; clinicType?: string; sessionName: string; startTime: string; endTime: string; availableQuota: number; slotMode: boolean; slots: Slot[] };
const plans = ref<Plan[]>([]), members = ref<any[]>([]), scheduleId = ref(0), slotId = ref<number | null>(null), memberId = ref<number | null>(null), selectedDate = ref(''), tip = ref(''), busy = ref(false), loading = ref(false);
const selected = computed(() => plans.value.find(plan => plan.id === scheduleId.value));
const dates = computed(() => [...new Set(plans.value.map(plan => plan.scheduleDate))]);
const datePlans = computed(() => selectedDate.value ? plans.value.filter(plan => plan.scheduleDate === selectedDate.value) : plans.value);
const key = computed(() => `hospital.booking.key.${scheduleId.value}.${slotId.value ?? 'session'}.${memberId.value ?? 'member'}`);
function options() { return (getCurrentPages()[getCurrentPages().length - 1] as any)?.options || {}; }
function errorMessage(error: any) { const code = error?.data?.code; return ({ QUOTA_001: '该号源刚刚被其他患者预约，请重新选择', APPOINTMENT_003: '该就诊人已有有效预约，请查看我的预约', APPOINTMENT_004: '号源保留已超时，请重新选择', APPOINTMENT_009: '当前不在预约时间窗口', APPOINTMENT_010: '该就诊人暂不符合预约条件，请先完成实名信息', APPOINTMENT_011: '该门诊已停诊或不可预约，请重新选择', APPOINTMENT_012: '该时段已不可预约，请重新选择', APPOINTMENT_008: '本次预约信息已变化，请重新发起' } as Record<string, string>)[code] || '网络连接异常，请检查网络后重试'; }
function returnPath(member?: number | null) { const q = options(); const entries = [`doctorId=${q.doctorId || ''}`, `departmentId=${q.departmentId || ''}`, `scheduleId=${scheduleId.value}`, `slotId=${slotId.value || ''}`, `date=${selectedDate.value}`, `memberId=${member ?? memberId.value ?? ''}`].filter(item => !item.endsWith('=')); return '/pages/booking/index?' + entries.join('&'); }
async function load() {
  loading.value = true;
  try {
    const q = options(), url = q.doctorId ? `/public/doctors/${q.doctorId}/schedules` : q.departmentId ? `/public/departments/${q.departmentId}/schedules` : '/public/schedules';
    plans.value = (await request<any>({ url, data: { page: 1, pageSize: 100 } })).data.records;
    selectedDate.value = dates.value.includes(String(q.date || selectedDate.value)) ? String(q.date || selectedDate.value) : dates.value[0] || '';
    const requestedSchedule = Number(q.scheduleId || scheduleId.value || 0);
    scheduleId.value = plans.value.some(plan => plan.id === requestedSchedule) ? requestedSchedule : (datePlans.value[0]?.id || plans.value[0]?.id || 0);
    if (uni.getStorageSync(patientSession().accessKey)) {
      const [memberResponse, profileResponse] = await Promise.all([request<any>({ url: '/patient/members' }), request<any>({ url: '/patient/me' })]);
      members.value = memberResponse.data; const requestedMember = Number(q.memberId || memberId.value || 0);
      memberId.value = members.value.find(member => member.usableForAppointment && member.id === requestedMember)?.id ?? members.value.find(member => member.usableForAppointment && member.id === profileResponse.data.defaultMemberId)?.id ?? members.value.find(member => member.usableForAppointment)?.id ?? null;
    }
  } catch (error) { tip.value = errorMessage(error); } finally { loading.value = false; }
}
function chooseDate(date: string) { selectedDate.value = date; scheduleId.value = datePlans.value[0]?.id || 0; slotId.value = null; }
function chooseSchedule(id: number) { scheduleId.value = id; slotId.value = null; }
function openPatient() { uni.navigateTo({ url: '/pages/patient/index?returnTo=' + encodeURIComponent(returnPath()) }); }
async function hold() {
  if (busy.value) return;
  if (!uni.getStorageSync(patientSession().accessKey)) return openPatient();
  if (!selected.value || !memberId.value || (selected.value.slotMode && !slotId.value)) { tip.value = '请选择排班、号段和就诊人'; return; }
  busy.value = true; tip.value = '';
  try {
    let idempotencyKey = uni.getStorageSync(key.value);
    if (!idempotencyKey) { idempotencyKey = `mp-${Date.now()}-${Math.random().toString(36).slice(2)}`; uni.setStorageSync(key.value, idempotencyKey); }
    const result = await appointmentApi.hold({ memberId: memberId.value, scheduleId: scheduleId.value, slotId: slotId.value }, idempotencyKey);
    uni.redirectTo({ url: `/pages/appointment-detail/index?id=${result.data.id}` });
  } catch (error) { tip.value = errorMessage(error); await load(); tip.value = errorMessage(error); } finally { busy.value = false; }
}
onMounted(load); onShow(load);
</script>
<template><view class="page"><view class="card"><text class="title">预约挂号</text><text v-if="tip" class="tip">{{ tip }}</text><text class="heading">选择日期</text><button v-for="date in dates" :key="date" :class="{active:date===selectedDate}" @click="chooseDate(date)">{{ date }}</button><text class="heading">选择出诊</text><text v-if="!datePlans.length">当天暂无可预约号源</text><button v-for="plan in datePlans" :key="plan.id" :class="{active:plan.id===scheduleId}" :disabled="plan.availableQuota<1" @click="chooseSchedule(plan.id)">{{ plan.doctorName }} · {{ plan.departmentName }} · {{ plan.campusName }}<br>{{ plan.sessionName }} · 剩余 {{ plan.availableQuota }} 号</button></view><view v-if="selected?.slotMode" class="card"><text class="heading">选择时段</text><button v-for="slot in selected.slots" :key="slot.id" :class="{active:slot.id===slotId}" :disabled="slot.availableQuota<1||slot.status!=='ACTIVE'" @click="slotId=slot.id">{{ slot.startTime }}-{{ slot.endTime }} · {{ slot.availableQuota>0?'剩余 '+slot.availableQuota+' 号':'已满' }}</button></view><view class="card"><text class="heading">选择就诊人</text><radio-group @change="memberId=Number($event.detail.value)"><label v-for="member in members.filter(x=>x.usableForAppointment)" :key="member.id" class="choice"><radio :value="String(member.id)" :checked="member.id===memberId"/>{{ member.name }} · {{ member.relationship }}</label></radio-group><button @click="openPatient">新增就诊人</button></view><view v-if="selected" class="card"><text class="heading">预约确认</text><text>就诊人：{{ members.find(x=>x.id===memberId)?.name||'未选择' }}</text><text>{{ selected.doctorName }} · {{ selected.departmentName }} · {{ selected.campusName }} · {{ selected.clinicType||'普通门诊' }}</text><text>{{ selected.scheduleDate }} {{ slotId ? selected.slots.find(x=>x.id===slotId)?.startTime+'-'+selected.slots.find(x=>x.id===slotId)?.endTime : selected.startTime+'-'+selected.endTime }}</text><button :disabled="busy||loading" @click="hold">{{ busy?'正在保留号源…':'确认并保留号源' }}</button></view></view></template>
<style scoped>.page{padding:28rpx}.card{display:flex;flex-direction:column;gap:18rpx;background:#fff;border-radius:16rpx;padding:28rpx;margin-bottom:20rpx}.title{font-size:40rpx;font-weight:700}.heading{font-weight:700;color:#17324d;margin-top:8rpx}.choice{display:block;padding:14rpx 0}.active{background:#176b92;color:#fff}.tip{color:#b45309}</style>
