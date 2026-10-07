import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import PlaceholderView from '../views/PlaceholderView.vue';
import { allNavItems } from '../presentation/navigation';

// One route per navigation entry; each leads to an empty page bearing its title.
const navRoutes: RouteRecordRaw[] = allNavItems().map((item) => ({
  path: item.path,
  name: item.name,
  component: PlaceholderView,
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
