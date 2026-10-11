// Presentation helpers for the imports screen. The server names every flow by its pivot code; this
// map turns those codes into the French labels the operator reads, so no technical code shows on
// screen (ADM-03).

/** French labels of the pivot flow codes the upload form offers (section 9.1, ING-08). */
const FLOW_LABELS: Record<string, string> = {
  events: 'Événements',
  coverages: 'Garanties',
  distributors: 'Distributeurs',
  distributor_groups: 'Groupements',
  production_codes: 'Codes de production',
  insurers: 'Assureurs',
  product_ranges: 'Gammes',
  products: 'Produits',
  product_options: 'Options de produit',
  bank_accounts: 'Coordonnées bancaires',
  quality_indicators: 'Indicateurs de qualité',
  portfolio_transfers: 'Cessions de portefeuille',
  replacement_orders: 'Ordres de remplacement',
  payee_rules: 'Règles de versement',
  external_codes: 'Codes externes',
};

/** French label of a pivot flow code, or the raw code when unknown. */
export function flowLabel(code: string): string {
  return FLOW_LABELS[code] ?? code;
}
