<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import patient from '../api/patient';
type Queue = { encounterNo:string; queueNo:string; status:string; roomName?:string; doctorName?:string; departmentName?:string; aheadCount:number };
const router=useRouter(), rows=ref<Queue[]>([]), error=ref(''); const labels:Record<string,string>={WAITING:'候诊中',CALLED:'已叫号，请前往诊室',IN_SERVICE:'接诊中',DONE:'本次就诊已完成',SKIPPED:'已过号，请联系工作人员',CANCELLED:'已取消',NO_SHOW:'未到诊'}; let timer=0;
async function load(){try{rows.value=(await patient.get('/patient/visits/queues')).data.data;error.value='';}catch{error.value='无法读取候诊状态，请先登录患者中心。';}}
onMounted(()=>{if(!localStorage.getItem('hospital.patient.access')){void router.replace('/patient?returnTo=/visit-queue');return;}void load();timer=window.setInterval(load,10000);});onBeforeUnmount(()=>clearInterval(timer));
</script>
<template><section class="page"><h1>我的候诊</h1><p v-if="error">{{error}}</p><article v-for="row in rows" :key="row.encounterNo" class="detail"><h2>候诊号 {{row.queueNo}}</h2><p class="state">{{labels[row.status]||row.status}}</p><p>{{row.departmentName}} · {{row.doctorName}} · {{row.roomName||'诊室待安排'}}</p><p v-if="row.status==='WAITING'">您前方还有 {{row.aheadCount}} 位患者；请留意叫号，不承诺精确等待时间。</p><p v-else>请以医院现场指引为准。</p></article><p v-if="!rows.length&&!error">暂无候诊记录。</p></section></template>
