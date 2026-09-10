import http from './http';
export type FeeRule = { id?:number;ruleCode:string;hospitalId:number;campusId:number|null;departmentId:number|null;clinicTypeId:number|null;doctorTitleId:number|null;amountCent:number;currency:string;effectiveFrom:string;effectiveTo:string|null;status:number;priority:number;version:number };
export type Order = { id:number;orderNo?:string;paymentNo?:string;appointmentId?:number;registrationOrderId?:number;provider?:string;channel?:string;amountCent:number;currency:string;status:string;paymentDeadline?:string;expiresAt?:string;paidAt?:string;successAt?:string;version:number };
export function formatMoney(cents:number|string,currency='CNY') { const digits=String(cents).replace(/^0+(?=\d)/,'').padStart(3,'0');const value=`${digits.slice(0,-2)}.${digits.slice(-2)}`;return currency==='CNY'?`¥${value.replace(/\B(?=(\d{3})+(?!\d))/g,',')}`:`${value} ${currency}`; }
export const paymentApi = {
  fees:()=>http.get('/admin/fee-rules'),
  save:(r:FeeRule)=>r.id?http.put(`/admin/fee-rules/${r.id}`,r):http.post('/admin/fee-rules',r),
  list:(type:string,page:number,status:string)=>http.get(`/admin/${type}`,{params:{page,pageSize:20,status:status||undefined}}),
  detail:(type:string,id:number)=>http.get(`/admin/${type}/${id}`),
  dashboard:()=>http.get('/admin/finance/dashboard'),
  refunds:(page:number,status:string)=>http.get('/admin/refund-orders',{params:{page,pageSize:20,status:status||undefined}}),
  refundDetail:(id:number)=>http.get(`/admin/refund-orders/${id}`),
  retryRefund:(id:number)=>http.post(`/admin/refund-orders/${id}/retry`),
  reconciliation:()=>http.get('/admin/reconciliation-anomalies'),
};
