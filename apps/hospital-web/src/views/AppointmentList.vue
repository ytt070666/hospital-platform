<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { appointmentApi, type Appointment, type AppointmentStatus } from '../api/appointment';
import { appointmentLabels, httpStatus } from '../api/booking-state';
const router = useRouter(), items = ref<Appointment[]>([]), status = ref<AppointmentStatus | ''>('');
const page = ref(1), pageSize = 10, total = ref(0), loading = ref(false), error = ref('');
async function load() {
  loading.value = true; error.value = '';
  try { const data = (await appointmentApi.list({ status: status.value || undefined, page: page.value, pageSize })).data.data; items.value = data.records; total.value = data.total; }
  catch (e: unknown) { if (httpStatus(e) === 401 || !localStorage.getItem('hospital.patient.access')) await router.replace('/patient?returnTo=/appointments'); else error.value = '加载失败，请检查网络后重试'; }
  finally { loading.value = false; }
}
function changePage(value: number) { page.value = value; void load(); }
onMounted(() => {
  if (!localStorage.getItem('hospital.patient.access')) { void router.replace('/patient?returnTo=/appointments'); return; }
  void load();
});
</script>
<template><section class="page"><h1>我的预约</h1>
  <label>预约状态 <select v-model="status" :disabled="loading" @change="changePage(1)"><option value="">全部</option><option v-for="(label, value) in appointmentLabels" :key="value" :value="value">{{ label }}</option></select></label>
  <p v-if="loading">正在加载预约…</p><p v-else-if="error" role="alert">{{ error }} <button @click="load">重试</button></p><p v-else-if="!items.length">暂无预约记录。</p>
  <router-link v-for="item in items" :key="item.id" class="detail" :to="`/appointments/${item.id}`"><strong>{{ item.appointmentNo }}</strong><p>{{ appointmentLabels[item.status] }} · {{ item.memberName || '就诊人' }} · {{ item.doctorName }} · {{ item.departmentName }}</p><p>{{ item.scheduleDate }} {{ item.startTime }}-{{ item.endTime }}</p></router-link>
  <nav aria-label="预约分页"><button :disabled="loading || page === 1" @click="changePage(page - 1)">上一页</button> 第 {{ page }} 页 · 共 {{ total }} 条 <button :disabled="loading || page * pageSize >= total" @click="changePage(page + 1)">下一页</button></nav>
</section></template>
