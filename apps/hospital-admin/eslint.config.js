import js from '@eslint/js';
import pluginVue from 'eslint-plugin-vue';
import tseslint from 'typescript-eslint';
import vueParser from 'vue-eslint-parser';

export default [
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.vue'],
    languageOptions: { parser: vueParser, parserOptions: { parser: tseslint.parser, extraFileExtensions: ['.vue'] } },
    rules: { 'vue/multi-word-component-names': 'off' }
  },
  // 主数据页在接入可扩展的 Map 型后台字段前保留边界类型，后续可逐步替换为各实体 DTO。
  { files: ['src/views/MasterData.vue', 'src/views/FacilityMasterData.vue', 'src/views/SchedulingWorkbench.vue'], rules: { '@typescript-eslint/no-explicit-any': 'off', 'no-empty': 'off' } },
  { ignores: ['dist', 'node_modules'] }
];
