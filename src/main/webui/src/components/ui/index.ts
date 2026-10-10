// Shared component layer (ERGONOMIE §3): the home-made socle every screen is
// built from, so the application reads as one system. Import from here, e.g.
// `import { DataTable, FormField } from '../components/ui';`.

export { default as AppButton } from './AppButton.vue';
export { default as AppCard } from './AppCard.vue';
export { default as AppForm } from './AppForm.vue';
export { default as AppMessage } from './AppMessage.vue';
export { default as ConfirmDialog } from './ConfirmDialog.vue';
export { default as DataTable } from './DataTable.vue';
export { default as DetailList } from './DetailList.vue';
export { default as DetailRow } from './DetailRow.vue';
export { default as FilterBar } from './FilterBar.vue';
export { default as FormField } from './FormField.vue';
export { default as PageHeading } from './PageHeading.vue';
export type { Column } from './DataTable.vue';
