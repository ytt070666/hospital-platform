<script setup lang="ts">
/* eslint-disable @typescript-eslint/no-explicit-any */
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import http from '../api/http';
import { useAuthStore } from '../stores/auth';

const auth=useAuthStore();
const can=(permission:string)=>auth.session?.permissions.includes(permission)??false;
const orders=ref<any[]>([]), report=ref<any>({findings:'',impression:'',conclusion:''}), selected=ref<any>(null), created=ref<any>(null), history=ref<any[]>([]), amendReason=ref('');
function message(error:any,fallback:string){ElMessage.error(error?.response?.data?.message??fallback)}
async function load(){try{orders.value=(await http.get('/imaging/orders')).data.data}catch(error){message(error,'无法读取影像工作队列')}}
async function acquire(row:any){try{await http.post(`/imaging/orders/${row.id}/acquire`);await load();ElMessage.success('影像检查已采集')}catch(error){message(error,'影像采集失败')}}
async function createReport(){if(!selected.value||!report.value.findings.trim())return;try{created.value=(await http.post(`/imaging/orders/${selected.value.id}/reports`,report.value)).data.data;ElMessage.success('影像报告草稿已创建')}catch(error){message(error,'创建影像报告失败')}}
async function openReport(row:any){if(!row.reportId)return;try{selected.value=row;created.value=(await http.get(`/diagnostic-reports/${row.reportId}`)).data.data;report.value={findings:created.value.findings??'',impression:created.value.impression??'',conclusion:created.value.conclusion??''};await loadHistory()}catch(error){message(error,'无法打开影像报告')}}
async function loadHistory(){if(!created.value)return;try{history.value=(await http.get(`/diagnostic-reports/${created.value.id}/revisions`)).data.data}catch(error){message(error,'无法读取报告修订历史')}}
async function sign(){if(!created.value)return;try{created.value=(await http.put(`/diagnostic-reports/${created.value.id}/draft`,{version:created.value.version,...report.value})).data.data;created.value=(await http.post(`/diagnostic-reports/${created.value.id}/sign`,{version:created.value.version})).data.data;await loadHistory();await load();ElMessage.success('影像报告已签署并完成')}catch(error){message(error,'签署影像报告失败')}}
async function amend(){if(!created.value||created.value.status!=='FINAL'||!amendReason.value.trim())return;try{created.value=(await http.post(`/diagnostic-reports/${created.value.id}/amendments`,{...report.value,amendReason:amendReason.value.trim()})).data.data;amendReason.value='';await loadHistory();ElMessage.success('已创建影像报告修订草稿')}catch(error){message(error,'创建影像报告修订失败')}}
onMounted(load);
</script>

<template>
  <section class="workbench">
    <header><div><h1>影像工作台</h1><p>仅展示当前影像岗位在其临床执行范围内的申请单。</p></div><el-button @click="load">刷新</el-button></header>
    <el-table :data="orders" @row-click="selected=$event"><el-table-column prop="orderNo" label="申请单号"/><el-table-column prop="priority" label="优先级"/><el-table-column prop="status" label="申请状态"/><el-table-column prop="studyStatus" label="检查状态"/><el-table-column label="操作"><template #default="scope"><el-button v-if="can('hospital:clinical:imaging:study:execute')&&scope.row.studyStatus==='ORDERED'" type="primary" link @click.stop="acquire(scope.row)">采集影像</el-button><el-button v-if="can('hospital:clinical:imaging:report:create')&&scope.row.studyStatus==='ACQUIRED'" type="success" link @click.stop="selected=scope.row">撰写报告</el-button><el-button v-if="scope.row.reportId" type="info" link @click.stop="openReport(scope.row)">打开报告</el-button></template></el-table-column></el-table>
    <article v-if="can('hospital:clinical:imaging:report:create')&&selected" class="panel"><h2>影像报告</h2><p>当前申请单：{{ selected.orderNo }}</p><el-form label-position="top"><el-form-item label="所见"><el-input v-model="report.findings" type="textarea" :disabled="created?.status==='FINAL'"/></el-form-item><el-form-item label="印象"><el-input v-model="report.impression" type="textarea" :disabled="created?.status==='FINAL'"/></el-form-item><el-form-item label="结论"><el-input v-model="report.conclusion" type="textarea" :disabled="created?.status==='FINAL'"/></el-form-item><el-form-item v-if="created?.status==='FINAL'" label="修订原因"><el-input v-model="amendReason"/></el-form-item><el-button v-if="!created" type="primary" @click="createReport">创建报告草稿</el-button><el-button v-if="created?.status==='DRAFT'" type="success" @click="sign">签署报告</el-button><el-button v-if="created?.status==='FINAL'" type="warning" @click="amend">创建修订草稿</el-button></el-form><el-table v-if="history.length" :data="history"><el-table-column prop="revisionNo" label="版本"/><el-table-column prop="revisionType" label="类型"/><el-table-column prop="status" label="状态"/><el-table-column prop="signedBy" label="签署人"/><el-table-column prop="signedAt" label="签署时间"/><el-table-column prop="amendReason" label="修订原因"/></el-table></article>
    <el-alert v-if="!can('hospital:clinical:imaging:study:execute')&&!can('hospital:clinical:imaging:report:create')" title="当前角色无影像执行权限" type="info" show-icon/>
  </section>
</template>

<style scoped>
.workbench{display:grid;gap:16px}.workbench header{display:flex;justify-content:space-between}.panel{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:16px}
</style>
