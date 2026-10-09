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
import PeriodsView from '../views/PeriodsView.vue';
import ControlsView from '../views/ControlsView.vue';
import AdjustmentsView from '../views/AdjustmentsView.vue';
import DistributorAccountView from '../views/DistributorAccountView.vue';
import PaymentView from '../views/PaymentView.vue';
import BordereauxView from '../views/BordereauxView.vue';
import CutoffView from '../views/CutoffView.vue';
import AccountingView from '../views/AccountingView.vue';
import FluxView from '../views/FluxView.vue';
import ExportsView from '../views/ExportsView.vue';
import FormatsView from '../views/FormatsView.vue';
import SimulationView from '../views/SimulationView.vue';
import ParameterTransferView from '../views/ParameterTransferView.vue';
import RepriseView from '../views/RepriseView.vue';
import DashboardsView from '../views/DashboardsView.vue';
import CommissionSearchView from '../views/CommissionSearchView.vue';
import GroupView from '../views/GroupView.vue';
import IndicatorsView from '../views/IndicatorsView.vue';
import SupervisionView from '../views/SupervisionView.vue';
import PurgeView from '../views/PurgeView.vue';
import { allNavItems } from '../presentation/navigation';

// Screens delivered so far use a real component; the others are empty titled placeholders.
const REAL_VIEWS: Record<string, unknown> = {
  periods: PeriodsView,
  controls: ControlsView,
  adjustments: AdjustmentsView,
  payment: PaymentView,
  statements: BordereauxView,
  cutoff: CutoffView,
  accounting: AccountingView,
  flows: FluxView,
  exports: ExportsView,
  formats: FormatsView,
  simulation: SimulationView,
  paramTransfer: ParameterTransferView,
  reprise: RepriseView,
  dashboards: DashboardsView,
  commissionSearch: CommissionSearchView,
  groupView: GroupView,
  indicators: IndicatorsView,
  supervision: SupervisionView,
  purge: PurgeView,
  distributor: DistributorAccountView,
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
