<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import patient from '../api/patient';
type Profile = { name: string; mobile: string; realNameStatus: string; defaultMemberId: number | null };
type Member = { id: number; relationship: string; name: string; gender: string; birthDate: string | null; idNumber: string | null; usableForAppointment: boolean };
type MemberForm = { relationship: string; name: string; gender: string; birthDate: string; idType: string; idNumber: string; mobile: string };
const mobile = ref(''), code = ref(''), testCode = ref(''), profile = ref<Profile | null>(null), members = ref<Member[]>([]), message = ref('');
const form = ref<MemberForm>({ relationship: 'SELF', name: '', gender: 'UNKNOWN', birthDate: '', idType: '', idNumber: '', mobile: '' }), editingMemberId = ref<number | null>(null);
const route = useRoute(), router = useRouter();
async function load() { profile.value = (await patient.get('/patient/me')).data.data; members.value = (await patient.get('/patient/members')).data.data; }
function returnToBooking(memberId?: number) {
  if (typeof route.query.returnTo !== 'string') return null;
  const target = new URL(route.query.returnTo, window.location.origin);
  if (memberId) target.searchParams.set('memberId', String(memberId));
  return target.pathname + target.search;
}
async function send() { const response = await patient.post('/patient/auth/send-code', { mobile: mobile.value }); testCode.value = response.data.data.testCode ?? ''; message.value = testCode.value ? '开发测试验证码已由后端提供' : '验证码已发送'; }
async function login() { const response = await patient.post('/patient/auth/login', { mobile: mobile.value, code: code.value, clientType: 'WEB_PATIENT' }); localStorage.setItem('hospital.patient.access', response.data.data.accessToken); localStorage.setItem('hospital.patient.refresh', response.data.data.refreshToken); await load(); const target = returnToBooking(); if (target) await router.replace(target); }
function resetForm() { form.value = { relationship: 'CHILD', name: '', gender: 'UNKNOWN', birthDate: '', idType: '', idNumber: '', mobile: '' }; editingMemberId.value = null; }
function edit(member: Member) { form.value = { relationship: member.relationship, name: member.name, gender: member.gender, birthDate: member.birthDate || '', idType: '', idNumber: '', mobile: '' }; editingMemberId.value = member.id; }
async function save() {
  const payload = { ...form.value, birthDate: form.value.birthDate || null, idType: form.value.idType || null, idNumber: form.value.idNumber || null, mobile: form.value.mobile || null };
  let createdId: number | undefined;
  if (editingMemberId.value) await patient.put(`/patient/members/${editingMemberId.value}`, payload);
  else createdId = (await patient.post('/patient/members', payload)).data.data;
  resetForm(); await load(); const target = returnToBooking(createdId); if (target) await router.replace(target);
}
async function setDefault(id: number) { await patient.post(`/patient/members/${id}/default`); await load(); }
async function logout() { const refresh = localStorage.getItem('hospital.patient.refresh'); try { if (refresh) await patient.post('/patient/auth/logout', { refreshToken: refresh }); } finally { localStorage.removeItem('hospital.patient.access'); localStorage.removeItem('hospital.patient.refresh'); profile.value = null; members.value = []; } }
onMounted(() => { if (localStorage.getItem('hospital.patient.access')) void load().catch(logout); });
</script>
<template><section class="page patient"><div v-if="!profile" class="detail"><h1>患者登录</h1><p>使用手机号验证码管理本人及家庭就诊人，并可完成预约挂号。</p><input v-model="mobile" placeholder="手机号"><button @click="send">发送验证码</button><small v-if="testCode">开发验证码：{{ testCode }}</small><input v-model="code" placeholder="验证码"><button @click="login">登录患者中心</button><p>{{ message }}</p></div><template v-else><div class="title"><div><h1>患者中心</h1><p>{{ profile.name }} · {{ profile.mobile }} · 实名状态：{{ profile.realNameStatus }}</p></div><router-link to="/payments">我的缴费</router-link><button @click="logout">退出登录</button></div><div class="detail"><h2>就诊人</h2><article v-for="member in members" :key="member.id" class="member"><strong>{{ member.name }}</strong> · {{ member.relationship }} · {{ member.idNumber || '未绑定证件' }} <button @click="edit(member)">编辑</button> <button v-if="profile.defaultMemberId !== member.id && member.usableForAppointment" @click="setDefault(member.id)">设为默认</button><em v-else>默认就诊人</em></article></div><form class="detail" @submit.prevent="save"><h2>{{ editingMemberId ? '编辑就诊人' : '添加就诊人' }}</h2><select v-model="form.relationship"><option value="SELF">本人</option><option value="CHILD">儿童</option><option value="PARENT">父母</option><option value="SPOUSE">配偶</option><option value="OTHER">其他</option></select><input v-model="form.name" placeholder="姓名"><select v-model="form.gender"><option value="UNKNOWN">未知</option><option value="MALE">男</option><option value="FEMALE">女</option></select><input v-model="form.birthDate" type="date"><select v-model="form.idType"><option value="">未绑定证件</option><option value="CN_ID">居民身份证</option><option value="PASSPORT">护照</option></select><input v-model="form.idNumber" placeholder="证件号码（仅加密保存）"><button>{{ editingMemberId ? '保存修改' : '保存就诊人' }}</button><button v-if="editingMemberId" type="button" @click="resetForm">取消编辑</button></form></template></section></template>
<style scoped>.patient{max-width:760px}.detail{margin:18px 0}.detail input,.detail select{display:block;width:100%;margin:10px 0}.member{padding:12px 0;border-bottom:1px solid #e5edf2}.member button{float:right}em{float:right;color:#176b92}small{display:block;color:#176b92}</style>
