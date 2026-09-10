<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { paymentApi,formatMoney,type RegistrationOrder,type RefundOrder } from '../../api/payment';
const orders=ref<RegistrationOrder[]>([]),refunds=ref<RefundOrder[]>([]),loading=ref(false),error=ref('');
const labels:Record<string,string>={PENDING_PAYMENT:'待支付',PAID:'已支付',CLOSED:'已关闭',CREATED:'处理中',PENDING:'处理中',SUCCESS:'退款成功',FAILED:'退款失败'};
async function load(){loading.value=true;error.value='';try{orders.value=(await paymentApi.registrations()).data.records;refunds.value=(await paymentApi.refunds()).data.records;}catch{error.value='加载缴费信息失败，请稍后重试';}finally{loading.value=false;}}
onShow(()=>void load());
</script>
<template><view class="page"><view class="card"><text class="title">我的缴费</text><text class="note">挂号、支付和退款进度以服务端状态为准</text><button size="mini" :loading="loading" @click="load">刷新</button><text v-if="error" class="error">{{error}}</text></view><view v-if="!orders.length&&!loading" class="card"><text>暂无缴费订单</text></view><view v-for="order in orders" :key="order.id" class="card"><text class="strong">{{order.orderNo}}</text><text>挂号费：{{formatMoney(order.amountCent,order.currency)}} · {{labels[order.status]||order.status}}</text><text>支付截止：{{order.paymentDeadline}}</text><text v-if="order.refundedAmountCent">已退款：{{formatMoney(order.refundedAmountCent,order.currency)}}，剩余可退：{{formatMoney(order.remainingRefundableCent,order.currency)}}</text></view><text class="section">退款进度</text><view v-if="!refunds.length" class="card"><text>暂无退款记录</text></view><view v-for="refund in refunds" :key="refund.id" class="card"><text class="strong">{{refund.refundNo}}</text><text>{{formatMoney(refund.amountCent,refund.currency)}} · {{labels[refund.status]||refund.status}}</text><text>原因：{{refund.reasonCode}}</text></view></view></template>
<style scoped>.page{padding:28rpx}.card{display:flex;flex-direction:column;gap:16rpx;background:#fff;border-radius:16rpx;padding:28rpx;margin-bottom:20rpx}.title{font-size:38rpx;font-weight:700}.strong{font-weight:700}.note{color:#64748b}.section{display:block;font-size:32rpx;font-weight:700;margin:28rpx 0 18rpx}.error{color:#b91c1c}</style>
