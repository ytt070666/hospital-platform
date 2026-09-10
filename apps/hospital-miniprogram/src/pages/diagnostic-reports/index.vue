<script setup lang="ts">
import { onMounted,ref } from 'vue'; import { request } from '../../api/http';
const rows=ref<any[]>([]),detail=ref<any>(null),error=ref('');
async function load(){try{rows.value=(await request<{data:any[]}>({url:'/patient/diagnostic-reports'})).data}catch{error.value='请登录后查看已发布报告'}}
async function show(id:number){try{detail.value=(await request<{data:any}>({url:`/patient/diagnostic-reports/${id}`})).data}catch{error.value='报告不可访问'}}
onMounted(load);
</script>
<template><view class="page"><view class="title">检查检验</view><view class="hint">仅显示已发布的最终报告</view><view v-if="error" class="error">{{error}}</view><view v-for="r in rows" :key="r.id" class="card" @click="show(r.id)"><text>{{r.reportNo}}</text><text>{{r.type}} · {{r.status}}</text></view><view v-if="detail" class="card"><view>{{detail.reportNo}}</view><view>{{detail.findings}}</view><view>{{detail.impression}}</view><view>{{detail.conclusion}}</view></view></view></template>
<style scoped>.page{padding:32rpx}.title{font-size:40rpx;font-weight:600}.hint{margin:12rpx 0;color:#64748b}.card{margin:20rpx 0;padding:24rpx;background:#fff;border-radius:16rpx;display:grid;gap:12rpx}.error{color:#dc2626}</style>
