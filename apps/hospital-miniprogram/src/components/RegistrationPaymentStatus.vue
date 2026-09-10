<script setup lang="ts">
import {ref,watch} from 'vue';
import {paymentApi,formatMoney,type RegistrationOrder} from '../api/payment';
const props=defineProps<{appointmentId:number;status:string;refreshAt:number}>();
const order=ref<RegistrationOrder|null>(null),error=ref(''),busy=ref(false),testAvailable=ref(false),paymentTip=ref('');
const labels={PENDING_PAYMENT:'待支付',PAID:'已结清',CLOSED:'已关闭'};
async function load(){if(busy.value)return;busy.value=true;error.value='';try{
  try{order.value=(await paymentApi.order(props.appointmentId)).data;}
  catch(e:unknown){if((e as {statusCode?:number}).statusCode!==404||props.status!=='BOOKED')throw e;order.value=(await paymentApi.ensure(props.appointmentId)).data;}
  testAvailable.value=(await paymentApi.capabilities()).data.availableChannels.includes('TEST');
}catch(e:unknown){const response=e as {statusCode?:number;data?:{code:string}};if(response.data?.code==='PRICE_NOT_CONFIGURED')error.value='医院尚未配置有效挂号费，请联系医院。';else if(response.statusCode!==404)error.value='费用状态读取失败，请刷新重试。';}finally{busy.value=false;}}
async function createTestPayment(){if(!order.value)return;busy.value=true;paymentTip.value='';try{const created=await paymentApi.create(order.value.id,`${Date.now()}-${Math.floor(Math.random()*1_000_000_000)}`,'TEST','TEST');paymentTip.value=`已创建测试支付单 ${created.data.paymentNo}，等待测试回调确认。`;}catch{paymentTip.value='测试支付单创建失败，请刷新后重试。';}finally{busy.value=false;}}
watch(()=>[props.appointmentId,props.status,props.refreshAt],()=>{void load();},{immediate:true});
</script>
<template><view v-if="order||error"><text>挂号费用</text><view v-if="order"><text>{{formatMoney(order.amountCent,order.currency)}}</text><text>支付状态：{{labels[order.status]}}</text><text v-if="order.amountCent===0">零元挂号，无需支付。</text><template v-else-if="order.status==='PENDING_PAYMENT'"><text v-if="testAvailable">当前为开发测试环境，可创建测试支付单；真实支付渠道尚未接入。</text><text v-else>当前没有可用支付渠道，请联系医院。</text><button v-if="testAvailable" :disabled="busy" @click="createTestPayment">创建测试支付单</button></template><text v-if="paymentTip">{{paymentTip}}</text></view><text v-if="error">{{error}}</text><button :disabled="busy" @click="load">刷新费用状态</button></view></template>
