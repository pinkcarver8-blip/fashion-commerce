// 화면 코드 검사(A4 정적 분석). 의존 방향은 ADR-009 결정 1: 앱 → 패키지 한 방향, store는 관리자 코드를 쓰지 않는다.
import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/** 가져오기 금지 규칙을 만든다. */
const forbid = (patterns) => ({
  'no-restricted-imports': ['error', { patterns }],
});

const NO_ADMIN = {
  group: ['@commerce/admin', '@commerce/admin/*', '@commerce/api-client/admin', '**/admin/**'],
  message: 'store는 관리자 앱과 관리자용 API 클라이언트를 가져오지 않는다(ADR-009).',
};
const NO_STORE = {
  group: ['@commerce/store', '@commerce/store/*', '@commerce/api-client/store', '**/store/**'],
  message: 'admin은 고객 앱과 고객용 API 클라이언트를 가져오지 않는다(ADR-009).',
};
const NO_APPS = {
  group: ['@commerce/store', '@commerce/store/*', '@commerce/admin', '@commerce/admin/*'],
  message: '공용 패키지는 앱을 가져오지 않는다(ADR-009).',
};
const NO_API_IN_UI = {
  group: ['@commerce/api-client', '@commerce/api-client/*'],
  message: '화면 부품(ui)은 서버를 부르지 않는다(ADR-009).',
};

export default tseslint.config(
  { ignores: ['**/dist/**', '**/node_modules/**'] },
  js.configs.recommended,
  ...tseslint.configs.strict,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: { globals: globals.browser },
    plugins: { 'react-hooks': reactHooks },
    rules: reactHooks.configs.recommended.rules,
  },
  { files: ['store/**/*.{ts,tsx}'], rules: forbid([NO_ADMIN]) },
  { files: ['admin/**/*.{ts,tsx}'], rules: forbid([NO_STORE]) },
  { files: ['packages/**/*.{ts,tsx}'], rules: forbid([NO_APPS]) },
  { files: ['packages/ui/**/*.{ts,tsx}'], rules: forbid([NO_APPS, NO_API_IN_UI]) },
);
