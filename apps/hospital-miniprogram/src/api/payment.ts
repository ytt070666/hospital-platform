import {request} from './http';
export type RegistrationOrder={id:number;orderNo:string;appointmentId:number;amountCent:number;currency:string;status:'PENDING_PAYMENT'|'PAID'|'CLOSED';refundedAmountCent:number;remainingRefundableCent:number;settlementStatus:string;paymentDeadline:string;paidAt:string|null;version:number};
export type PaymentOrder={id:number;paymentNo:string;registrationOrderId:number;provider:string;amountCent:number;currency:string;status:'CREATED'|'PENDING'|'SUCCESS'|'FAILED'|'CLOSED';expiresAt:string;version:number};
export type RefundOrder={id:number;refundNo:string;registrationOrderId:number;paymentOrderId:number;appointmentId:number;amountCent:number;currency:string;reasonCode:string;status:string;requestedAt:string|null;successAt:string|null;version:number};
export function formatMoney(cents:number|string,currency='CNY'){const digits=String(cents).replace(/^0+(?=\d)/,'').padStart(3,'0');const value=`${digits.slice(0,-2)}.${digits.slice(-2)}`;return currency==='CNY'?`¥${value.replace(/\B(?=(\d{3})+(?!\d))/g,',')}`:`${value} ${currency}`;}
export const paymentApi={
  order:(id:number)=>request<{data:RegistrationOrder}>({url:`/patient/appointments/${id}/registration-order`}),
  ensure:(id:number)=>request<{data:RegistrationOrder}>({url:`/patient/appointments/${id}/registration-order`,method:'POST'}),
  create:(id:number,key:string,provider:string,channel:string)=>request<{data:PaymentOrder}>({url:`/patient/registration-orders/${id}/payments`,method:'POST',header:{'Idempotency-Key':key},data:{provider,channel}}),
  detail:(id:number)=>request<{data:PaymentOrder}>({url:`/patient/payments/${id}`}),
  query:(id:number)=>request<{data:PaymentOrder}>({url:`/patient/payments/${id}/query`,method:'POST'}),
  capabilities:()=>request<{data:{availableChannels:string[];testOnly:boolean}}>({url:'/patient/payment/capabilities'}),
  registrations:(page=1,status?:string)=>request<{data:{records:RegistrationOrder[];total:number}}>({url:'/patient/registration-orders',data:{page,pageSize:20,status}}),
  refunds:(page=1,status?:string)=>request<{data:{records:RefundOrder[];total:number}}>({url:'/patient/refunds',data:{page,pageSize:20,status}}),
};
