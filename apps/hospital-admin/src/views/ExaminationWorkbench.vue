<script setup lang="ts">
/* eslint-disable @typescript-eslint/no-explicit-any */
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import http from '../api/http';
import { useAuthStore } from '../stores/auth';

const auth=useAuthStore(); const can=(p:string)=>auth.session?.permissions.includes(p)??false;
const orders=ref<any[]>([]), selected=ref<any>(null), report=ref({findings:'',impression:'',conclusion:''}), created=ref<any>(null);
function failure(error:any,fallback:string){ElMessage.error(error?.response?.data?.message??fallback)}
async function load(){try{orders.value=(await http.get('/examinations/orders')).data.data}catch(error){failure(error,'无法读取检查执行队列')}}
async function move(row:any,action:'accept'|'start'|'complete'){try{await http.post(`/examinations/orders/${row.id}/${action}`,{version:row.version});await load();ElMessage.success('检查状态已更新')}catch(error){failure(error,'检查状态更新失败')}}
async function createReport(){if(!selected.value||!report.value.findings.trim())return;try{created.value=(await http.post(`/examinations/orders/${selected.value.id}/reports`,report.value)).data.data;ElMessage.success('检查报告草稿已创建')}catch(error){failure(error,'创建检查报告失败')}}
async function sign(){if(!created.value)return;try{await http.post(`/diagnostic-reports/${created.value.id}/sign`,{version:created.value.version});await load();ElMessage.success('检查报告已签署')}catch(error){failure(error,'签署检查报告失败')}}
onMounted(load);
</script>

<template>
  <section class="workbench">
    <header><div><h1>检查工作台</h1><p>仅展示当前检查岗位获得授权的执行范围。</p></div><el-button @click="load">刷新</el-button></header>
    <el-table :data="orders" @row-click="selected=$event"><el-table-column prop="orderNo" label="申请单号"/><el-table-column prop="status" label="状态"/><el-table-column label="操作"><template #default="scope"><el-button v-if="can('hospital:clinical:examination:accept')&&scope.row.status==='PLACED'" link type="primary" @click.stop="move(scope.row,'accept')">接单</el-button><el-button v-if="can('hospital:clinical:examination:start')&&scope.row.status==='ACCEPTED'" link type="primary" @click.stop="move(scope.row,'start')">开始</el-button><el-button v-if="can('hospital:clinical:examination:complete')&&scope.row.status==='IN_PROGRESS'" link type="success" @click.stop="move(scope.row,'complete')">完成</el-button><el-button v-if="can('hospital:clinical:examination:report:create')&&scope.row.status==='COMPLETED'" link type="success" @click.stop="selected=scope.row">撰写报告</el-button></template></el-table-column></el-table>
    <article v-if="can('hospital:clinical:examination:report:create')&&selected?.status==='COMPLETED'" class="panel"><h2>检查报告</h2><el-form label-position="top"><el-form-item label="所见"><el-input v-model="report.findings" type="textarea"/></el-form-item><el-form-item label="印象"><el-input v-model="report.impression" type="textarea"/></el-form-item><el-form-item label="结论"><el-input v-model="report.conclusion" type="textarea"/></el-form-item><el-button type="primary" @click="createReport">创建报告草稿</el-button><el-button v-if="created" type="success" @click="sign">签署报告</el-button></el-form></article>
  </section>
</template>

<style scoped>
.workbench{display:grid;gap:16px}.workbench header{display:flex;justify-content:space-between}.panel{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:16px}
</style>
