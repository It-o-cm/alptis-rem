import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import PlaceholderView from '../views/PlaceholderView.vue';
import EntityParamsView from '../views/EntityParamsView.vue';
import RoleMappingsView from '../views/RoleMappingsView.vue';
import AuditView from '../views/AuditView.vue';
import ImportsView from '../views/ImportsView.vue';
import MappingView from '../views/MappingView.vue';
import DistributorsView from '../views/DistributorsView.vue';
import ContractsView from '../views/ContractsView.vue';
import ProductsView from '../views/ProductsView.vue';
import QualityIndicatorsView from '../views/QualityIndicatorsView.vue';
import BankAccountsView from '../views/BankAccountsView.vue';
import TransfersView from '../views/TransfersView.vue';
import ManualEventView from '../views/ManualEventView.vue';
import PayeeRulesView from '../views/PayeeRulesView.vue';
import ExternalCodesView from '../views/ExternalCodesView.vue';
import RulesView from '../views/RulesView.vue';
import FamiliesView from '../views/FamiliesView.vue';
import AdvanceAgreementsView from '../views/AdvanceAgreementsView.vue';
import EnrollmentsView from '../views/EnrollmentsView.vue';
import { allNavItems } from '../presentation/navigation';

// Screens delivered so far use a real component; the others are empty titled placeholders.
const REAL_VIEWS: Record<string, unknown> = {
  imports: ImportsView,
  mapping: MappingView,
  distributorSearch: DistributorsView,
  contractSearch: ContractsView,
  products: ProductsView,
  qualityIndicators: QualityIndicatorsView,
  bankAccounts: BankAccountsView,
  transfers: TransfersView,
  manualEvent: ManualEventView,
  payeeRules: PayeeRulesView,
  externalCodes: ExternalCodesView,
  rules: RulesView,
  families: FamiliesView,
  advance: AdvanceAgreementsView,
  enrollments: EnrollmentsView,
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
