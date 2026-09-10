<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import http from '../api/http';
import { useAuthStore } from '../stores/auth';
import type { ApiResponse } from '../types';

type UserRow = Record<string, unknown> & { id: number; username: string; display_name?: string };
type RoleRow = Record<string, unknown> & { id: number; role_code: string; role_name: string };
const auth = useAuthStore();
const rows = ref<UserRow[]>([]), roles = ref<RoleRow[]>([]), loading = ref(false), keyword = ref('');
const createVisible = ref(false), editVisible = ref(false), roleVisible = ref(false), selected = ref<UserRow | null>(null);
const createForm = ref({ username: '', password: '', displayName: '' });
const editName = ref(''), selectedRoleIds = ref<number[]>([]);
const canUpdate = computed(() => auth.session?.permissions.includes('system:user:update') ?? false);
const filteredRows = computed(() => rows.value.filter(row => `${row.username}${row.display_name ?? ''}`.toLowerCase().includes(keyword.value.toLowerCase())));
async function load() { loading.value = true; try { const response = await http.get<ApiResponse<UserRow[]>>('/admin/users'); rows.value = response.data.data; } finally { loading.value = false; } }
async function loadRoles() { const response = await http.get<ApiResponse<RoleRow[]>>('/admin/roles'); roles.value = response.data.data; }
async function createUser() { await http.post('/admin/users', createForm.value); ElMessage.success('用户已创建'); createVisible.value = false; createForm.value = { username: '', password: '', displayName: '' }; await load(); }
function openEdit(row: UserRow) { selected.value = row; editName.value = String(row.display_name ?? ''); editVisible.value = true; }
async function saveEdit() { if (!selected.value) return; await http.put(`/admin/users/${selected.value.id}`, { displayName: editName.value }); ElMessage.success('用户已更新'); editVisible.value = false; await load(); }
async function openRoles(row: UserRow) { selected.value = row; selectedRoleIds.value = []; await loadRoles(); roleVisible.value = true; }
async function saveRoles() { if (!selected.value) return; await http.put(`/admin/users/${selected.value.id}/roles`, { roleIds: selectedRoleIds.value }); ElMessage.success('角色已分配'); roleVisible.value = false; }
onMounted(async () => { await load(); if (canUpdate.value) await loadRoles(); });
</script>
<template><section><div class="page-heading"><h1>用户管理</h1><el-button v-if="auth.session?.permissions.includes('system:user:create')" type="primary" @click="createVisible=true">新建用户</el-button></div><el-input v-model="keyword" placeholder="按账号或姓名查询" clearable class="search"/><el-table :data="filteredRows" v-loading="loading" border><el-table-column prop="username" label="账号"/><el-table-column prop="display_name" label="姓名"/><el-table-column prop="status" label="状态"/><el-table-column v-if="canUpdate" label="操作" width="190"><template #default="{row}"><el-button text type="primary" @click="openEdit(row)">编辑</el-button><el-button text type="primary" @click="openRoles(row)">分配角色</el-button></template></el-table-column></el-table><el-empty v-if="!loading&&!filteredRows.length" description="暂无数据"/>
<el-dialog v-model="createVisible" title="新建用户" width="420px"><el-form label-position="top"><el-form-item label="账号"><el-input v-model="createForm.username"/></el-form-item><el-form-item label="密码"><el-input v-model="createForm.password" type="password" show-password/></el-form-item><el-form-item label="姓名"><el-input v-model="createForm.displayName"/></el-form-item></el-form><template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" @click="createUser">保存</el-button></template></el-dialog>
<el-dialog v-model="editVisible" title="编辑用户" width="420px"><el-form label-position="top"><el-form-item label="姓名"><el-input v-model="editName"/></el-form-item></el-form><template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" @click="saveEdit">保存</el-button></template></el-dialog>
<el-dialog v-model="roleVisible" title="分配角色" width="420px"><el-checkbox-group v-model="selectedRoleIds"><el-checkbox v-for="role in roles" :key="role.id" :value="role.id">{{ role.role_name }}（{{ role.role_code }}）</el-checkbox></el-checkbox-group><template #footer><el-button @click="roleVisible=false">取消</el-button><el-button type="primary" @click="saveRoles">保存</el-button></template></el-dialog></section></template>
