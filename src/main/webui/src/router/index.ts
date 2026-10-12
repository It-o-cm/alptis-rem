import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import type { Component } from 'vue';
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
import EventsView from '../views/EventsView.vue';
import HeldEventsView from '../views/HeldEventsView.vue';
import CalculationsView from '../views/CalculationsView.vue';
import ClaimsView from '../views/ClaimsView.vue';
import ValidationView from '../views/ValidationView.vue';
import GroupingsView from '../views/GroupingsView.vue';
import PayeeRulesView from '../views/PayeeRulesView.vue';
import ExternalCodesView from '../views/ExternalCodesView.vue';
import RulesView from '../views/RulesView.vue';
import FamiliesView from '../views/FamiliesView.vue';
import AdvanceAgreementsView from '../views/AdvanceAgreementsView.vue';
import DiscountsView from '../views/DiscountsView.vue';
import EnrollmentsView from '../views/EnrollmentsView.vue';
import PeriodsView from '../views/PeriodsView.vue';
import ControlsView from '../views/ControlsView.vue';
import AdjustmentsView from '../views/AdjustmentsView.vue';
import DistributorAccountView from '../views/DistributorAccountView.vue';
import PaymentView from '../views/PaymentView.vue';
import BordereauxView from '../views/BordereauxView.vue';
import DocumentTemplatesView from '../views/DocumentTemplatesView.vue';
import TaxCertificatesView from '../views/TaxCertificatesView.vue';
import CutoffView from '../views/CutoffView.vue';
import AccountingView from '../views/AccountingView.vue';
import ReconciliationView from '../views/ReconciliationView.vue';
import FluxView from '../views/FluxView.vue';
import ExportsView from '../views/ExportsView.vue';
import FormatsView from '../views/FormatsView.vue';
import SimulationView from '../views/SimulationView.vue';
import ParameterTransferView from '../views/ParameterTransferView.vue';
import RepriseView from '../views/RepriseView.vue';
import DashboardsView from '../views/DashboardsView.vue';
import CommissionSearchView from '../views/CommissionSearchView.vue';
import GroupView from '../views/GroupView.vue';
import MarginView from '../views/MarginView.vue';
import IndicatorsView from '../views/IndicatorsView.vue';
import SupervisionView from '../views/SupervisionView.vue';
import PurgeView from '../views/PurgeView.vue';
import FdvCollaboratorsView from '../views/FdvCollaboratorsView.vue';
import FdvMeasuresView from '../views/FdvMeasuresView.vue';
import FdvObjectivesView from '../views/FdvObjectivesView.vue';
import FdvPlanView from '../views/FdvPlanView.vue';
import FdvValidationView from '../views/FdvValidationView.vue';
import FdvCommercialView from '../views/FdvCommercialView.vue';
import { allNavItems } from '../presentation/navigation';

// Screens delivered so far use a real component; the others are empty titled placeholders.
const REAL_VIEWS: Record<string, Component> = {
  periods: PeriodsView,
  controls: ControlsView,
  adjustments: AdjustmentsView,
  payment: PaymentView,
  statements: BordereauxView,
  documentTemplates: DocumentTemplatesView,
  taxCertificates: TaxCertificatesView,
  cutoff: CutoffView,
  accounting: AccountingView,
  reconciliation: ReconciliationView,
  flows: FluxView,
  exports: ExportsView,
  formats: FormatsView,
  simulation: SimulationView,
  paramTransfer: ParameterTransferView,
  reprise: RepriseView,
  dashboards: DashboardsView,
  commissionSearch: CommissionSearchView,
  groupView: GroupView,
  margin: MarginView,
  indicators: IndicatorsView,
  supervision: SupervisionView,
  purge: PurgeView,
  fdvCollaborators: FdvCollaboratorsView,
  fdvMeasures: FdvMeasuresView,
  fdvObjectives: FdvObjectivesView,
  fdvPlan: FdvPlanView,
  fdvValidation: FdvValidationView,
  fdvCommercial: FdvCommercialView,
  distributor: DistributorAccountView,
  imports: ImportsView,
  mapping: MappingView,
  distributorSearch: DistributorsView,
  groupings: GroupingsView,
  contractSearch: ContractsView,
  products: ProductsView,
  qualityIndicators: QualityIndicatorsView,
  bankAccounts: BankAccountsView,
  transfers: TransfersView,
  manualEvent: ManualEventView,
  events: EventsView,
  heldEvents: HeldEventsView,
  calculations: CalculationsView,
  claims: ClaimsView,
  validation: ValidationView,
  payeeRules: PayeeRulesView,
  externalCodes: ExternalCodesView,
  rules: RulesView,
  families: FamiliesView,
  advance: AdvanceAgreementsView,
  discounts: DiscountsView,
  enrollments: EnrollmentsView,
  adminEntities: EntityParamsView,
  adminRoleMappings: RoleMappingsView,
  adminAudit: AuditView,
};

const navRoutes: RouteRecordRaw[] = allNavItems().map((item) => ({
  path: item.path,
  name: item.name,
  component: REAL_VIEWS[item.name],
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
