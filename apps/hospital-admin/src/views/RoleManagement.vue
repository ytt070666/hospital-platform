<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import http from '../api/http';
import { useAuthStore } from '../stores/auth';
import type { ApiResponse } from '../types';

type RoleRow = Record<string, unknown> & { id: number; role_code: string; role_name: string; data_scope: string };
type PermissionRow = Record<string, unknown> & { id: number; permission_code: string; permission_name: string };
const auth = useAuthStore();
const rows = ref<RoleRow[]>([]), permissions = ref<PermissionRow[]>([]), loading = ref(false), createVisible = ref(false), permissionVisible = ref(false), selected = ref<RoleRow | null>(null), permissionIds = ref<number[]>([]);
const form = ref({ roleCode: '', roleName: '', dataScope: 'SELF' });
async function load() { loading.value = true; try { const response = await http.get<ApiResponse<RoleRow[]>>('/admin/roles'); rows.value = response.data.data; } finally { loading.value = false; } }
async function createRole() { await http.post('/admin/roles', form.value); ElMessage.success('角色已创建'); createVisible.value = false; form.value = { roleCode: '', roleName: '', dataScope: 'SELF' }; await load(); }
async function openPermissions(row: RoleRow) { selected.value = row; const [permissionResponse, selectedResponse] = await Promise.all([http.get<ApiResponse<PermissionRow[]>>('/admin/permissions'), http.get<ApiResponse<number[]>>(`/admin/roles/${row.id}/permissions`)]); permissions.value = permissionResponse.data.data; permissionIds.value = selectedResponse.data.data; permissionVisible.value = true; }
async function savePermissions() { if (!selected.value) return; await http.put(`/admin/roles/${selected.value.id}/permissions`, { permissionIds: permissionIds.value }); ElMessage.success('权限已保存'); permissionVisible.value = false; }
onMounted(load);
</script>
<template><section><div class="page-heading"><h1>角色管理</h1><el-button v-if="auth.session?.permissions.includes('system:role:create')" type="primary" @click="createVisible=true">新建角色</el-button></div><el-table :data="rows" v-loading="loading" border><el-table-column prop="role_code" label="角色编码"/><el-table-column prop="role_name" label="角色名称"/><el-table-column prop="data_scope" label="数据范围"/><el-table-column v-if="auth.session?.permissions.includes('system:role:update')" label="操作" width="120"><template #default="{row}"><el-button text type="primary" @click="openPermissions(row)">权限配置</el-button></template></el-table-column></el-table>
<el-dialog v-model="createVisible" title="新建角色" width="420px"><el-form label-position="top"><el-form-item label="角色编码"><el-input v-model="form.roleCode"/></el-form-item><el-form-item label="角色名称"><el-input v-model="form.roleName"/></el-form-item><el-form-item label="数据范围"><el-select v-model="form.dataScope"><el-option v-for="scope in ['SELF','DEPARTMENT','DEPT_AND_CHILDREN','ORGANIZATION','CUSTOM','ALL']" :key="scope" :label="scope" :value="scope"/></el-select></el-form-item></el-form><template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" @click="createRole">保存</el-button></template></el-dialog>
<el-dialog v-model="permissionVisible" title="权限配置" width="560px"><el-checkbox-group v-model="permissionIds"><el-checkbox v-for="permission in permissions" :key="permission.id" :value="permission.id">{{ permission.permission_name }}（{{ permission.permission_code }}）</el-checkbox></el-checkbox-group><template #footer><el-button @click="permissionVisible=false">取消</el-button><el-button type="primary" @click="savePermissions">保存</el-button></template></el-dialog></section></template>
