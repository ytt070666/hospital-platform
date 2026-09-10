<script setup lang="ts">
import { onBeforeUnmount,onMounted,ref,watch } from 'vue';
import { paymentApi,formatMoney,type RegistrationOrder } from '../api/payment';
const props=defineProps<{appointmentId:number;status:string}>();
const order=ref<RegistrationOrder|null>(null),error=ref(''),busy=ref(false),testAvailable=ref(false),paymentTip=ref('');
const labels={PENDING_PAYMENT:'待支付',PAID:'已结清',CLOSED:'已关闭'};
async function load(){if(busy.value)return;busy.value=true;error.value='';const id=props.appointmentId;try{
  let value:RegistrationOrder;
  try{value=(await paymentApi.order(id)).data.data;}
  catch(e:unknown){if((e as {response?:{status:number}}).response?.status!==404||props.status!=='BOOKED')throw e;value=(await paymentApi.ensure(id)).data.data;}
  if(id===props.appointmentId)order.value=value;
  const capability=await paymentApi.capabilities();testAvailable.value=capability.data.data.availableChannels.includes('TEST');
}catch(e:unknown){const response=(e as {response?:{status:number;data?:{code:string}}}).response;
  if(response?.data?.code==='PRICE_NOT_CONFIGURED')error.value='医院尚未配置有效挂号费，请联系医院。';
  else if(response?.status!==404)error.value='费用状态读取失败，请刷新重试。';
}finally{busy.value=false;}}
async function createTestPayment(){if(!order.value)return;busy.value=true;paymentTip.value='';try{const created=await paymentApi.create(order.value.id,crypto.randomUUID(),'TEST','TEST');paymentTip.value=`已创建测试支付单 ${created.data.data.paymentNo}，等待测试回调确认。`;}catch{paymentTip.value='测试支付单创建失败，请刷新后重试。';}finally{busy.value=false;}}
function resume(){if(document.visibilityState==='visible')void load();}
watch(()=>[props.appointmentId,props.status],()=>{order.value=null;void load();},{immediate:true});
onMounted(()=>document.addEventListener('visibilitychange',resume));onBeforeUnmount(()=>document.removeEventListener('visibilitychange',resume));
</script>
<template><section v-if="order||error" aria-label="挂号费用"><h2>挂号费用</h2><template v-if="order"><p>{{formatMoney(order.amountCent,order.currency)}}</p><p>支付状态：{{labels[order.status]}}</p><p v-if="order.amountCent===0">零元挂号，无需发起支付。</p><template v-else-if="order.status==='PENDING_PAYMENT'"><p v-if="testAvailable">当前为开发测试环境，可创建测试支付单；微信和支付宝真实渠道尚未接入。</p><p v-else>当前没有可用支付渠道，请联系医院。</p><button v-if="testAvailable" :disabled="busy" @click="createTestPayment">创建测试支付单</button></template><p v-if="paymentTip" role="status">{{paymentTip}}</p></template><p v-if="error" role="alert">{{error}}</p><button :disabled="busy" @click="load">刷新费用状态</button></section></template>
