# Matrice de traçabilité des tests de bout en bout

Généré par `src/test/e2e/scripts/traceability.mjs` (ne pas éditer à la main).

Étapes cochées dans `PLAN.md` : 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12

| Dimension | Couverts |
| --- | --- |
| Écrans (`@screen`) | accounting, adjustments, advance, audit, bank-accounts, contract-search, controls, cutoff, distributor-account, distributor-search, enrollments, entities, exports, external-codes, families, flows, formats, imports, manualEvent, mapping, paramTransfer, payee-rules, payment, periods, production-hierarchy, products, quality-indicators, reprise, role-mappings, rules, shell, simulation, statements, transfers |
| Exigences (`@req`) | 5, ADM-04, ADM-05, BRD-01, BRD-02, BRD-06, BRD-07, BRD-12, CPT-02, CPT-04, CPT-05, CPT-06, CPT-07, CTL-01, CTL-02, CTL-05, FIN-02, FIN-03, FIN-04, FIN-05, FIN-09, ING-01, ING-02, ING-05, ING-07, ING-10, ING-11, PAR-01, PAR-02, PAR-03, PAR-04, PAR-05, PAR-09, PAR-10, PAR-12, PAR-13, PAR-15, PAY-01, PAY-02, PAY-03, PAY-06, PAY-07, REF-01, REF-02, REF-03, REF-04, REF-05, REF-06, REF-07, REF-09, REG-01, REG-03, REP-04, WKF-01, WKF-04 |
| Cas de recette (`@cr`) | CR-68, CR-70 |
| Parcours (`@path`) | ADM-01, BOOT-01, CR-70, INT-11, INT-13, PAR-01 |
| Couples rôle-action (`@role-action`) | BUSINESS_ADMIN-approve-adjustment, BUSINESS_ADMIN-import-plan, FINANCE_CONTROLLER-record-bank-result, FINANCE_CONTROLLER-validate-lot, OPERATOR-advance-period, OPERATOR-block-account, OPERATOR-compensate-accounts, OPERATOR-create-off-cycle, OPERATOR-decide-anomaly, OPERATOR-extract-accounting, OPERATOR-import-opening-balance, OPERATOR-prepare-lot, OPERATOR-produce-export, OPERATOR-publish-statement, OPERATOR-rectify-statement, OPERATOR-run-simulation, OPERATOR-submit-adjustment, VIEWER-read-periods |

Nombre de fichiers de tests : 30
Nombre de tests : 96
