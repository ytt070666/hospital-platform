<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import http from '../api/http';
import { useAuthStore } from '../stores/auth';
type AppointmentRow = { id:number; appointmentNo:string; patientNo:string; memberName:string; relationship:string; doctorName:string; originalDoctorName?:string|null; substituted?:boolean; departmentName:string; campusName:string; clinicType?:string; scheduleDate:string; startTime:string; endTime:string; status:string; holdExpiresAt?:string; bookedAt?:string; cancelledAt?:string; createdAt?:string };
type AppointmentDetail = { appointment:AppointmentRow; statusHistory:Array<Record<string,unknown>>; inventory?:Record<string,unknown>; audit?:Array<Record<string,unknown>> };
const labels: Record<string,string> = { HOLDING:'待确认', BOOKED:'已预约', CANCELLED:'已取消', EXPIRED:'已失效' };
const auth = useAuthStore(), rows = ref<AppointmentRow[]>([]), total = ref(0), loading = ref(false), error = ref('');
const appointmentNo = ref(''), patientNo = ref(''), memberName = ref(''), doctorId = ref(''), departmentId = ref(''), campusId = ref(''), clinicTypeId = ref(''), scheduleDate = ref(''), createdRange = ref<string[] | null>([]), status = ref('');
const page = ref(1), pageSize = 10, detail = ref<AppointmentDetail | null>(null), detailOpen = ref(false), reason = ref(''), cancelling = ref(false);
const canCancel = computed(() => auth.session?.permissions.includes('hospital:appointment:cancel') ?? false);
const positiveId = (value: string) => /^\d+$/.test(value) && Number(value) > 0 ? Number(value) : undefined;
async function load(reset = false) {
  if (reset) page.value = 1;
  loading.value = true; error.value = '';
  try {
    const range = createdRange.value || [];
    const response = await http.get('/admin/appointments', { params: { appointmentNo: appointmentNo.value || undefined, patientNo: patientNo.value || undefined, memberName: memberName.value || undefined, doctorId: positiveId(doctorId.value), departmentId: positiveId(departmentId.value), campusId: positiveId(campusId.value), clinicTypeId: positiveId(clinicTypeId.value), scheduleDate: scheduleDate.value || undefined, status: status.value || undefined, createdFrom: range[0] || undefined, createdTo: range[1] || undefined, page: page.value, pageSize } });
    rows.value = response.data.data.records; total.value = response.data.data.total;
  } catch { error.value = '加载预约数据失败，请检查权限或网络后重试。'; } finally { loading.value = false; }
}
async function open(row: AppointmentRow) { try { detail.value = (await http.get('/admin/appointments/' + row.id)).data.data; reason.value = ''; detailOpen.value = true; } catch { error.value = '无法读取该预约详情。'; } }
async function cancel() {
  if (!detail.value || !reason.value.trim() || cancelling.value) return;
  cancelling.value = true;
  try { await http.post('/admin/appointments/' + detail.value.appointment.id + '/cancel', { reasonCode: 'STAFF_CANCEL', reason: reason.value.trim() }); await open(detail.value.appointment); await load(); }
  catch { error.value = '协助取消未完成，请刷新后查看预约状态。'; }
  finally { cancelling.value = false; }
}
function move(delta: number) { page.value += delta; void load(); }
onMounted(load);
</script>
<template><section><h1>预约管理中心</h1><p>仅展示当前数据范围内预约；就诊人姓名脱敏显示，精确姓名查询使用不可逆哈希。</p><p v-if="error" class="error" role="alert">{{ error }} <el-button text @click="load">重试</el-button></p>
  <el-form inline @submit.prevent="load(true)"><el-form-item label="预约编号"><el-input v-model="appointmentNo" clearable/></el-form-item><el-form-item label="患者编号"><el-input v-model="patientNo" clearable/></el-form-item><el-form-item label="就诊人姓名"><el-input v-model="memberName" clearable/></el-form-item><el-form-item label="医生 ID"><el-input v-model="doctorId" inputmode="numeric" clearable/></el-form-item><el-form-item label="科室 ID"><el-input v-model="departmentId" inputmode="numeric" clearable/></el-form-item><el-form-item label="院区 ID"><el-input v-model="campusId" inputmode="numeric" clearable/></el-form-item><el-form-item label="门诊类型 ID"><el-input v-model="clinicTypeId" inputmode="numeric" clearable/></el-form-item><el-form-item label="排班日期"><el-date-picker v-model="scheduleDate" value-format="YYYY-MM-DD" type="date" clearable/></el-form-item><el-form-item label="创建日期"><el-date-picker v-model="createdRange" value-format="YYYY-MM-DD" type="daterange" range-separator="至" clearable/></el-form-item><el-form-item label="状态"><el-select v-model="status" clearable><el-option v-for="(label,value) in labels" :key="value" :label="label" :value="value"/></el-select></el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form>
  <el-table :data="rows" v-loading="loading" @row-click="open"><el-table-column prop="appointmentNo" label="预约编号"/><el-table-column prop="patientNo" label="患者编号"/><el-table-column prop="memberName" label="就诊人"/><el-table-column prop="doctorName" label="医生"/><el-table-column prop="departmentName" label="科室"/><el-table-column prop="campusName" label="院区"/><el-table-column prop="scheduleDate" label="日期"/><el-table-column label="状态"><template #default="{row}">{{ labels[row.status] || row.status }}</template></el-table-column></el-table>
  <div class="pager"><el-button :disabled="loading||page===1" @click="move(-1)">上一页</el-button><span>第 {{ page }} 页，共 {{ total }} 条</span><el-button :disabled="loading||page*pageSize>=total" @click="move(1)">下一页</el-button></div>
  <el-drawer v-model="detailOpen" title="预约详情" size="620px"><template v-if="detail"><p>预约编号：{{ detail.appointment.appointmentNo }}</p><p>患者编号：{{ detail.appointment.patientNo }}</p><p>就诊人：{{ detail.appointment.memberName }} · {{ detail.appointment.relationship }}</p><p>{{ detail.appointment.doctorName }}<template v-if="detail.appointment.substituted">（替诊医生，原出诊：{{ detail.appointment.originalDoctorName }}）</template> · {{ detail.appointment.departmentName }} · {{ detail.appointment.campusName }} · {{ detail.appointment.clinicType || '普通门诊' }}</p><p>{{ detail.appointment.scheduleDate }} {{ detail.appointment.startTime }}-{{ detail.appointment.endTime }} · {{ labels[detail.appointment.status] || detail.appointment.status }}</p><p>创建：{{ detail.appointment.createdAt || '-' }}；保留截止：{{ detail.appointment.holdExpiresAt || '-' }}</p><p>确认：{{ detail.appointment.bookedAt || '-' }}；取消：{{ detail.appointment.cancelledAt || '-' }}</p><h3>号源库存</h3><pre>{{ detail.inventory }}</pre><h3>状态历史</h3><el-table :data="detail.statusHistory"><el-table-column prop="fromStatus" label="原状态"/><el-table-column prop="toStatus" label="新状态"/><el-table-column prop="reason" label="原因"/><el-table-column prop="operatorType" label="操作方"/><el-table-column prop="createdAt" label="时间"/></el-table><h3>审计记录</h3><el-table :data="detail.audit || []"><el-table-column prop="action" label="操作"/><el-table-column prop="result" label="结果"/><el-table-column prop="createdAt" label="时间"/></el-table><template v-if="canCancel && ['HOLDING','BOOKED'].includes(detail.appointment.status)"><h3>协助取消</h3><el-input v-model="reason" placeholder="必须填写取消原因"/><el-button type="danger" :loading="cancelling" :disabled="!reason.trim()" @click="cancel">确认取消预约</el-button></template></template></el-drawer>
</section></template>
<style scoped>.pager{display:flex;align-items:center;gap:12px;margin-top:16px}.error{color:#b45309}pre{white-space:pre-wrap;background:#f6f8fa;padding:10px;border-radius:6px}</style>
