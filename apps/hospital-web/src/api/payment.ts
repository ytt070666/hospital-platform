import patient from './patient';
export type RegistrationOrder={id:number;orderNo:string;appointmentId:number;amountCent:number;currency:string;status:'PENDING_PAYMENT'|'PAID'|'CLOSED';refundedAmountCent:number;remainingRefundableCent:number;settlementStatus:string;paymentDeadline:string;paidAt:string|null;version:number};
export type RefundOrder={id:number;refundNo:string;registrationOrderId:number;paymentOrderId:number;appointmentId:number;amountCent:number;currency:string;reasonCode:string;status:string;requestedAt:string|null;successAt:string|null;version:number};
export type PaymentOrder={id:number;paymentNo:string;registrationOrderId:number;provider:string;channel:string;amountCent:number;currency:string;status:'CREATED'|'PENDING'|'SUCCESS'|'FAILED'|'CLOSED';expiresAt:string;successAt:string|null;version:number};
export function formatMoney(cents:number|string,currency='CNY'){const digits=String(cents).replace(/^0+(?=\d)/,'').padStart(3,'0');const value=`${digits.slice(0,-2)}.${digits.slice(-2)}`;return currency==='CNY'?`¥${value.replace(/\B(?=(\d{3})+(?!\d))/g,',')}`:`${value} ${currency}`;}
export const paymentApi={
  order:(id:number)=>patient.get<{data:RegistrationOrder}>(`/patient/appointments/${id}/registration-order`),
  ensure:(id:number)=>patient.post<{data:RegistrationOrder}>(`/patient/appointments/${id}/registration-order`),
  create:(id:number,key:string,provider:string,channel:string)=>patient.post<{data:PaymentOrder}>(`/patient/registration-orders/${id}/payments`,{provider,channel},{headers:{'Idempotency-Key':key}}),
  detail:(id:number)=>patient.get<{data:PaymentOrder}>(`/patient/payments/${id}`),
  query:(id:number)=>patient.post<{data:PaymentOrder}>(`/patient/payments/${id}/query`),
  capabilities:()=>patient.get<{data:{availableChannels:string[];testOnly:boolean}}>('/patient/payment/capabilities'),
  registrations:(page=1,status?:string)=>patient.get<{data:{records:RegistrationOrder[];total:number}}>('/patient/registration-orders',{params:{page,pageSize:20,status}}),
  refunds:(page=1,status?:string)=>patient.get<{data:{records:RefundOrder[];total:number}}>('/patient/refunds',{params:{page,pageSize:20,status}}),
};
