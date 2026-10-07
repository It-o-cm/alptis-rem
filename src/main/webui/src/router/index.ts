import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import PlaceholderView from '../views/PlaceholderView.vue';
import EntityParamsView from '../views/EntityParamsView.vue';
import RoleMappingsView from '../views/RoleMappingsView.vue';
import AuditView from '../views/AuditView.vue';
import { allNavItems } from '../presentation/navigation';

// Screens delivered so far use a real component; the others are empty titled placeholders.
const REAL_VIEWS: Record<string, unknown> = {
  adminEntities: EntityParamsView,
  adminRoleMappings: RoleMappingsView,
  adminAudit: AuditView,
};

const navRoutes: RouteRecordRaw[] = allNavItems().map((item) => ({
  path: item.path,
  name: item.name,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  component: (REAL_VIEWS[item.name] as any) ?? PlaceholderView,
  meta: { title: item.label },
}));

const routes: RouteRecordRaw[] = [
  ...navRoutes,
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});
