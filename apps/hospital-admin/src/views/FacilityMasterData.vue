<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRoute } from 'vue-router';
import http from '../api/http';

const route = useRoute();
const rows = ref<Record<string, any>[]>([]);
const loading = ref(false);
const error = ref('');
const visible = ref(false);
const editing = ref<Record<string, any> | null>(null);
const form = reactive<Record<string, any>>({});
const type = computed(() => String(route.path.split('/').pop()));
const config = computed(() => ({
  buildings: { title: '楼宇管理', endpoint: '/admin/master/buildings', owner: 'campusId', code: 'buildingCode' },
  floors: { title: '楼层管理', endpoint: '/admin/master/floors', owner: 'buildingId', code: 'floorCode' },
  'outpatient-departments': { title: '门诊科室', endpoint: '/admin/master/outpatient-departments', owner: 'departmentId', code: 'outpatientCode' }
}[type.value]!));
const columns = computed(() => type.value === 'floors' ? ['floorCode', 'name', 'floorNumber', 'published', 'status'] : [config.value.code, 'name', 'published', 'status']);

function reset(row?: Record<string, any>) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, { status: true, published: false, sortOrder: 0, version: row?.version ?? 0 }, row ?? {});
  form[config.value.owner] = row?.[config.value.owner] ?? '';
  form.code = row?.[config.value.code] ?? '';
  form.name = row?.name ?? '';
  if (type.value === 'floors') form.floorNumber = row?.floorNumber ?? 1;
  if (type.value === 'outpatient-departments') form.clinicTypeId = row?.clinicTypeId ?? null;
}
async function load() { loading.value = true; error.value = ''; try { rows.value = (await http.get(config.value.endpoint)).data.data; } catch (reason: any) { error.value = reason.message ?? '加载失败'; } finally { loading.value = false; } }
function open(row?: Record<string, any>) { editing.value = row ?? null; reset(row); visible.value = true; }
async function save() {
  const payload = { ...form, [config.value.owner]: Number(form[config.value.owner]) };
  if (type.value === 'floors') payload.floorNumber = Number(form.floorNumber);
  if (type.value === 'outpatient-departments') payload.clinicTypeId = form.clinicTypeId ? Number(form.clinicTypeId) : null;
  try { if (editing.value) await http.put(`${config.value.endpoint}/${editing.value.id}`, payload); else await http.post(config.value.endpoint, payload); ElMessage.success('已保存'); visible.value = false; await load(); } catch (reason: any) { ElMessage.error(reason.response?.data?.message ?? '保存失败'); }
}
watch(type, load); onMounted(load);
</script>

<template>
  <section>
    <div class="toolbar"><div><h2>{{ config.title }}</h2><p>楼宇、楼层和门诊科室均直接读取医院主数据接口。</p></div><el-button type="primary" @click="open()">新建</el-button></div>
    <el-alert v-if="error" :title="error" type="error" show-icon /><el-skeleton v-else-if="loading" :rows="4" animated /><el-empty v-else-if="!rows.length" description="暂无数据" />
    <el-table v-else :data="rows"><el-table-column v-for="column in columns" :key="column" :prop="column" :label="column" /><el-table-column label="操作"><template #default="scope"><el-button link type="primary" @click="open(scope.row)">编辑</el-button></template></el-table-column></el-table>
    <el-dialog v-model="visible" :title="editing ? '编辑' : '新建' + config.title" width="560px"><el-form label-position="top"><el-form-item :label="`${config.owner}（上级 ID）`"><el-input v-model="form[config.owner]" /></el-form-item><el-form-item label="编码"><el-input v-model="form.code" :disabled="!!editing" /></el-form-item><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item><el-form-item v-if="type === 'floors'" label="楼层序号"><el-input-number v-model="form.floorNumber" /></el-form-item><el-form-item v-if="type === 'outpatient-departments'" label="门诊类型 ID（可选）"><el-input v-model="form.clinicTypeId" /></el-form-item><el-form-item label="说明"><el-input v-model="form.description" type="textarea" /></el-form-item><el-checkbox v-model="form.status">启用</el-checkbox><el-checkbox v-model="form.published">向患者端发布</el-checkbox></el-form><template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
  </section>
</template>

<style scoped>.toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.toolbar h2{margin:0}.toolbar p{color:#718096;margin:6px 0 0}</style>
