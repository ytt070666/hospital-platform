<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { request } from '../../api/http';
const items = ref<any[]>([]), error = ref('');
async function load() { try { items.value = (await request<any>({ url: '/public/departments' })).data; } catch { error.value = '加载失败'; } }
function book(id: number) { uni.navigateTo({ url: '/pages/booking/index?departmentId=' + id }); }
onMounted(load);
</script>
<template><view class="page"><view v-if="error">{{ error }}</view><view v-else-if="!items.length">暂无已发布科室</view><view v-for="item in items" :key="item.id" class="card"><text class="name">{{ item.name }}</text><text>{{ item.introduction||'科室介绍待完善' }}</text><text>公开医生：{{ item.doctorCount }}</text><button @click="book(item.id)">查看出诊并预约</button></view></view></template>
<style scoped>.page{padding:28rpx}.card{display:flex;flex-direction:column;gap:12rpx;padding:28rpx;background:#fff;border-radius:16rpx;margin-bottom:18rpx;color:#64748b}.name{font-size:34rpx;color:#17324d;font-weight:700}</style>
