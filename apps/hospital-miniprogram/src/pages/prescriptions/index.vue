<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { request } from '../../api/http';
type Item={drugName:string;strength:string;dosageForm:string;doseAmount:number;doseUnit:string;routeName:string;frequencyName:string;durationValue:number;durationUnit:string;quantity:number;quantityUnit:string};type Row={id:number;prescriptionNo:string;status:string;items:Item[]};
const rows=ref<Row[]>([]),error=ref(''),loading=ref(false);
async function load(){loading.value=true;error.value='';try{rows.value=(await request<{data:Row[]}>({url:'/patient/prescriptions'})).data}catch{error.value='无法读取处方，请先登录患者账户'}finally{loading.value=false}}
onShow(load);
</script>
<template><view class="page"><view class="card"><text class="title">我的处方</text><text class="hint">只显示已签署后的处方，不显示草稿、药房内部备注、库存或安全规则细节。</text><text v-if="loading">正在加载…</text><text v-else-if="error" class="error">{{error}}</text><button v-if="error" @click="load">重试</button><text v-else-if="!rows.length">暂无可查看处方</text><view v-for="row in rows" :key="row.id" class="row"><text>{{row.prescriptionNo}} · {{row.status}}</text><text v-for="item in row.items" :key="item.drugName">{{item.drugName}} · {{item.strength}} · {{item.doseAmount}}{{item.doseUnit}} · {{item.routeName}} · {{item.frequencyName}} · {{item.quantity}}{{item.quantityUnit}}</text></view></view></view></template>
<style scoped>.page{padding:28rpx}.card,.row{display:flex;flex-direction:column;gap:14rpx;background:#fff;border-radius:16rpx;padding:28rpx}.row{border-top:1px solid #eef2f5;border-radius:0;padding:20rpx 0}.title{font-size:40rpx;font-weight:700}.hint{font-size:26rpx;color:#607080}.error{color:#b45309}</style>
