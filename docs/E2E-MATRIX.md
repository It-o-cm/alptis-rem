# Matrice de traçabilité des tests de bout en bout

Généré par `src/test/e2e/scripts/traceability.mjs` (ne pas éditer à la main). Les listes attendues
sont dérivées des sources : `screen-routes.json` (73 écrans), le routeur Vue, la section 8 (exigences),
la section 13 (cas de recette) et la section 4 de `TESTS-E2E.md` (parcours).

| Dimension | Attendus | Couverts | Manquants |
| --- | --- | --- | --- |
| Écrans (routes) | 56 | 56 | — |
| Exigences §8 | 135 | 135 | — |
| Cas de recette §13 | 70 | 70 | — |
| Parcours §4 | 135 | 128 | ADM-02, ADM-04, FDV-03, FDV-07, FDV-10, FIN-09, HIS-01 |
| Couples rôle-action | — | 23 | — |

Écrans couverts (`@screen`) : accounting, adjustments, advance, alignment, audit, bank-accounts, bordereaux, calculations, claims, commissionSearch, contract-search, controls, cutoff, dashboards, demo, discounts, distributor, distributor-account, distributor-search, documentTemplates, enrollments, entities, events, exports, external-codes, families, fdvCollaborators, fdvCommercial, fdvMeasures, fdvObjectives, fdvPlan, fdvValidation, flows, formats, groupView, groupings, heldEvents, imports, indicators, manualEvent, mapping, margin, menu, paramTransfer, payee-rules, payment, periods, production-hierarchy, products, purge, quality-indicators, reconciliation, reprise, role-mappings, rules, shell, simulation, statements, states, supervision, taxCertificates, transfers, validation

Nombre de fichiers de tests : 50
Nombre de tests : 322
