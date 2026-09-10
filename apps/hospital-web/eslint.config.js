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
  // 公共主数据接口在页面层以只读动态字段呈现，DTO 收敛前限制豁免到新增页面。
  { files: ['src/views/Home.vue', 'src/views/CampusList.vue', 'src/views/DepartmentList.vue', 'src/views/DepartmentDetail.vue', 'src/views/DoctorList.vue', 'src/views/DoctorDetail.vue'], rules: { '@typescript-eslint/no-explicit-any': 'off' } },
  { ignores: ['dist', 'node_modules'] }
];
