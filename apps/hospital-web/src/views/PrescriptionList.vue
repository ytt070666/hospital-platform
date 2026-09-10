<script setup lang="ts">
import { onMounted,ref } from 'vue';
import patient from '../api/patient';
type Item={drugName:string;strength:string;dosageForm:string;doseAmount:number;doseUnit:string;routeName:string;frequencyName:string;durationValue:number;durationUnit:string;quantity:number;quantityUnit:string};type Prescription={id:number;prescriptionNo:string;status:string;items:Item[]};
const rows=ref<Prescription[]>([]),error=ref(''),loading=ref(false);
async function load(){loading.value=true;error.value='';try{rows.value=(await patient.get('/patient/prescriptions')).data.data}catch{error.value='无法读取处方，请先登录患者账户。'}finally{loading.value=false}}
onMounted(load);
</script>
<template><section class="page"><h1>我的处方</h1><p>仅展示已签署后的处方；草稿、药师内部备注、库存与安全规则细节不会展示。</p><p v-if="error" role="alert">{{error}}</p><p v-if="loading">正在加载…</p><article v-for="row in rows" :key="row.id" class="card"><h2>{{row.prescriptionNo}} <small>{{row.status}}</small></h2><ul><li v-for="item in row.items" :key="item.drugName">{{item.drugName}} · {{item.dosageForm}} · {{item.strength}}：{{item.doseAmount}}{{item.doseUnit}}，{{item.routeName}}，{{item.frequencyName}}，{{item.durationValue}}{{item.durationUnit}}，共 {{item.quantity}}{{item.quantityUnit}}</li></ul></article><p v-if="!loading&&!rows.length">暂无可查看处方。</p></section></template>
<style scoped>.card{margin:12px 0;padding:16px;border:1px solid #dbe5ec;border-radius:10px}small{color:#0d6186}</style>
