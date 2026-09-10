<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import http from '../api/http';
import patient from '../api/patient';
import { appointmentApi } from '../api/appointment';
import { appointmentError, attemptKey } from '../api/booking-state';

type Slot = { id: number; startTime: string; endTime: string; availableQuota: number; status: string };
type Plan = { id: number; scheduleDate: string; doctorName: string; departmentName: string; campusName: string; clinicType?: string; sessionName: string; startTime: string; endTime: string; availableQuota: number; slotMode: boolean; slots: Slot[] };
type Member = { id: number; name: string; relationship: string; usableForAppointment: boolean; realNameStatus?: string };
const route = useRoute(), router = useRouter();
const loading = ref(false), message = ref(''), plans = ref<Plan[]>([]), members = ref<Member[]>([]);
const selectedScheduleId = ref(Number(route.query.scheduleId || 0)), selectedSlotId = ref<number | null>(route.query.slotId ? Number(route.query.slotId) : null);
const memberId = ref<number | null>(route.query.memberId ? Number(route.query.memberId) : null), selectedDate = ref(String(route.query.date || '')), submitting = ref(false);
const dates = computed(() => [...new Set(plans.value.map(plan => plan.scheduleDate))]);
const datePlans = computed(() => selectedDate.value ? plans.value.filter(plan => plan.scheduleDate === selectedDate.value) : plans.value);
const selected = computed(() => plans.value.find(plan => plan.id === selectedScheduleId.value) ?? null);
const slots = computed(() => selected.value?.slots ?? []);
const usableMembers = computed(() => members.value.filter(member => member.usableForAppointment));
const key = computed(() => attemptKey(selectedScheduleId.value, selectedSlotId.value, memberId.value));
const slotPast = (slot: Slot) => !!selected.value && new Date(`${selected.value.scheduleDate}T${slot.startTime}`).getTime() <= Date.now();
function selectDefaultMember(defaultMemberId: number | null) {
  const requested = Number(route.query.memberId || memberId.value || 0);
  memberId.value = usableMembers.value.find(member => member.id === requested)?.id
    ?? usableMembers.value.find(member => member.id === defaultMemberId)?.id
    ?? usableMembers.value[0]?.id ?? null;
}
async function load() {
  loading.value = true;
  try {
    const doctorId = route.query.doctorId, departmentId = route.query.departmentId;
    const url = doctorId ? `/public/doctors/${doctorId}/schedules` : departmentId ? `/public/departments/${departmentId}/schedules` : '/public/schedules';
    const result = await http.get(url, { params: { page: 1, pageSize: 100 } });
    plans.value = result.data.data.records;
    if (!selectedDate.value || !dates.value.includes(selectedDate.value)) selectedDate.value = dates.value[0] ?? '';
    if (!selectedScheduleId.value || !plans.value.some(plan => plan.id === selectedScheduleId.value))
      selectedScheduleId.value = datePlans.value[0]?.id ?? plans.value[0]?.id ?? 0;
    if (localStorage.getItem('hospital.patient.access')) {
      const [memberResponse, profileResponse] = await Promise.all([patient.get('/patient/members'), patient.get('/patient/me')]);
      members.value = memberResponse.data.data; selectDefaultMember(profileResponse.data.data.defaultMemberId);
    }
  } catch (error) { message.value = appointmentError(error); }
  finally { loading.value = false; }
}
function chooseDate(date: string) { selectedDate.value = date; selectedScheduleId.value = datePlans.value[0]?.id ?? 0; selectedSlotId.value = null; }
function choosePlan(id: number) { selectedScheduleId.value = id; selectedSlotId.value = null; }
function chooseSlot(id: number) { selectedSlotId.value = id; }
function bookingReturnPath(member?: number | null) {
  const query = new URLSearchParams();
  for (const key of ['doctorId', 'departmentId']) if (route.query[key]) query.set(key, String(route.query[key]));
  if (selectedScheduleId.value) query.set('scheduleId', String(selectedScheduleId.value));
  if (selectedSlotId.value) query.set('slotId', String(selectedSlotId.value));
  if (selectedDate.value) query.set('date', selectedDate.value);
  if (member ?? memberId.value) query.set('memberId', String(member ?? memberId.value));
  return `/booking?${query.toString()}`;
}
function loginOrMember() { void router.push(`/patient?returnTo=${encodeURIComponent(bookingReturnPath())}`); }
async function hold() {
  if (submitting.value) return;
  if (!localStorage.getItem('hospital.patient.access')) return loginOrMember();
  if (!selected.value || !memberId.value || (selected.value.slotMode && !selectedSlotId.value)) { message.value = '请完整选择排班、号段和就诊人'; return; }
  submitting.value = true; message.value = '';
  try {
    let idempotencyKey = localStorage.getItem(key.value);
    if (!idempotencyKey) { idempotencyKey = crypto.randomUUID(); localStorage.setItem(key.value, idempotencyKey); }
    const response = await appointmentApi.hold({ memberId: memberId.value, scheduleId: selected.value.id, slotId: selectedSlotId.value }, idempotencyKey);
    await router.replace(`/appointments/${response.data.data.id}`);
  } catch (error) {
    message.value = appointmentError(error);
    // Inventory is refreshed, but the attempt key is deliberately retained for a response-loss retry.
    await load();
    message.value = appointmentError(error);
  } finally { submitting.value = false; }
}
onMounted(load);
</script>
<template><section class="page booking"><h1>预约挂号</h1><p>选择出诊、号源与就诊人后，再确认保留号源。</p><p v-if="message" role="alert" class="notice">{{ message }}</p>
  <div v-if="loading" class="detail">正在加载排班与就诊人…</div>
  <template v-else>
    <div class="detail"><h2>1. 选择日期</h2><p v-if="!dates.length">暂无可预约号源，请选择其他医生或日期。</p><button v-for="date in dates" :key="date" :class="{ active: date === selectedDate }" @click="chooseDate(date)">{{ date }}</button></div>
    <div class="detail"><h2>2. 选择出诊</h2><p v-if="!datePlans.length">当天暂无可预约号源。</p><button v-for="plan in datePlans" :key="plan.id" :class="{ active: plan.id === selectedScheduleId }" :disabled="plan.availableQuota < 1" @click="choosePlan(plan.id)">{{ plan.doctorName }} · {{ plan.departmentName }} · {{ plan.campusName }} · {{ plan.clinicType || '普通门诊' }}<br>{{ plan.sessionName }} {{ plan.startTime }}-{{ plan.endTime }} · 剩余 {{ plan.availableQuota }} 号</button></div>
    <div v-if="selected?.slotMode" class="detail"><h2>3. 选择时段</h2><button v-for="slot in slots" :key="slot.id" :class="{ active: slot.id === selectedSlotId }" :disabled="slot.availableQuota < 1 || slot.status !== 'ACTIVE' || slotPast(slot)" @click="chooseSlot(slot.id)">{{ slot.startTime }}-{{ slot.endTime }} · {{ slot.availableQuota > 0 ? '剩余 ' + slot.availableQuota + ' 号' : '已满' }}<small v-if="slotPast(slot)">已结束</small></button></div>
    <div class="detail"><h2>{{ selected?.slotMode ? '4' : '3' }}. 选择就诊人</h2><template v-if="!members.length"><p>登录后可选择本人或家庭就诊人。</p><button @click="loginOrMember">登录或新增就诊人</button></template><template v-else><label v-for="member in usableMembers" :key="member.id" class="choice"><input v-model="memberId" type="radio" :value="member.id"> {{ member.name }} · {{ member.relationship }} <small v-if="member.realNameStatus !== 'VERIFIED'">未实名</small></label><button @click="loginOrMember">新增就诊人</button></template></div>
    <div v-if="selected" class="detail"><h2>预约确认</h2><p>就诊人：{{ usableMembers.find(member => member.id === memberId)?.name || '未选择' }}</p><p>{{ selected.doctorName }} · {{ selected.departmentName }} · {{ selected.campusName }} · {{ selected.clinicType || '普通门诊' }}</p><p>{{ selected.scheduleDate }} · {{ selected.sessionName }} · {{ selectedSlotId ? slots.find(slot => slot.id === selectedSlotId)?.startTime + '-' + slots.find(slot => slot.id === selectedSlotId)?.endTime : selected.startTime + '-' + selected.endTime }}</p><button :disabled="submitting" @click="hold">{{ submitting ? '正在保留号源…' : '确认并保留号源' }}</button></div>
  </template></section></template>
<style scoped>.booking{max-width:860px}.detail button{display:block;width:100%;margin:10px 0;text-align:left;min-height:44px}.detail button.active{background:#0d6186}.choice{display:block;padding:10px 0}.notice{padding:12px;border-radius:8px;background:#fff4e5;color:#9a4a00}small{color:#b45309}</style>
