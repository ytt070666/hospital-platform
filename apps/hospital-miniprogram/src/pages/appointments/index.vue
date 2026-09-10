<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { appointmentApi, type Appointment } from '../../api/appointment';
const rows = ref<Appointment[]>([]), status = ref(''), page = ref(1), total = ref(0), loading = ref(false), error = ref('');
const labels: Record<string, string> = { HOLDING: '待确认', BOOKED: '已预约', CANCELLED: '已取消', EXPIRED: '已失效' };
async function load() { loading.value = true; error.value = ''; try { const data = (await appointmentApi.list({ status: (status.value || undefined) as any, page: page.value, pageSize: 10 })).data; rows.value = data.records; total.value = data.total; } catch { error.value = '加载失败，请检查网络后重试'; } finally { loading.value = false; } }
function open(id: number) { uni.navigateTo({ url: '/pages/appointment-detail/index?id=' + id }); }
function filter(value: number) { status.value = ['', 'HOLDING', 'BOOKED', 'CANCELLED', 'EXPIRED'][value]; page.value = 1; void load(); }
function move(delta: number) { page.value += delta; void load(); }
onShow(load);
</script>
<template><view class="page"><view class="card"><text class="title">我的预约</text><picker :range="['全部','待确认','已预约','已取消','已失效']" @change="filter(Number($event.detail.value))"><view>筛选：{{ labels[status]||'全部' }}</view></picker><text v-if="loading">正在加载预约…</text><text v-else-if="error" class="tip">{{ error }}</text><button v-if="error" @click="load">重试</button><text v-else-if="!rows.length">暂无预约记录</text><view v-for="row in rows" :key="row.id" class="row" @click="open(row.id)"><text>{{ row.appointmentNo }}</text><text>{{ labels[row.status] }} · {{ row.memberName||'就诊人' }} · {{ row.doctorName }} · {{ row.departmentName }}</text><text>{{ row.scheduleDate }} {{ row.startTime }}-{{ row.endTime }}</text></view><view class="pagination"><button :disabled="loading||page===1" @click="move(-1)">上一页</button><text>第 {{ page }} 页 · 共 {{ total }} 条</text><button :disabled="loading||page*10>=total" @click="move(1)">下一页</button></view></view></view></template>
<style scoped>.page{padding:28rpx}.card{display:flex;flex-direction:column;gap:20rpx;background:#fff;border-radius:16rpx;padding:28rpx}.title{font-size:40rpx;font-weight:700}.row{display:flex;flex-direction:column;gap:8rpx;padding:20rpx 0;border-bottom:1px solid #eef2f5}.pagination{display:flex;justify-content:space-between;align-items:center;gap:12rpx}.tip{color:#b45309}</style>
