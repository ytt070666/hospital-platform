<script setup lang="ts">
/* eslint-disable @typescript-eslint/no-explicit-any */
import { onMounted,ref } from 'vue'; import patient from '../api/patient';
const rows=ref<any[]>([]),active=ref<any>(null),error=ref('');
async function load(){try{rows.value=(await patient.get('/patient/diagnostic-reports')).data.data}catch{error.value='请先登录后查看已发布的检查检验报告'}}
async function detail(id:number){try{active.value=(await patient.get(`/patient/diagnostic-reports/${id}`)).data.data}catch{error.value='无法读取该报告'}}
onMounted(load);
</script>
<template><section><header><div><h1>检查检验报告</h1><p>仅展示已审核、已发布的报告；未审核结果和内部危急规则不会对患者开放。</p></div><button @click="load">刷新</button></header><p v-if="error" class="error">{{error}}</p><div v-for="r in rows" :key="r.id" class="card"><strong>{{r.reportNo}}</strong><span>{{r.type}} · {{r.status}}</span><button @click="detail(r.id)">查看报告</button></div><article v-if="active" class="card"><h2>{{active.reportNo}}</h2><p v-if="active.findings">{{active.findings}}</p><p v-if="active.impression">{{active.impression}}</p><p v-if="active.conclusion">{{active.conclusion}}</p><table v-if="active.labItems?.length"><thead><tr><th>项目</th><th>结果</th><th>单位</th><th>参考范围</th><th>标记</th></tr></thead><tbody><tr v-for="i in active.labItems" :key="i.test_name_snapshot"><td>{{i.test_name_snapshot}}</td><td>{{i.numeric_value??i.text_value}}</td><td>{{i.unit_name_snapshot}}</td><td>{{i.reference_low??i.reference_text}} ~ {{i.reference_high}}</td><td>{{i.abnormal_flag}}</td></tr></tbody></table></article></section></template>
<style scoped>section{max-width:900px;margin:auto;padding:24px}header,.card{display:flex;gap:12px;justify-content:space-between;align-items:center}.card{margin-top:12px;padding:16px;background:#fff;border-radius:12px;flex-wrap:wrap}button{padding:8px 12px;border:0;border-radius:6px;background:#2563eb;color:#fff}.error{color:#b91c1c}table{width:100%;border-collapse:collapse}th,td{padding:8px;border-bottom:1px solid #e5e7eb;text-align:left}</style>
