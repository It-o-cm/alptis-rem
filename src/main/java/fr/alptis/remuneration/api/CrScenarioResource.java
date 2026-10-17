package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.AdvanceAgreementStatus;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.AccountEntry;
import fr.alptis.remuneration.domain.AccountEntryType;
import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.AccountingEvent;
import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.AdvanceDebtHandling;
import fr.alptis.remuneration.domain.AdvanceStatus;
import fr.alptis.remuneration.domain.Agreement;
import fr.alptis.remuneration.domain.PortfolioTransfer;
import fr.alptis.remuneration.domain.TransferStatus;
import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.AgreementType;
import fr.alptis.remuneration.domain.AssignmentRole;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.ConditionOperator;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.DistributorType;
import fr.alptis.remuneration.domain.Insurer;
import fr.alptis.remuneration.domain.InsurerRole;
import fr.alptis.remuneration.domain.OptionType;
import fr.alptis.remuneration.domain.ProductOption;
import fr.alptis.remuneration.domain.SalesChannel;
import fr.alptis.remuneration.domain.TariffType;
import fr.alptis.remuneration.domain.Enrollment;
import fr.alptis.remuneration.domain.EnrollmentStatus;
import fr.alptis.remuneration.domain.ContractStatus;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ImportMapping;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.PayeeRule;
import fr.alptis.remuneration.domain.PayeeRuleShare;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ProductTarget;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.ReplacementOrder;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.QualityIndicatorType;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.RemunerationMode;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.RuleCondition;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.Scale;
import fr.alptis.remuneration.domain.ScaleMeasure;
import fr.alptis.remuneration.domain.ScaleMode;
import fr.alptis.remuneration.domain.ScaleOutput;
import fr.alptis.remuneration.domain.ScaleTier;
import fr.alptis.remuneration.domain.SignatureStatus;
import fr.alptis.remuneration.domain.StaffCategory;
import fr.alptis.remuneration.domain.SurcommissionAllocation;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionStatus;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dev-only loader of a reference recette case's input data for the end-to-end suite (TESTS-E2E.md
 * section 3, outside production). It seeds exactly the product, rule version, coverage, remuneration
 * choice, distributor, production code and events of a case — never the resulting commission lines —
 * so the e2e test can launch the real engine from the calculations screen and read the computed lines
 * and their explanation where a user would. The expected amounts stay in the spec (section 13) and in
 * the test. A single case is planted per call; the case switch is extended as cases are replayed.
 */
@Path("/api/test/cr-scenario")
@UnlessBuildProfile("prod")
public class CrScenarioResource {

    /**
     * Plants a recette case's data for an entity and month.
     *
     * @param crCode     the recette case code (e.g. CR-01)
     * @param entityCode the entity code
     * @param month      the period month (ISO date) the calculation will run
     * @return 204 once seeded, 400 for an unknown case
     */
    @POST
    @Transactional
    public Response seed(@QueryParam("cr") String crCode, @QueryParam("entity") String entityCode,
                         @QueryParam("month") String month) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        LocalDate periodMonth = LocalDate.parse(month);
        period(entity, periodMonth);
        switch (crCode) {
            case "CR-01" -> seedCr01(entity, periodMonth);
            case "CR-02" -> seedCr02(entity, periodMonth);
            case "CR-03" -> seedCr03(entity, periodMonth);
            case "CR-04" -> seedCr04(entity, periodMonth);
            case "CR-05" -> seedCr05(entity, periodMonth);
            case "CR-06" -> seedCr06(entity, periodMonth);
            case "CR-07" -> seedCr07(entity, periodMonth);
            case "CR-08" -> seedCr08(entity, periodMonth);
            case "CR-09" -> seedCr09(entity, periodMonth);
            case "CR-10" -> seedCr10(entity, periodMonth);
            case "CR-11" -> seedCr11(entity, periodMonth);
            case "CR-12" -> seedCr12(entity, periodMonth);
            case "CR-20" -> seedCr20(entity, periodMonth);
            case "CR-21" -> seedCr21(entity, periodMonth);
            case "CR-22" -> seedCr22(entity, periodMonth);
            case "CR-23" -> seedCr23(entity, periodMonth);
            case "CR-24" -> seedCr24(entity, periodMonth);
            case "CR-25" -> seedCr25(entity, periodMonth);
            case "CR-26" -> seedCr26(entity, periodMonth);
            case "CR-27" -> seedCr27(entity, periodMonth);
            case "CR-28" -> seedCr28(entity, periodMonth);
            case "CR-30" -> seedCr30(entity, periodMonth);
            case "CR-31" -> seedCr31(entity, periodMonth);
            case "CR-32" -> seedCr32(entity, periodMonth);
            case "CR-33" -> seedCr33(entity, periodMonth);
            case "CR-34" -> seedCr34(entity, periodMonth);
            case "CR-35" -> seedCr35(entity, periodMonth);
            case "CR-36" -> seedCr36(entity, periodMonth);
            case "CR-42" -> seedCr42(entity, periodMonth);
            case "CR-46" -> seedCr46(entity, periodMonth);
            case "CR-40" -> seedCr40(entity, periodMonth);
            case "CR-41" -> seedCr41(entity, periodMonth);
            case "CR-43" -> seedCr43(entity, periodMonth);
            case "CR-44" -> seedCr44(entity, periodMonth);
            case "CR-45" -> seedCr45(entity, periodMonth);
            case "CR-47" -> seedCr47(entity, periodMonth);
            case "CR-48" -> seedCr48(entity, periodMonth);
            case "CR-49" -> seedCr49(entity, periodMonth);
            case "CR-50" -> seedCr50(entity, periodMonth);
            case "CR-51" -> seedCr51(entity, periodMonth);
            case "CR-37" -> seedCr37(entity, periodMonth);
            case "CR-38" -> seedCr38(entity, periodMonth);
            case "CR-60" -> seedCr60(entity, periodMonth);
            case "CR-61" -> seedCr61(entity, periodMonth);
            case "CR-62" -> seedCr62(entity, periodMonth);
            case "CR-63" -> seedCr63(entity, periodMonth);
            case "CR-65" -> seedCr65(entity, periodMonth);
            case "CR-66" -> seedCr66(entity, periodMonth);
            case "CR-67" -> seedCr67(entity, periodMonth);
            case "CR-73" -> seedCr73(entity, periodMonth);
            case "CR-74" -> seedCr74(entity, periodMonth);
            case "CR-75" -> seedCr75(entity, periodMonth);
            case "CR-77" -> seedCr77(entity, periodMonth);
            case "CR-82" -> seedCr82(entity, periodMonth);
            case "CR-83" -> seedCr83(entity, periodMonth);
            case "CR-81" -> seedCr81(entity, periodMonth);
            case "CR-79" -> seedCr79(entity, periodMonth);
            case "CR-84" -> seedCr84(entity, periodMonth);
            case "CR-76" -> seedCr76(entity, periodMonth);
            case "CR-85" -> seedCr85(entity, periodMonth);
            case "CR-78" -> seedCr78(entity, periodMonth);
            case "CR-80" -> seedCr80(entity, periodMonth);
            case "CR-86" -> seedCr86(entity, periodMonth);
            case "CAL-07" -> seedCal07(entity, periodMonth);
            case "CAL-12" -> seedCal12(entity, periodMonth);
            case "INT-17" -> seedInt17(entity, periodMonth);
            case "INT-20" -> seedInt20(entity, periodMonth);
            case "INT-22" -> seedInt22(entity, periodMonth);
            case "INT-23" -> seedInt23(entity, periodMonth);
            case "PAR-04" -> seedPar04(entity, periodMonth);
            case "PAR-06" -> seedPar06(entity, periodMonth);
            case "PAR-08" -> seedPar08(entity, periodMonth);
            case "PAR-09" -> seedPar09(entity, periodMonth);
            case "PAR-12" -> seedPar12(entity, periodMonth);
            case "CAL-04" -> seedCal04(entity, periodMonth);
            case "CAL-06" -> seedCal06(entity, periodMonth);
            case "CAL-06-RULE" -> seedCal06Rule(entity, periodMonth);
            case "CAL-08" -> seedCal08(entity, periodMonth);
            case "CAL-09" -> seedCal09(entity, periodMonth);
            case "CAL-10" -> seedCal10(entity, periodMonth);
            case "CAL-10-END" -> seedCal10End(entity, periodMonth);
            case "CAL-11" -> seedCal11(entity, periodMonth);
            case "CYC-01" -> seedCyc01(entity, periodMonth);
            case "CYC-02" -> seedCyc02(entity, periodMonth);
            case "CYC-03" -> seedCyc03(entity, periodMonth);
            case "CYC-04" -> seedCyc04(entity, periodMonth);
            case "CYC-05" -> seedCyc05(entity, periodMonth);
            case "CTL-01" -> seedCtl01(entity, periodMonth);
            default -> {
                return Response.status(Response.Status.BAD_REQUEST).entity("Cas inconnu : " + crCode).build();
            }
        }
        return Response.noContent().build();
    }

    /**
     * The premium perimeter of the reference product for an entity: the product and a collected premium.
     *
     * @param entity the entity
     * @return the scope JSON
     */
    private String premiumScope(LegalEntity entity) {
        return "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE_SELECT\"],"
                + "\"eventType\":[\"PREMIUM_COLLECTED\"]}";
    }

    /** Parameters of the reference 30/10 upfront rule (first year 365 days). */
    private static final String UPFRONT_PARAMS =
            "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365}";

    /**
     * CR-01 (linéaire) : Santé Select, choix linéaire 15 %, un encaissement de 100 € hors taxes ; le
     * moteur doit produire une seule ligne {@code LINEAR} de 15,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr01(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR01", product, periodMonth, "P-CR01", null);
        linearChoice(coverage, "0.15", periodMonth, null);
        version(entity, "PLAN-CR01", "R-CR01-LIN", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        premium(entity, coverage, "CR01-E1", periodMonth.plusDays(4), "100.00");
    }

    /**
     * CR-02 (précompté, année 1) : apport 30 %, suivi 10 %, prime annuelle de 1 000 € encaissée la
     * première année ; le moteur doit produire une ligne {@code ACQUISITION} de 300,00 € et une ligne
     * {@code SERVICING} de 100,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr02(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR02", "P-CR02");
        premium(entity, coverage, "CR02-E1", periodMonth.plusDays(4), "1000.00");
    }

    /**
     * CR-03 (précompté, année 2) : même garantie une année plus tard, prime de 1 000 € encaissée la
     * deuxième année ; le moteur ne doit produire qu'une ligne {@code SERVICING} de 100,00 €, sans apport.
     * La garantie prend effet un an avant le mois de calcul, de sorte que la prime tombe en 2e année.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr03(LegalEntity entity, LocalDate periodMonth) {
        LocalDate effect = periodMonth.minusYears(1);
        Coverage coverage = upfrontCoverage(entity, effect, "CR03", "P-CR03");
        premium(entity, coverage, "CR03-E1", periodMonth.plusDays(4), "1000.00");
    }

    /**
     * CR-04 (reprise en 1re année) : six encaissements mensuels de 100 € (précompté 30/10) puis
     * résiliation au 180e jour ; acquis apport 180,00 € et suivi 60,00 €, reprise −180,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr04(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR04", "P-CR04");
        for (int i = 0; i < 6; i++) {
            premium(entity, coverage, "CR04-E" + i, periodMonth.plusMonths(i), "100.00");
        }
        event(entity, coverage, "CR04-TERM", EventType.TERMINATION, periodMonth.plusDays(180), null);
    }

    /**
     * CR-05 (reprise en 2e année) : prime de 1 000 € puis résiliation au 540e jour ; reprise −150,00 €
     * (50 % de l'apport de 300 €).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr05(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR05", "P-CR05");
        premium(entity, coverage, "CR05-E1", periodMonth.plusDays(4), "1000.00");
        event(entity, coverage, "CR05-TERM", EventType.TERMINATION, periodMonth.plusDays(540), null);
    }

    /**
     * CR-06 (pas de reprise au-delà de deux ans) : prime de 1 000 € puis résiliation au 731e jour ;
     * aucune reprise.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr06(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR06", "P-CR06");
        premium(entity, coverage, "CR06-E1", periodMonth.plusDays(4), "1000.00");
        event(entity, coverage, "CR06-TERM", EventType.TERMINATION, periodMonth.plusDays(731), null);
    }

    /**
     * CR-07 (sans suite) : deux encaissements de 100 € puis classement sans suite ; reprise de 100 % de
     * toutes les lignes acquises, soit −80,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr07(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR07", "P-CR07");
        premium(entity, coverage, "CR07-E1", periodMonth, "100.00");
        premium(entity, coverage, "CR07-E2", periodMonth.plusMonths(1), "100.00");
        event(entity, coverage, "CR07-NP", EventType.NOT_PURSUED, periodMonth.plusDays(90), null);
    }

    /**
     * CR-08 (décès) : prime de 1 000 € puis décès de l'adhérent au 5e mois ; aucune reprise (le décès
     * n'est pas un événement de rupture).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr08(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = upfrontCoverage(entity, periodMonth, "CR08", "P-CR08");
        premium(entity, coverage, "CR08-E1", periodMonth.plusDays(4), "1000.00");
        event(entity, coverage, "CR08-DEATH", EventType.DEATH, periodMonth.plusDays(150), null);
    }

    /**
     * CR-09 (annulation d'encaissement) : l'encaissement de CR-01 est annulé ; une ligne `LINEAR` de
     * 15,00 € et sa ligne inverse de −15,00 €, même règle, même bénéficiaire.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr09(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR09", product, periodMonth, "P-CR09", null);
        linearChoice(coverage, "0.15", periodMonth, null);
        version(entity, "PLAN-CR09", "R-CR09", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        BusinessEvent origin = premium(entity, coverage, "CR09-E1", periodMonth.plusDays(4), "100.00");
        BusinessEvent cancellation = event(entity, coverage, "CR09-CANCEL", EventType.PREMIUM_CANCELLED,
                periodMonth.plusDays(9), null);
        cancellation.reversesEvent = origin;
    }

    /**
     * CR-10 (changement de taux) : barème à 15 % jusqu'au 28 février, 12 % à partir du 1er mars (choix et
     * règle versionnés) ; l'encaissement de janvier reste à 15 % (15,00 €), celui de mars passe à 12 %
     * (12,00 €), aucune ligne recalculée.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr10(LegalEntity entity, LocalDate periodMonth) {
        LocalDate boundary = periodMonth.plusMonths(2);
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR10", product, periodMonth, "P-CR10", null);
        linearChoice(coverage, "0.15", periodMonth, boundary.minusDays(1));
        linearChoice(coverage, "0.12", boundary, null);
        version(entity, "PLAN-CR10-V1", "R-CR10-V1", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, boundary.minusDays(1), null);
        version(entity, "PLAN-CR10-V2", "R-CR10-V2", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.12]}", boundary, null, null);
        premium(entity, coverage, "CR10-JAN", periodMonth.plusDays(14), "100.00");
        premium(entity, coverage, "CR10-MAR", boundary.plusDays(14), "100.00");
    }

    /**
     * CR-11 (taux hors grille) : garantie au taux linéaire de 12 % alors que la grille n'admet que 15 et
     * 20 % ; l'événement est mis en attente avec le motif « taux hors grille », aucune ligne produite.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr11(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR11", product, periodMonth, "P-CR11", null);
        linearChoice(coverage, "0.12", periodMonth, null);
        version(entity, "PLAN-CR11", "R-CR11", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15,0.20]}", periodMonth, null, null);
        premium(entity, coverage, "CR11-E1", periodMonth.plusDays(4), "100.00");
    }

    /**
     * CR-12 (collèges) : santé collective à 7 % pour les cadres et 5 % pour les non-cadres, 1 000 €
     * encaissés sur chaque collège ; 70,00 € et 50,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr12(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage executive = coverage(entity, "CR12-EXE", product, periodMonth, "P-CR12-EXE",
                StaffCategory.EXECUTIVE);
        Coverage nonExecutive = coverage(entity, "CR12-NON", product, periodMonth, "P-CR12-NON",
                StaffCategory.NON_EXECUTIVE);
        linearChoice(executive, "0.07", periodMonth, null);
        linearChoice(nonExecutive, "0.05", periodMonth, null);
        version(entity, "PLAN-CR12-EXE", "R-CR12-EXE", RuleFamily.LINEAR, LineNature.LINEAR,
                collegeScope(entity, "EXECUTIVE"), "{\"allowedRates\":[0.07]}", periodMonth, null, null);
        version(entity, "PLAN-CR12-NON", "R-CR12-NON", RuleFamily.LINEAR, LineNature.LINEAR,
                collegeScope(entity, "NON_EXECUTIVE"), "{\"allowedRates\":[0.05]}", periodMonth, null, null);
        premium(entity, executive, "CR12-EXE-E", periodMonth.plusDays(4), "1000.00");
        premium(entity, nonExecutive, "CR12-NON-E", periodMonth.plusDays(4), "1000.00");
    }

    /**
     * CR-20 (versement d'escompte) : convention valide, prime annualisée 1 000 €, apport 30 % / suivi
     * 10 %, escompte demandé ; le moteur verse l'escompte dans la période du mois d'effet : deux lignes
     * `ADVANCE` totalisant 400,00 € (300 + 100) et une position d'escompte active.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (the coverage effect month)
     */
    private void seedCr20(LegalEntity entity, LocalDate periodMonth) {
        escompteCoverage(entity, periodMonth, "CR20", "P-CR20", "1000");
        convention(entity, "P-CR20", 100, LocalDate.of(periodMonth.getYear(), 12, 31));
    }

    /**
     * CR-21 (apurement) : escompte de 480 € sur une prime de 1 200 €, puis deux encaissements de 100 €
     * imputés (apurés) ; les lignes acquises sont au statut `OFFSET` (jamais payées) et la position reste
     * active (créance encore ouverte). Observable sur l'écran de la convention : escompté 480,00 €, apuré
     * 80,00 €, statut Active.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr21(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = escompteCoverage(entity, periodMonth, "CR21", "P-CR21", "1200");
        convention(entity, "P-CR21", 100, LocalDate.of(periodMonth.getYear(), 12, 31));
        premium(entity, coverage, "CR21-E1", periodMonth.plusDays(14), "100.00");
        premium(entity, coverage, "CR21-E2", periodMonth.plusMonths(1).plusDays(14), "100.00");
    }

    /**
     * CR-22 (résiliation à 6 mois) : 200 € apurés puis résiliation au 180e jour ; l'escompte est annulé,
     * la part non apurée est régularisée (−200,00 €) et l'apport acquis est repris (−150,00 €) ; le
     * distributeur conserve 50 € de suivi.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr22(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = escompteCoverage(entity, periodMonth, "CR22", "P-CR22", "1000");
        convention(entity, "P-CR22", 100, LocalDate.of(periodMonth.getYear(), 12, 31));
        premium(entity, coverage, "CR22-E1", periodMonth.plusMonths(2).plusDays(14), "500.00");
        event(entity, coverage, "CR22-TERM", EventType.TERMINATION, periodMonth.plusDays(180), null);
    }

    /**
     * CR-23 (solde par apurement) : 400 € apurés au 10e mois ; l'escompte est soldé (`SETTLED`) sans
     * régularisation, et les commissions suivantes redeviennent payables.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr23(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = escompteCoverage(entity, periodMonth, "CR23", "P-CR23", "1000");
        convention(entity, "P-CR23", 100, LocalDate.of(periodMonth.getYear(), 12, 31));
        for (int i = 0; i < 10; i++) {
            premium(entity, coverage, "CR23-E" + i, periodMonth.plusMonths(i).plusDays(14), "100.00");
        }
    }

    /**
     * CR-24 (solde avec complément d'escompte) : 380 € apurés puis une ligne acquise après une hausse de
     * cotisation qui dépasse l'escompte ; l'escompte est soldé avec une régularisation payable de +30,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr24(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = escompteCoverage(entity, periodMonth, "CR24", "P-CR24", "1000");
        convention(entity, "P-CR24", 100, LocalDate.of(periodMonth.getYear(), 12, 31));
        premium(entity, coverage, "CR24-E1", periodMonth.plusMonths(1).plusDays(14), "950.00");
        premium(entity, coverage, "CR24-E2", periodMonth.plusMonths(2).plusDays(14), "125.00");
    }

    /**
     * CR-25 (hors convention) : la garantie prend effet après la fin de la convention ; aucun escompte,
     * ses encaissements sont payés normalement (une ligne `ACQUISITION` et une ligne `SERVICING`).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr25(LegalEntity entity, LocalDate periodMonth) {
        LocalDate effect = periodMonth.plusMonths(1);
        Coverage coverage = escompteCoverage(entity, effect, "CR25", "P-CR25", "1000");
        convention(entity, "P-CR25", 100, periodMonth.minusDays(1));
        premium(entity, coverage, "CR25-E1", effect.plusDays(14), "100.00");
    }

    /**
     * CR-26 (second impayé) : un avis d'impayé de rang 2 (seuil de la convention) rompt l'escompte
     * aussitôt ; la position passe `CANCELLED` et la part escomptée est régularisée (−400,00 €).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr26(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = escompteCoverage(entity, periodMonth, "CR26", "P-CR26", "1000");
        convention(entity, "P-CR26", 100, LocalDate.of(periodMonth.getYear(), 12, 31));
        unpaidNotice(entity, coverage, "CR26-UNPAID", periodMonth.plusDays(90), (short) 2);
    }

    /**
     * CR-27 (plafond) : la convention est plafonnée à une adhésion, déjà prise par une position existante ;
     * la garantie n'obtient pas d'escompte et ses encaissements sont payés.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr27(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = escompteCoverage(entity, periodMonth, "CR27", "P-CR27", "1000");
        AdvanceAgreement agreement = convention(entity, "P-CR27", 1, LocalDate.of(periodMonth.getYear(), 12, 31));
        AdvancePosition taken = new AdvancePosition();
        taken.advanceAgreement = agreement;
        taken.status = AdvanceStatus.ACTIVE;
        taken.persist();
        premium(entity, coverage, "CR27-E1", periodMonth.plusDays(14), "100.00");
    }

    /**
     * CR-28 (déficit à 360 jours) : 370 € apurés mais la position encore ouverte au 360e jour, contrat
     * toujours en vigueur ; l'escompte est soldé et le reliquat non apuré repris comme escompte indu
     * (−30,00 €). La garantie prend effet onze mois avant le mois de règlement (décembre).
     *
     * @param entity      the entity
     * @param periodMonth the settlement month (around the 360th day)
     */
    private void seedCr28(LegalEntity entity, LocalDate periodMonth) {
        LocalDate effect = periodMonth.minusMonths(11);
        Coverage coverage = escompteCoverage(entity, effect, "CR28", "P-CR28", "1000");
        convention(entity, "P-CR28", 100, LocalDate.of(effect.getYear(), 12, 31));
        premium(entity, coverage, "CR28-E1", effect.plusMonths(6).plusDays(14), "925.00");
    }

    /**
     * CR-30 (versement initial, épargne retraite) : versement initial de 6 000 € sur un PER à 2 % ; une
     * ligne `LINEAR` de 120,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr30(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "PER");
        Coverage coverage = coverage(entity, "CR30", product, periodMonth, "P-CR30", null);
        linearChoice(coverage, "0.02", periodMonth, null);
        version(entity, "PLAN-CR30", "R-CR30", RuleFamily.LINEAR, LineNature.LINEAR,
                perScope(entity, "INITIAL_PAYMENT"), "{\"allowedRates\":[0.02]}", periodMonth, null, null);
        event(entity, coverage, "CR30-E1", EventType.INITIAL_PAYMENT, periodMonth.plusDays(4), "6000.00");
    }

    /**
     * CR-31 (versement programmé, linéaire) : versement programmé de 300 € sur un PER à 3 % ; une ligne
     * `LINEAR` de 9,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr31(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "PER");
        Coverage coverage = coverage(entity, "CR31", product, periodMonth, "P-CR31", null);
        linearChoice(coverage, "0.03", periodMonth, null);
        version(entity, "PLAN-CR31", "R-CR31", RuleFamily.LINEAR, LineNature.LINEAR,
                perScope(entity, "SCHEDULED_PAYMENT"), "{\"allowedRates\":[0.03]}", periodMonth, null, null);
        event(entity, coverage, "CR31-E1", EventType.SCHEDULED_PAYMENT, periodMonth.plusDays(4), "300.00");
    }

    /**
     * The savings perimeter for a PER product and an event type.
     *
     * @param entity    the entity
     * @param eventType the event type
     * @return the scope JSON
     */
    private String perScope(LegalEntity entity, String eventType) {
        return "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"PER\"],\"eventType\":[\"" + eventType + "\"]}";
    }

    /**
     * The savings perimeter for a PER product, all event types.
     *
     * @param entity the entity
     * @return the scope JSON
     */
    private String savingsScope(LegalEntity entity) {
        return "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"PER\"]}";
    }

    /** Parameters of an outbound retirement discount settled at the fifth anniversary, rate 8 %. */
    private static final String OUT_DISCOUNT =
            "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"agreementRequired\":false,\"rate\":0.08,"
            + "\"offsetting\":false,\"annualReadjustment\":false}";

    /** Parameters of an inbound (insurer) retirement discount at 10 % with a 1,95 % recall rate. */
    private static final String IN_DISCOUNT =
            "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"agreementRequired\":false,\"rate\":0.10,"
            + "\"recallRate\":0.0195,\"annualReadjustment\":false}";

    /**
     * CR-33 (rappel, commission reçue de l'assureur) : escompte de 120 € (25 versements de 100 € à
     * 1,95 %) sur un plan suspendu ; le rappel attendu de −71,25 € est conservé comme **montant attendu**
     * du contrôle CAL-13, sans ligne. Lu sur l'écran des contrôles (colonne « Attendu »).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr33(LegalEntity entity, LocalDate periodMonth) {
        seedInsurerRecall(entity, periodMonth, "CR33", 25, "100.00");
    }

    /**
     * CR-34 (rappel, commission reçue de l'assureur) : escompte de 240 € (21 versements de 200 € à
     * 1,95 %) ; le rappel attendu de −158,10 € est conservé comme montant attendu du contrôle CAL-13.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr34(LegalEntity entity, LocalDate periodMonth) {
        seedInsurerRecall(entity, periodMonth, "CR34", 21, "200.00");
    }

    /**
     * Seeds an inbound retirement discount whose plan is suspended after a number of scheduled payments,
     * so the real calculation records the expected insurer-commission recall (CAL-13, P17) without a line.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     * @param key         the scenario key (contract and partner suffix)
     * @param payments    the number of scheduled payments before suspension
     * @param amount      each scheduled payment amount
     */
    private void seedInsurerRecall(LegalEntity entity, LocalDate periodMonth, String key, int payments,
                                   String amount) {
        Product product = product(entity, "PER");
        Coverage coverage = coverage(entity, key, product, periodMonth, "P-" + key, null);
        linearChoice(coverage, "0.0145", periodMonth, null);
        version(entity, Direction.INBOUND, "PLAN-" + key, "R-" + key, RuleFamily.ADVANCE, LineNature.ADVANCE,
                savingsScope(entity), IN_DISCOUNT, periodMonth, null, null);
        for (int i = 1; i <= payments; i++) {
            event(entity, coverage, key + "-S" + i, EventType.SCHEDULED_PAYMENT, periodMonth.plusDays(i), amount);
        }
        event(entity, coverage, key + "-SUS", EventType.PLAN_SUSPENDED, periodMonth.plusDays(27), null);
    }

    /**
     * CR-36 (sur-commission assureur) : 28 % de 3 600 € de versements programmés, plafonné à 50 000 € ;
     * la sur-commission attendue de 1 008,00 € est conservée comme montant attendu du contrôle CAL-13,
     * sans ligne, au moment de la clôture annuelle.
     *
     * @param entity      the entity
     * @param periodMonth the annual-close month
     */
    private void seedCr36(LegalEntity entity, LocalDate periodMonth) {
        int year = periodMonth.getYear();
        Product product = product(entity, "PER");
        Coverage coverage = coverage(entity, "CR36", product, LocalDate.of(year, 1, 1), "P-CR36", null);
        version(entity, Direction.INBOUND, "PLAN-CR36", "R-CR36", RuleFamily.LINEAR, LineNature.LINEAR,
                savingsScope(entity),
                "{\"rateSource\":\"RULE\",\"rate\":0.28,\"calculationFrequency\":\"ANNUAL\",\"annualCap\":50000}",
                LocalDate.of(year, 1, 1), null, null);
        event(entity, coverage, "CR36-S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(year, 6, 1), "3600.00");
    }

    /**
     * CR-32 (versement programmé, escompté) : un plan programmé de 300 €/mois escompté donne, l'année 1,
     * une seule ligne `ADVANCE` de 288,00 € (3 600 × 8 %) ; les versements programmés ne produisent pas de
     * ligne. L'escompte est dû dans la période qui suit le premier versement (§6.6), d'où un calcul de
     * février sur un versement de janvier.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (February)
     */
    private void seedCr32(LegalEntity entity, LocalDate periodMonth) {
        LocalDate effect = periodMonth.minusMonths(1).withDayOfMonth(1);
        Product product = product(entity, "PER");
        Coverage coverage = coverage(entity, "CR32", product, effect, "P-CR32", null);
        linearChoice(coverage, "0.03", effect, null);
        version(entity, "PLAN-CR32", "R-CR32", RuleFamily.ADVANCE, LineNature.ADVANCE,
                savingsScope(entity), OUT_DISCOUNT, effect, null, null);
        event(entity, coverage, "CR32-E1", EventType.SCHEDULED_PAYMENT, effect.plusDays(9), "300.00");
    }

    /**
     * CR-35 (complément retraite) : la production annualisée du distributeur sur l'année est inférieure
     * à 10 000 € (deux contrats à 3 510 € et 3 529,80 € nets de frais), le palier à 10 % s'applique donc
     * à chacun ; deux lignes `COMPLEMENT` de 351,00 € et 352,98 €, soit 703,98 €. Calculé à la clôture
     * annuelle, déclenchée depuis l'écran des exécutions.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (the close period, within the production year)
     */
    private void seedCr35(LegalEntity entity, LocalDate periodMonth) {
        LocalDate effect = LocalDate.of(periodMonth.getYear(), 1, 1);
        Product product = product(entity, "PER");
        Coverage c1 = coverage(entity, "CR35-1", product, effect, "P-CR35", null);
        Coverage c2 = coverage(entity, "CR35-2", product, effect, "P-CR35", null);
        RuleVersion rule = version(entity, "PLAN-CR35", "R-CR35", RuleFamily.TIERED, LineNature.COMPLEMENT,
                savingsScope(entity), "{\"counterCode\":\"PER\",\"calculationFrequency\":\"ANNUAL\"}", effect, null,
                null);
        complementScale(rule);
        event(entity, c1, "CR35-E1", EventType.SCHEDULED_PAYMENT, LocalDate.of(periodMonth.getYear(), 6, 1),
                "3510.00");
        event(entity, c2, "CR35-E2", EventType.SCHEDULED_PAYMENT, LocalDate.of(periodMonth.getYear(), 6, 1),
                "3529.80");
    }

    /** The santé surcommission perimeter. */
    private String santeScope(LegalEntity entity) {
        return "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE\"]}";
    }

    /**
     * CR-42 (RCD santé, palier au premier euro) : une production cumulée de 120 000 € atteint le palier
     * « à partir du premier euro » ; la clôture de développement, déclenchée depuis l'écran des
     * exécutions, verse une seule ligne `RCD` de 6 000,00 € (120 000 × 5 %).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (the development close month)
     */
    private void seedCr42(LegalEntity entity, LocalDate periodMonth) {
        int year = periodMonth.getYear();
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "CR42", product, LocalDate.of(year, 1, 10), "P-CR42", null);
        coverage.annualPremium = new BigDecimal("120000");
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CR42").firstResult();
        RuleVersion version = version(entity, "PLAN-CR42", "R-CR42", RuleFamily.TIERED, LineNature.RCD,
                santeScope(entity), "{\"counterCode\":\"RCD\",\"calculationFrequency\":\"MONTHLY\"}",
                LocalDate.of(year, 1, 1), null, null);
        santeDevelopmentScale(version);
        enrol(version.rule, distributor, AgreementType.RCD_AMENDMENT, true);
        event(entity, coverage, "CR42-SUB", EventType.SUBSCRIPTION, LocalDate.of(year, 1, 15), null);
    }

    /**
     * CR-40 (RCD santé, sous le premier palier) : une production cumulée de 15 000 € reste sous le
     * premier palier (20 000 €) ; la clôture de développement ne produit aucune ligne `RCD`.
     *
     * @param entity      the entity
     * @param periodMonth the development close month
     */
    private void seedCr40(LegalEntity entity, LocalDate periodMonth) {
        int year = periodMonth.getYear();
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "CR40", product, LocalDate.of(year, 1, 10), "P-CR40", null);
        coverage.annualPremium = new BigDecimal("15000");
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CR40").firstResult();
        RuleVersion version = version(entity, "PLAN-CR40", "R-CR40", RuleFamily.TIERED, LineNature.RCD,
                santeScope(entity), "{\"counterCode\":\"RCD\",\"calculationFrequency\":\"MONTHLY\"}",
                LocalDate.of(year, 1, 1), null, null);
        santeDevelopmentScale(version);
        enrol(version.rule, distributor, AgreementType.RCD_AMENDMENT, true);
        event(entity, coverage, "CR40-SUB", EventType.SUBSCRIPTION, LocalDate.of(year, 1, 15), null);
    }

    /**
     * CR-44 (RCD sans avenant signé) : l'inscription existe mais l'avenant n'est pas signé ; la clôture
     * de développement ne produit aucune ligne et lève une anomalie de contrôle (`ENR-01`, « Avenant non
     * signé »), lue sur l'écran des contrôles.
     *
     * @param entity      the entity
     * @param periodMonth the development close month
     */
    private void seedCr44(LegalEntity entity, LocalDate periodMonth) {
        int year = periodMonth.getYear();
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "CR44", product, LocalDate.of(year, 1, 10), "P-CR44", null);
        coverage.annualPremium = new BigDecimal("30000");
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CR44").firstResult();
        RuleVersion version = version(entity, "PLAN-CR44", "R-CR44", RuleFamily.TIERED, LineNature.RCD,
                santeScope(entity), "{\"counterCode\":\"RCD\",\"calculationFrequency\":\"MONTHLY\"}",
                LocalDate.of(year, 1, 1), null, null);
        santeDevelopmentScale(version);
        enrol(version.rule, distributor, AgreementType.RCD_AMENDMENT, false);
        event(entity, coverage, "CR44-SUB", EventType.SUBSCRIPTION, LocalDate.of(year, 1, 15), null);
    }

    /**
     * CR-41 (RCD santé, deux paliers) : le cumul atteint 30 000 € fin janvier puis 60 000 € fin février ;
     * la clôture de développement verse 300,00 € en janvier (palier marginal 3 %) puis 1 350 − 300 =
     * 1 050,00 € en février. Deux périodes sont semées, janvier et février.
     *
     * @param entity      the entity
     * @param periodMonth the January calculation month
     */
    private void seedCr41(LegalEntity entity, LocalDate periodMonth) {
        int year = periodMonth.getYear();
        period(entity, periodMonth.plusMonths(1));
        Product product = product(entity, "SANTE");
        Coverage january = coverage(entity, "CR41-1", product, LocalDate.of(year, 1, 10), "P-CR41", null);
        january.annualPremium = new BigDecimal("30000");
        Coverage february = coverage(entity, "CR41-2", product, LocalDate.of(year, 2, 10), "P-CR41", null);
        february.annualPremium = new BigDecimal("30000");
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CR41").firstResult();
        RuleVersion version = version(entity, "PLAN-CR41", "R-CR41", RuleFamily.TIERED, LineNature.RCD,
                santeScope(entity), "{\"counterCode\":\"RCD\",\"calculationFrequency\":\"MONTHLY\"}",
                LocalDate.of(year, 1, 1), null, null);
        santeDevelopmentScale(version);
        enrol(version.rule, distributor, AgreementType.RCD_AMENDMENT, true);
        event(entity, january, "CR41-SUB1", EventType.SUBSCRIPTION, LocalDate.of(year, 1, 15), null);
        event(entity, february, "CR41-SUB2", EventType.SUBSCRIPTION, LocalDate.of(year, 2, 15), null);
    }

    /**
     * CR-45 (reprise de RCD) : une adhésion portant 40 € de RCD déjà payé (allocation en période
     * antérieure), résiliée au 8e mois, est reprise en totalité : une ligne `CLAWBACK` de −40,00 € au
     * calcul réel de la résiliation.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (the termination month)
     */
    private void seedCr45(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "CR45", product, LocalDate.of(periodMonth.getYear(), 1, 15),
                "P-CR45", null);
        CommissionLine paid = new CommissionLine();
        paid.entity = entity;
        paid.period = period(entity, LocalDate.of(periodMonth.getYear(), 2, 1));
        paid.direction = Direction.OUTBOUND;
        paid.status = LineStatus.PAID;
        paid.nature = LineNature.RCD;
        paid.amount = new BigDecimal("40.00");
        paid.persist();
        SurcommissionAllocation allocation = new SurcommissionAllocation();
        allocation.commissionLine = paid;
        allocation.contract = coverage.contract;
        allocation.annualisedPremium = new BigDecimal("5000");
        allocation.allocatedAmount = new BigDecimal("40.00");
        allocation.persist();
        event(entity, coverage, "CR45-TERM", EventType.TERMINATION, LocalDate.of(periodMonth.getYear(), 9, 15), null);
    }

    /**
     * CR-46 (RCP santé) : une rémunération de portefeuille à 4 % sur 200 000 € de primes encaissées,
     * toutes conditions de qualité remplies (P/C 82 % ≤ 95 %, taux de chute 14 % ≤ 20 %) ; la clôture de
     * portefeuille, déclenchée depuis l'écran des exécutions, verse une ligne `RCP` de 8 000,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (the portfolio close month)
     */
    private void seedCr46(LegalEntity entity, LocalDate periodMonth) {
        int year = periodMonth.getYear();
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "CR46", product, LocalDate.of(year, 1, 10), "P-CR46", null);
        coverage.annualPremium = new BigDecimal("200000");
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CR46").firstResult();
        RuleVersion version = version(entity, "PLAN-CR46", "R-CR46", RuleFamily.PORTFOLIO, LineNature.RCP,
                santeScope(entity), "{\"rate\":0.04}", LocalDate.of(year, 1, 1), null, null);
        condition(version, QualityIndicatorType.LOSS_RATIO, ConditionOperator.LE, "0.95");
        condition(version, QualityIndicatorType.PORTFOLIO_LAPSE_RATE, ConditionOperator.LE, "0.20");
        enrol(version.rule, distributor, AgreementType.RCP_AMENDMENT, true);
        indicator(entity, distributor, QualityIndicatorType.LOSS_RATIO, "0.82", year);
        indicator(entity, distributor, QualityIndicatorType.PORTFOLIO_LAPSE_RATE, "0.14", year);
        event(entity, coverage, "CR46-PREM", EventType.PREMIUM_COLLECTED, LocalDate.of(year, 6, 1), "200000");
    }

    /**
     * Attaches the santé development marginal scale to a rule version: 3 % from 20 000 €, 4 % from
     * 45 000 €, 5 % from 75 000 €, then 5 % from the first euro past 100 000 €.
     *
     * @param version the development rule version
     */
    private void santeDevelopmentScale(RuleVersion version) {
        Scale scale = new Scale();
        scale.ruleVersion = version;
        scale.mode = ScaleMode.MARGINAL;
        scale.measure = ScaleMeasure.AMOUNT;
        scale.output = ScaleOutput.RATE;
        scale.persist();
        boundedTier(scale, "20000", "45000", "0.03", false);
        boundedTier(scale, "45000", "75000", "0.04", false);
        boundedTier(scale, "75000", "100000", "0.05", false);
        boundedTier(scale, "100001", null, "0.05", true);
    }

    /**
     * Adds one bounded tier to a scale.
     *
     * @param scale         the scale
     * @param lowerBound    the included lower bound
     * @param upperBound    the excluded upper bound, or null for the last tier
     * @param rate          the tier rate, as a fraction
     * @param fromFirstUnit whether the rate applies from the first unit
     */
    private void boundedTier(Scale scale, String lowerBound, String upperBound, String rate, boolean fromFirstUnit) {
        ScaleTier tier = new ScaleTier();
        tier.scale = scale;
        tier.lowerBound = new BigDecimal(lowerBound);
        tier.upperBound = upperBound == null ? null : new BigDecimal(upperBound);
        tier.rate = new BigDecimal(rate);
        tier.fromFirstUnit = fromFirstUnit;
        tier.persist();
    }

    /**
     * Enrols a distributor on a rule with an amendment of a type, signed or not.
     *
     * @param rule        the rule
     * @param distributor the distributor
     * @param type        the amendment type
     * @param signed      whether the amendment is signed
     */
    private void enrol(Rule rule, Distributor distributor, AgreementType type, boolean signed) {
        Agreement agreement = new Agreement();
        agreement.entity = rule.plan.entity;
        agreement.agreementType = type;
        agreement.distributor = distributor;
        agreement.signatureStatus = signed ? SignatureStatus.SIGNED : SignatureStatus.NOT_SIGNED;
        agreement.validFrom = LocalDate.of(2020, 1, 1);
        agreement.persist();
        Enrollment enrollment = new Enrollment();
        enrollment.rule = rule;
        enrollment.distributor = distributor;
        enrollment.agreement = agreement;
        enrollment.status = EnrollmentStatus.ACTIVE;
        enrollment.validFrom = LocalDate.of(2020, 1, 1);
        enrollment.persist();
    }

    /**
     * Adds a quality condition to a rule version.
     *
     * @param version   the rule version
     * @param type      the quality indicator type
     * @param operator  the comparison operator
     * @param threshold the threshold, as a fraction
     */
    private void condition(RuleVersion version, QualityIndicatorType type, ConditionOperator operator,
                           String threshold) {
        RuleCondition condition = new RuleCondition();
        condition.ruleVersion = version;
        condition.indicatorType = type;
        condition.operator = operator;
        condition.threshold = new BigDecimal(threshold);
        condition.persist();
    }

    /**
     * Records a yearly quality indicator for a distributor.
     *
     * @param entity      the entity
     * @param distributor the distributor
     * @param type        the quality indicator type
     * @param value       the indicator value, as a fraction
     * @param year        the year the indicator covers
     */
    private void indicator(LegalEntity entity, Distributor distributor, QualityIndicatorType type, String value,
                           int year) {
        QualityIndicator indicator = new QualityIndicator();
        indicator.entity = entity;
        indicator.distributor = distributor;
        indicator.indicatorType = type;
        indicator.periodFrom = LocalDate.of(year, 1, 1);
        indicator.periodTo = LocalDate.of(year, 12, 31);
        indicator.value = new BigDecimal(value);
        indicator.persist();
    }

    /**
     * CR-43 (RCD, condition non remplie) : un taux d'impayés de 6 % casse la condition de paiement
     * (&lt; 5 %) ; la clôture de développement écrit une ligne `RCD` de 0,00 €, le motif étant conservé
     * dans l'explication de la ligne.
     *
     * @param entity      the entity
     * @param periodMonth the development close month
     */
    private void seedCr43(LegalEntity entity, LocalDate periodMonth) {
        int year = periodMonth.getYear();
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "CR43", product, LocalDate.of(year, 1, 10), "P-CR43", null);
        coverage.annualPremium = new BigDecimal("30000");
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CR43").firstResult();
        RuleVersion version = version(entity, "PLAN-CR43", "R-CR43", RuleFamily.TIERED, LineNature.RCD,
                santeScope(entity), "{\"counterCode\":\"RCD\",\"calculationFrequency\":\"MONTHLY\"}",
                LocalDate.of(year, 1, 1), null, null);
        santeDevelopmentScale(version);
        condition(version, QualityIndicatorType.UNPAID_RATE, ConditionOperator.LT, "0.05");
        enrol(version.rule, distributor, AgreementType.RCD_AMENDMENT, true);
        indicator(entity, distributor, QualityIndicatorType.UNPAID_RATE, "0.06", year);
        event(entity, coverage, "CR43-SUB", EventType.SUBSCRIPTION, LocalDate.of(year, 1, 15), null);
    }

    /**
     * CR-47 (RCP santé, condition non remplie) : un P/C de 96 % casse la condition de portefeuille
     * (&le; 95 %) ; la clôture de portefeuille écrit une ligne `RCP` de 0,00 €, le détail des conditions
     * étant conservé dans l'explication de la ligne.
     *
     * @param entity      the entity
     * @param periodMonth the portfolio close month
     */
    private void seedCr47(LegalEntity entity, LocalDate periodMonth) {
        int year = periodMonth.getYear();
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "CR47", product, LocalDate.of(year, 1, 10), "P-CR47", null);
        coverage.annualPremium = new BigDecimal("200000");
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CR47").firstResult();
        RuleVersion version = version(entity, "PLAN-CR47", "R-CR47", RuleFamily.PORTFOLIO, LineNature.RCP,
                santeScope(entity), "{\"rate\":0.04}", LocalDate.of(year, 1, 1), null, null);
        condition(version, QualityIndicatorType.LOSS_RATIO, ConditionOperator.LE, "0.95");
        enrol(version.rule, distributor, AgreementType.RCP_AMENDMENT, true);
        indicator(entity, distributor, QualityIndicatorType.LOSS_RATIO, "0.96", year);
        event(entity, coverage, "CR47-PREM", EventType.PREMIUM_COLLECTED, LocalDate.of(year, 6, 1), "200000");
    }

    /**
     * CR-48 (RCP prévoyance, palier au nombre) : 12 adhésions nouvelles atteignent le palier « 10 à 19 »
     * (2 %) d'un barème au nombre ; appliqué aux 50 000 € de primes encaissées, la clôture de portefeuille
     * verse une ligne `RCP` de 1 000,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (the portfolio close month)
     */
    private void seedCr48(LegalEntity entity, LocalDate periodMonth) {
        int year = periodMonth.getYear();
        Product product = product(entity, "SANTE");
        RuleVersion version = version(entity, "PLAN-CR48", "R-CR48", RuleFamily.PORTFOLIO, LineNature.RCP,
                santeScope(entity), "{\"rate\":0}", LocalDate.of(year, 1, 1), null, null);
        countScale(version);
        condition(version, QualityIndicatorType.PORTFOLIO_LAPSE_RATE, ConditionOperator.LE, "0.15");
        for (int i = 1; i <= 12; i++) {
            Coverage coverage = coverage(entity, "CR48-" + i, product, LocalDate.of(year, 1, 10), "P-CR48", null);
            event(entity, coverage, "CR48-SUB" + i, EventType.SUBSCRIPTION, LocalDate.of(year, 2, 1), null);
            if (i == 1) {
                event(entity, coverage, "CR48-PREM", EventType.PREMIUM_COLLECTED, LocalDate.of(year, 6, 1), "50000");
            }
        }
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CR48").firstResult();
        enrol(version.rule, distributor, AgreementType.RCP_AMENDMENT, true);
        indicator(entity, distributor, QualityIndicatorType.PORTFOLIO_LAPSE_RATE, "0.10", year);
    }

    /**
     * Attaches a new-business count scale (prévoyance RCP) to a rule version: 1 % from 5 adhésions, 2 %
     * from 10, 3 % from 20, 4 % from 40, the reached tier's rate applying to the whole collected premium.
     *
     * @param version the portfolio rule version
     */
    private void countScale(RuleVersion version) {
        Scale scale = new Scale();
        scale.ruleVersion = version;
        scale.mode = ScaleMode.WHOLE;
        scale.measure = ScaleMeasure.AMOUNT;
        scale.output = ScaleOutput.RATE;
        scale.persist();
        tier(scale, "5", "0.01");
        tier(scale, "10", "0.02");
        tier(scale, "20", "0.03");
        tier(scale, "40", "0.04");
    }

    /**
     * CR-49 (partage avec un groupement) : une commission linéaire de 30 € (15 % de 200 €) partagée par
     * la règle de bénéficiaire donne 27,00 € au distributeur (90 %) et 3,00 € à son groupement (10 %) ;
     * deux lignes `LINEAR`.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr49(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "CR49", product, periodMonth, "P-CR49", null);
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CR49").firstResult();
        DistributorGroup group = new DistributorGroup();
        group.code = "G-CR49";
        group.label = "G-CR49";
        group.persist();
        ProductionCode pc = CoverageAssignment.<CoverageAssignment>find("coverage", coverage)
                .firstResult().productionCode;
        payeeRule(entity, pc, distributor, "0.9", group, "0.1");
        linearChoice(coverage, "0.15", periodMonth, null);
        version(entity, "PLAN-CR49", "R-CR49", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}",
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        event(entity, coverage, "CR49-E1", EventType.PREMIUM_COLLECTED, periodMonth.plusDays(14), "200");
    }

    /**
     * CR-62 (distributeur suspendu) : un distributeur radié de l'ORIAS a sa commission calculée (110 €)
     * mais son paiement de cycle est bloqué (ordre annulé, exclusion « bénéficiaire inactif ») ; une fois
     * réinscrit (par ré-import du référentiel), un lot hors cycle le règle. Le distributeur est semé
     * `STRUCK_OFF`, avec un compte doté d'une ligne acquise de 110 € et des coordonnées bancaires.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr62(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR62", product, periodMonth, "P-CR62", null);
        ProductionCode pc = CoverageAssignment.<CoverageAssignment>find("coverage", coverage)
                .firstResult().productionCode;
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CR62").firstResult();
        distributor.oriasStatus = OriasStatus.STRUCK_OFF;
        DistributorEntity link = new DistributorEntity();
        link.distributor = distributor;
        link.entity = entity;
        link.status = PartnerStatus.ACTIVE;
        link.validFrom = periodMonth;
        link.persist();
        earnedOnCode(entity, period(entity, periodMonth), coverage, "110.00");
        BankAccount bank = new BankAccount();
        bank.entity = entity;
        bank.holderType = PayeeType.PRODUCTION_CODE;
        bank.holderId = pc.getId();
        bank.iban = "FR7630001007941234567890185";
        bank.bic = "AGRIFRPP";
        bank.holderName = "P-CR62";
        bank.validFrom = periodMonth;
        bank.persist();
    }

    /**
     * CR-60 (compte débiteur) : un solde d'ouverture de −1 000 € est injecté ; en février une commission
     * de 600 € laisse le compte à −400 € (aucun paiement), puis en mars une commission de 700 € le ramène
     * à +300 € (un ordre de 300,00 €). Deux périodes semées, le solde reporté d'un mois sur l'autre.
     *
     * @param entity      the entity
     * @param periodMonth the February calculation month
     */
    private void seedCr60(LegalEntity entity, LocalDate periodMonth) {
        Period february = period(entity, periodMonth);
        Period march = period(entity, periodMonth.plusMonths(1));
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR60", product, periodMonth.minusMonths(1), "P-CR60", null);
        ProductionCode pc = CoverageAssignment.<CoverageAssignment>find("coverage", coverage)
                .firstResult().productionCode;
        DistributorAccount account = new DistributorAccount();
        account.entity = entity;
        account.payeeType = PayeeType.PRODUCTION_CODE;
        account.payeeId = pc.getId();
        account.persist();
        AccountEntry opening = new AccountEntry();
        opening.account = account;
        opening.entryType = AccountEntryType.OPENING_BALANCE;
        opening.amount = new BigDecimal("-1000.00");
        opening.entryDate = periodMonth.minusMonths(1);
        opening.persist();
        earnedOnCode(entity, february, coverage, "600.00");
        earnedOnCode(entity, march, coverage, "700.00");
    }

    /**
     * CR-61 (compensation) : un distributeur a un code à −200 € et un code à +500 € ; la compensation
     * écrit deux lignes `TRANSFER`, ramène le code débiteur à 0 et laisse 300,00 € payables sur le code
     * créditeur. Les deux lignes acquises sont posées comme état d'entrée, chacune sur son code de
     * production du même distributeur.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr61(LegalEntity entity, LocalDate periodMonth) {
        Period period = period(entity, periodMonth);
        Product product = product(entity, "SANTE_SELECT");
        earnedOnCode(entity, period, coverage(entity, "CR61A", product, periodMonth, "P-CR61", null), "-200.00");
        earnedOnCode(entity, period, coverage(entity, "CR61B", product, periodMonth, "P-CR61", null), "500.00");
        // Register the distributor in the entity so it is selectable on the accounts screen.
        DistributorEntity link = new DistributorEntity();
        link.distributor = Distributor.<Distributor>find("partnerCode", "P-CR61").firstResult();
        link.entity = entity;
        link.status = PartnerStatus.ACTIVE;
        link.validFrom = periodMonth;
        link.persist();
    }

    /**
     * Poses an earned commission line on a coverage's main production code, so it posts to that code's
     * account at business validation.
     *
     * @param entity   the entity
     * @param period   the period
     * @param coverage the coverage carrying the production code
     * @param amount   the signed amount
     */
    private void earnedOnCode(LegalEntity entity, Period period, Coverage coverage, String amount) {
        ProductionCode pc = CoverageAssignment.<CoverageAssignment>find("coverage", coverage)
                .firstResult().productionCode;
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = LineStatus.EARNED;
        line.amount = new BigDecimal(amount);
        line.contract = coverage.contract;
        line.coverage = coverage;
        line.originProductionCode = pc;
        line.attachmentMonth = period.periodMonth;
        line.persist();
    }

    /**
     * CR-65 (cycle comptable) : l'exemple de la §10.2 — une provision de prévisionnel de 180 €, puis un
     * encaissement commissionné à 15 € (reconnaissance de l'acquisition puis du payable), enfin le
     * paiement de 15 €, toutes les écritures équilibrées. La ligne `FORECAST` et la ligne `EARNED` (dotée
     * d'un code de production pour alimenter un compte et un ordre) sont posées comme état d'entrée.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr65(LegalEntity entity, LocalDate periodMonth) {
        Period period = period(entity, periodMonth);
        forecastLine(entity, period, "180.00", null);
        Product product = product(entity, "SANTE_SELECT");
        earnedOnCode(entity, period, coverage(entity, "CR65", product, periodMonth, "P-CR65", null), "15.00");
    }

    /**
     * CR-66 (résiliation, vision comptable) : une résiliation au 6e mois régularise le prévisionnel de
     * −90 € ; à la validation métier, l'écriture d'ajustement du prévisionnel de 90,00 € contre-passe la
     * provision (débit provision, crédit charge prévisionnelle). Les lignes `FORECAST` sont posées
     * directement, comme état d'entrée du scénario.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr66(LegalEntity entity, LocalDate periodMonth) {
        Period period = period(entity, periodMonth);
        CommissionLine origin = forecastLine(entity, period, "180.00", null);
        forecastLine(entity, period, "-90.00", origin);
    }

    /**
     * Poses a forecast commission line on a period, optionally pointing at the origin line it adjusts.
     *
     * @param entity     the entity
     * @param period     the period
     * @param amount     the signed amount
     * @param originLine the line this one adjusts, or null
     * @return the created forecast line
     */
    private CommissionLine forecastLine(LegalEntity entity, Period period, String amount, CommissionLine originLine) {
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = LineStatus.FORECAST;
        line.amount = new BigDecimal(amount);
        line.originLine = originLine;
        line.attachmentMonth = period.periodMonth;
        line.persist();
        return line;
    }

    /**
     * CR-63 (seuil de paiement) : une prime de 50 € à 15 % produit une commission de 7,50 €, sous le
     * seuil minimal de paiement de l'entité (10,00 €) ; une fois la période préparée au paiement, le
     * bénéficiaire n'a pas d'ordre et figure en exclusion « sous le seuil », son solde restant acquis.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr63(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR63", product, periodMonth, "P-CR63", null);
        linearChoice(coverage, "0.15", periodMonth, null);
        version(entity, "PLAN-CR63", "R-CR63", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        premium(entity, coverage, "CR63-E1", periodMonth.plusDays(4), "50.00");
    }

    /** The UMR insurer-file mapping: keep only the ALPTIS mutuelle's lines and derive the received commission. */
    private static final String UMR_MAPPING = "{"
            + "\"filter\": {\"column\": \"NOM_MUTUELLE\", \"equals\": \"ALPTIS\"},"
            + "\"fields\": {"
            + "\"source_key\": {\"column\": \"CODE_MOUVEMENT\"},"
            + "\"event_type\": {\"column\": \"TYPE_MOUVEMENT\", \"transcode\": {\"VI\": \"INITIAL_PAYMENT\","
            + " \"FG\": \"OUTSTANDING_FEE\"}},"
            + "\"contract_number\": {\"column\": \"NUMERO_CONTRAT\"},"
            + "\"coverage_code\": {\"column\": \"NUMERO_CONTRAT\"},"
            + "\"event_date\": {\"column\": \"DATE_MOUVEMENT\"}},"
            + "\"derived\": [{"
            + "\"when\": {\"column\": \"MONTANT_COMMISSION\"},"
            + "\"fields\": {"
            + "\"source_key\": {\"column\": \"CODE_MOUVEMENT\", \"suffix\": \"-COM\"},"
            + "\"event_type\": {\"const\": \"INSURER_COMMISSION\"},"
            + "\"contract_number\": {\"column\": \"NUMERO_CONTRAT\"},"
            + "\"coverage_code\": {\"column\": \"NUMERO_CONTRAT\"},"
            + "\"event_date\": {\"column\": \"DATE_MOUVEMENT\"},"
            + "\"precomputed_amount\": {\"column\": \"MONTANT_COMMISSION\"}}}]}";

    /**
     * CR-37 (part de l'entité) : sème le contrat UMR `AD-1` et le mapping du fichier de production UMR
     * (flux `EVENTS`, source `UMR`), de sorte qu'un import libre par ce mapping ne garde que les lignes de
     * la mutuelle de l'entité (part Alptis) et dérive la commission reçue.
     *
     * @param entity      the entity
     * @param periodMonth the mapping's start of validity
     */
    private void seedCr37(LegalEntity entity, LocalDate periodMonth) {
        seedUmrFlow(entity, periodMonth);
    }

    /**
     * CR-38 (fichier réel) : au-delà du mapping UMR (CR-37), ajoute une règle entrante `EXTERNAL` de
     * nature `INSURER` qui charge les commissions reçues en lignes ; le fichier de production UMR donne
     * alors des lignes `INSURER` dont le total égale le « montant de commission dû » du bordereau.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr38(LegalEntity entity, LocalDate periodMonth) {
        seedUmrFlow(entity, periodMonth);
        version(entity, Direction.INBOUND, "PLAN-CR38", "R-CR38", RuleFamily.EXTERNAL, LineNature.INSURER,
                "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE_SELECT\"],"
                        + "\"eventType\":[\"INSURER_COMMISSION\"]}",
                "{}", periodMonth, null, null);
    }

    /**
     * Seeds the UMR insurer production flow: the UMR contract `AD-1` and its coverage (keyed by the
     * contract number so the mapped events resolve), and the UMR file mapping on the `events` flow.
     *
     * @param entity      the entity
     * @param periodMonth the mapping's start of validity
     */
    private void seedUmrFlow(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "UMR";
        contract.contractNumber = "AD-1";
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = "AD-1";
        coverage.effectDate = periodMonth;
        coverage.persist();
        ImportMapping mapping = new ImportMapping();
        mapping.entity = entity;
        mapping.flowCode = "events";
        mapping.sourceSystem = "UMR";
        mapping.versionNo = 1;
        mapping.definition = UMR_MAPPING;
        mapping.validFrom = periodMonth;
        mapping.persist();
    }

    /**
     * CR-67 (données erronées à l'import) : sème le contrat `AD-1` (garantie `C1`, système source CLEVA)
     * que vise le fichier d'événements, pour que la ligne valide s'intègre, les trois lignes fausses
     * soient rejetées et la ligne au contrat inconnu soit mise en attente.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (unused beyond the coverage effect year)
     */
    private void seedCr67(LegalEntity entity, LocalDate periodMonth) {
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "CLEVA";
        contract.contractNumber = "AD-1";
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = "C1";
        coverage.effectDate = LocalDate.of(periodMonth.getYear(), 1, 1);
        coverage.persist();
    }

    /**
     * CR-73 (prorata à la frontière de la première année, §6.2) : garantie à effet du 1er mars 2026,
     * précompté 30/10, prime trimestrielle de 300 € couvrant du 1er février au 30 avril 2027 ; la part
     * d'apport est proratisée au nombre de jours dans la première année (28 jours de février sur 89) —
     * une ligne {@code ACQUISITION} de 28,31 € et une ligne {@code SERVICING} de 30,00 €. Le calcul tourne
     * dans la période de février 2027 (le mois de l'événement).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (February 2027)
     */
    private void seedCr73(LegalEntity entity, LocalDate periodMonth) {
        LocalDate effect = LocalDate.of(2026, 3, 1);
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR73", product, effect, "P-CR73", null);
        upfrontChoice(coverage, "0.30", "0.10", effect);
        version(entity, "PLAN-CR73", "R-CR73", RuleFamily.UPFRONT, LineNature.ACQUISITION, premiumScope(entity),
                UPFRONT_PARAMS, effect, null, null);
        BusinessEvent e = premium(entity, coverage, "CR73-E1", periodMonth, "300.00");
        e.periodFrom = LocalDate.of(2027, 2, 1);
        e.periodTo = LocalDate.of(2027, 4, 30);
    }

    /**
     * CR-74 (changement de taux en cours de période couverte, P12) : garantie passée de 15 % à 18 % le
     * 16 juin ; la prime de juin de 100 € (couvrant le mois entier) est partagée au prorata des jours —
     * 15 jours à 15 % (7,50 €) et 15 jours à 18 % (9,00 €) — en deux lignes {@code LINEAR}.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (June 2026)
     */
    private void seedCr74(LegalEntity entity, LocalDate periodMonth) {
        LocalDate boundary = LocalDate.of(2026, 6, 16);
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR74", product, periodMonth, "P-CR74", null);
        linearChoice(coverage, "0.15", periodMonth, boundary.minusDays(1));
        linearChoice(coverage, "0.18", boundary, null);
        version(entity, "PLAN-CR74", "R-CR74", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15,0.18]}", periodMonth, null, null);
        BusinessEvent e = premium(entity, coverage, "CR74-E1", LocalDate.of(2026, 6, 1), "100.00");
        e.periodFrom = LocalDate.of(2026, 6, 1);
        e.periodTo = LocalDate.of(2026, 6, 30);
    }

    /**
     * CR-75 (groupes d'exclusion et priorités, §6.11) : une règle dérogatoire de priorité supérieure
     * remplace la règle de base dans le groupe {@code BASE} (18 % au lieu de 15 %), et une opération de
     * rentrée d'un autre groupe ajoute 2 % ; le courtier reçoit 18,00 € et 2,00 € en deux lignes.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr75(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR75", product, periodMonth, "PX-CR75", null);
        linearChoice(coverage, "0.18", periodMonth, null);
        version(entity, "PLAN-CR75-BASE", "R-CR75-BASE", RuleFamily.LINEAR, LineNature.LINEAR,
                premiumScope(entity), "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        RuleVersion derog = version(entity, "PLAN-CR75-DEROG", "R-CR75-DEROG", RuleFamily.LINEAR,
                LineNature.LINEAR,
                "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE_SELECT\"],"
                        + "\"distributor\":[\"PX-CR75\"]}",
                "{\"allowedRates\":[0.18]}", periodMonth, null, null);
        derog.priority = 30;
        RuleVersion bonus = version(entity, "PLAN-CR75-BONUS", "R-CR75-BONUS", RuleFamily.LINEAR,
                LineNature.LINEAR, "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE_SELECT\"]}",
                "{\"rateSource\":\"RULE\",\"rate\":0.02}", periodMonth, null, null);
        bonus.exclusionGroup = "BONUS_RENTREE";
        premium(entity, coverage, "CR75-E1", periodMonth.plusDays(4), "100.00");
    }

    /**
     * CR-77 (frais de dossier, §6.5) : 300 € de frais saisis à la souscription et encaissés ; une ligne
     * {@code FEE} de 300,00 €, sans taux ni politique de reprise.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr77(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR77", product, periodMonth, "P-CR77", null);
        version(entity, "PLAN-CR77", "R-CR77", RuleFamily.FIXED, LineNature.FEE, premiumScope(entity),
                "{\"amount\":300}", periodMonth, null, null);
        premium(entity, coverage, "CR77-E1", periodMonth.plusDays(4), "0.00");
    }

    /**
     * CR-82 (commission sur encours) : encours moyen de 20 000 € en mode linéaire sur un fichier
     * trimestriel à 0,30 % annuel ; une ligne {@code OUTSTANDING} de 15,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr82(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "PER");
        Coverage coverage = coverage(entity, "CR82", product, periodMonth, "P-CR82", null);
        version(entity, "PLAN-CR82", "R-CR82", RuleFamily.OUTSTANDING, LineNature.OUTSTANDING,
                perScope(entity, "OUTSTANDING_BALANCE"), "{\"annualRate\":0.003}", periodMonth, null, null);
        BusinessEvent e = event(entity, coverage, "CR82-E1", EventType.OUTSTANDING_BALANCE,
                LocalDate.of(periodMonth.getYear(), 1, 31), "20000.00");
        e.periodFrom = LocalDate.of(periodMonth.getYear(), 1, 1);
        e.periodTo = LocalDate.of(periodMonth.getYear(), 3, 31);
    }

    /**
     * CR-83 (arbitrage) : deux arbitrages de 10 000 € dans la même année civile ; rien sur le premier,
     * une ligne {@code ARBITRAGE} de 30,00 € sur le second (0,30 % de 10 000 €, rang minimal 2).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr83(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "PER");
        Coverage coverage = coverage(entity, "CR83", product, periodMonth, "P-CR83", null);
        version(entity, "PLAN-CR83", "R-CR83", RuleFamily.LINEAR, LineNature.ARBITRAGE,
                perScope(entity, "ARBITRAGE"),
                "{\"rateSource\":\"RULE\",\"rate\":0.003,\"minRankInCalendarYear\":2}", periodMonth, null, null);
        event(entity, coverage, "CR83-A1", EventType.ARBITRAGE, LocalDate.of(periodMonth.getYear(), 3, 1),
                "10000.00");
        event(entity, coverage, "CR83-A2", EventType.ARBITRAGE, LocalDate.of(periodMonth.getYear(), 6, 1),
                "10000.00");
    }

    /** Parameters of a borrower upfront rule: 30/10, 36-month early-repayment window, 15 % recalc rate. */
    private static final String EMPRUNTEUR_PARAMS =
            "{\"allowedRates\":[{\"acquisition\":0.3,\"servicing\":0.1}],\"firstYearDays\":365,"
            + "\"earlyRepaymentMonths\":36,\"recalculationRate\":0.15}";

    /**
     * CR-81 (rachat anticipé d'un prêt, §6.5) : garantie emprunteur précomptée 30/10, deux primes
     * annuelles de 1 000 € (mars 2026 et mars 2027), rachat anticipé au 1er janvier 2028 dans la fenêtre
     * de 36 mois ; le recalcul linéaire produit une ligne {@code CLAWBACK} de −200,00 €. Au-delà de la
     * fenêtre, aucune ligne (variante prouvée au niveau moteur).
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr81(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR81", product, periodMonth, "P-CR81", null);
        upfrontChoice(coverage, "0.30", "0.10", periodMonth);
        version(entity, "PLAN-CR81", "R-CR81", RuleFamily.UPFRONT, LineNature.ACQUISITION, premiumScope(entity),
                EMPRUNTEUR_PARAMS, periodMonth, null, null);
        premium(entity, coverage, "CR81-Y1", LocalDate.of(2026, 3, 1), "1000.00");
        premium(entity, coverage, "CR81-Y2", LocalDate.of(2027, 3, 1), "1000.00");
        event(entity, coverage, "CR81-BUYBACK", EventType.EARLY_REPAYMENT, LocalDate.of(2028, 1, 1), null);
    }

    /**
     * CR-79 (délai d'anticipation) : garantie escomptée à effet du 1er juin 2026, inscrite le 15 janvier,
     * convention à délai d'anticipation de 120 jours ; l'escompte n'est pas dû en janvier mais l'est dans
     * la période de février 2026 (120 jours avant l'effet) — deux lignes {@code ADVANCE} (apport 300,00 €
     * et suivi 100,00 €) sur une prime annualisée de 1 000 €. Le calcul tourne sur février 2026.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (February 2026)
     */
    private void seedCr79(LegalEntity entity, LocalDate periodMonth) {
        Coverage coverage = escompteCoverage(entity, LocalDate.of(2026, 6, 1), "CR79", "P-CR79", "1000");
        coverage.contract.registrationDate = LocalDate.of(2026, 1, 15);
        AdvanceAgreement agreement = convention(entity, "P-CR79", 100, LocalDate.of(2026, 12, 31));
        agreement.anticipationDays = 120;
    }

    /**
     * CR-76 (co-courtage et groupement) : commission de 100 € (taux 100 %) ; courtier A à 60 %, courtier B
     * co-courtier à 40 % ; A reversé à 90 % et 10 % à son groupement. Donne 54,00 € (A), 6,00 € (groupement)
     * et 40,00 € (B). Une annulation ultérieure de l'encaissement reprend les trois lignes : −54,00 €,
     * −6,00 € et −40,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr76(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "CR76", product, periodMonth, "P-CR76-A", null);
        CoverageAssignment mainA = CoverageAssignment.<CoverageAssignment>find("coverage", coverage).firstResult();
        mainA.share = new BigDecimal("0.6");
        Distributor a = mainA.productionCode.distributor;
        DistributorGroup ga = new DistributorGroup();
        ga.code = "G-CR76";
        ga.label = "G-CR76";
        ga.persist();
        payeeRule(entity, mainA.productionCode, a, "0.9", ga, "0.1");
        Distributor b = new Distributor();
        b.partnerCode = "P-CR76-B";
        b.legalName = "P-CR76-B";
        b.persist();
        ProductionCode pcB = new ProductionCode();
        pcB.entity = entity;
        pcB.code = "PC-CR76-B";
        pcB.distributor = b;
        pcB.persist();
        CoverageAssignment coBroker = new CoverageAssignment();
        coBroker.coverage = coverage;
        coBroker.productionCode = pcB;
        coBroker.role = AssignmentRole.CO_BROKER;
        coBroker.share = new BigDecimal("0.4");
        coBroker.validFrom = periodMonth;
        coBroker.persist();
        linearChoice(coverage, "1", periodMonth, null);
        version(entity, "PLAN-CR76", "R-CR76", RuleFamily.LINEAR, LineNature.LINEAR,
                "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE\"],\"eventType\":[\"PREMIUM_COLLECTED\"]}",
                "{\"allowedRates\":[1]}", periodMonth, null, null);
        BusinessEvent premium = event(entity, coverage, "CR76-E1", EventType.PREMIUM_COLLECTED,
                periodMonth.plusDays(14), "100");
        BusinessEvent reversal = event(entity, coverage, "CR76-E2", EventType.PREMIUM_CANCELLED,
                periodMonth.plusDays(19), null);
        reversal.reversesEvent = premium;
    }

    /**
     * INT-17 (parcours §4.1) : radiation puis réinscription d'un distributeur à leur date d'effet. Le
     * distributeur porte une commission payable dans deux périodes consécutives ; un lien `TERMINATED`
     * couvre la première (paiement bloqué, « bénéficiaire inactif ») et un lien `ACTIVE` prend effet à
     * la seconde (paiement débloqué). Les coordonnées bancaires et la ligne acquise sont posées en entrée.
     *
     * @param entity      the entity
     * @param periodMonth the first (blocked) month; the second month reinstates the distributor
     */
    private void seedInt17(LegalEntity entity, LocalDate periodMonth) {
        LocalDate second = periodMonth.plusMonths(1);
        Period first = period(entity, periodMonth);
        Period later = period(entity, second);
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "INT17", product, periodMonth, "P-INT17", null);
        ProductionCode pc = CoverageAssignment.<CoverageAssignment>find("coverage", coverage)
                .firstResult().productionCode;
        Distributor distributor = pc.distributor;
        // Terminated over the first month, reinstated (active) from the second — date-effective (REF-08).
        DistributorEntity terminated = new DistributorEntity();
        terminated.distributor = distributor;
        terminated.entity = entity;
        terminated.status = PartnerStatus.TERMINATED;
        terminated.validFrom = periodMonth.minusMonths(1);
        terminated.validTo = second.minusDays(1);
        terminated.persist();
        DistributorEntity active = new DistributorEntity();
        active.distributor = distributor;
        active.entity = entity;
        active.status = PartnerStatus.ACTIVE;
        active.validFrom = second;
        active.persist();
        BankAccount bank = new BankAccount();
        bank.entity = entity;
        bank.holderType = PayeeType.PRODUCTION_CODE;
        bank.holderId = pc.getId();
        bank.iban = "FR7630001007941234567890185";
        bank.bic = "AGRIFRPP";
        bank.holderName = pc.code;
        bank.validFrom = periodMonth.minusMonths(1);
        bank.persist();
        earnedOnCode(entity, first, coverage, "100.00");
        earnedOnCode(entity, later, coverage, "100.00");
    }

    /**
     * INT-20 (parcours §4.1) : réaffectation d'une garantie par un ordre de remplacement reçu du CRM
     * (REF-09). Une garantie dont le code de production principal est {@code PC-INT20} ; un ordre de
     * remplacement effectif au premier jour de la période la réaffecte à {@code PC-INT20-B}. Le calcul
     * applique l'ordre : l'affectation d'origine est clôturée la veille de l'effet, une nouvelle
     * affectation prend effet ce jour-là. La fiche de garantie montre alors l'historique des deux
     * affectations, l'écran des cessions l'ordre lui-même.
     *
     * @param entity      the legal entity the replacement order is received for
     * @param periodMonth the first day of the calculation period, also the order's effect date
     */
    private void seedInt20(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "INT20", product, periodMonth, "P-INT20-A", null);
        ProductionCode from = CoverageAssignment.<CoverageAssignment>find("coverage", coverage)
                .firstResult().productionCode;
        // The transferee's production code, carried by a second distributor.
        Distributor transferee = new Distributor();
        transferee.partnerCode = "P-INT20-B";
        transferee.legalName = "P-INT20-B";
        transferee.persist();
        ProductionCode to = new ProductionCode();
        to.entity = entity;
        to.code = "PC-INT20-B";
        to.distributor = transferee;
        to.persist();
        // The replacement order reassigns the contract's main coverage at the period's first day.
        ReplacementOrder order = new ReplacementOrder();
        order.contract = coverage.contract;
        order.fromProductionCode = from;
        order.toProductionCode = to;
        order.receivedDate = periodMonth.minusDays(1);
        order.effectDate = periodMonth;
        order.persist();
    }

    /**
     * INT-22 (parcours §4.1) : un code externe inconnu met l'événement en attente, et une correspondance
     * ajoutée le débloque (REF-04). Une adhésion interne {@code INT22} avec sa garantie, son choix de
     * rémunération et une règle linéaire ; un événement qui la désigne par le code {@code EXT-INT22} d'un
     * système source, inconnu tant que la correspondance n'est pas saisie, est mis en attente sur une
     * référence inconnue — jamais rejeté. Une fois le code rattaché à {@code INT22}, le calcul suivant
     * re-résout l'événement et produit sa ligne (100,00 € × 0,15 = 15,00 €).
     *
     * @param entity      the entity the event and contract belong to
     * @param periodMonth the first day of the calculation period
     */
    private void seedInt22(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "INT22", product, periodMonth, "P-INT22", null);
        linearChoice(coverage, "0.15", periodMonth, null);
        version(entity, "PLAN-INT22", "R-INT22-LIN", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        // The event references the contract by the source system's own identifier, unknown until mapped:
        // held on an unknown reference, keeping the identifiers to re-resolve against once the code arrives.
        BusinessEvent held = new BusinessEvent();
        held.entity = entity;
        held.sourceSystem = "SYS";
        held.sourceKey = "INT22-E1";
        held.eventType = EventType.PREMIUM_COLLECTED;
        held.eventDate = periodMonth.plusDays(4);
        held.receivedDate = periodMonth.plusDays(4);
        held.basisAmount = new BigDecimal("100.00");
        held.processingStatus = ProcessingStatus.ON_HOLD;
        held.holdReason = HoldReason.UNKNOWN_REFERENCE;
        held.pendingContractNumber = "EXT-INT22";
        held.pendingCoverageCode = "INT22-C1";
        held.persist();
    }

    /**
     * INT-23 (parcours §4.1) : indicateurs de qualité reçus et calculés en interne, par distributeur et
     * par période (REF-07). Un distributeur porte deux garanties en vigueur dont l'une est en état
     * d'impayé : le calcul en déduit un taux d'impayés de 50 % (source « calculé »). À côté, un taux de
     * réclamations reçu du CRM (source « reçu ») illustre la coexistence des deux origines sur l'écran.
     *
     * @param entity      the entity the indicators are computed for
     * @param periodMonth the first day of the calculation period
     */
    private void seedInt23(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        Product product = product(entity, "SANTE_SELECT");
        Coverage inForce = coverage(entity, "INT23A", product, periodMonth, "P-INT23", null);
        inForce.status = ContractStatus.IN_FORCE;
        Coverage unpaid = coverage(entity, "INT23B", product, periodMonth, "P-INT23", null);
        unpaid.status = ContractStatus.IN_FORCE;
        // One of the two in-force garanties carries an unpaid notice, so its distributor's unpaid rate is 50 %.
        event(entity, unpaid, "INT23-UNPAID", EventType.UNPAID_NOTICE, periodMonth.plusDays(3), null);
        // A complaint rate received from the CRM, shown next to the internally computed unpaid rate.
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-INT23").firstResult();
        indicator(entity, distributor, QualityIndicatorType.COMPLAINT_RATE, "0.03", periodMonth.getYear());
    }

    /**
     * PAR-04 (parcours §4.2) : passage du temps d'une version de règle. Une version approuvée dont la
     * fenêtre de validité court du 1er janvier au 30 juin 2026 : son statut affiché suit l'horloge, sans
     * intervention — « En vigueur » tant que la date du jour est dans la fenêtre, « Retirée » une fois le
     * 30 juin passé. Le parcours gèle l'horloge avant puis après la fin pour l'observer.
     *
     * @param entity      the entity the rule belongs to
     * @param periodMonth unused; the validity window is fixed to the first half of 2026
     */
    private void seedPar04(LegalEntity entity, LocalDate periodMonth) {
        version(entity, "PAR04-PLAN", "PAR04-R", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), null);
    }

    /**
     * PAR-06 (parcours §4.2) : périmètre d'une version de règle sur chacun des critères de la section
     * 5.4. Une version linéaire dont le périmètre conjugue entité, gamme, produit, option, type de tarif,
     * collège, assureur, mode de rémunération, canal de vente, distributeur, type de distributeur et
     * groupement. Une garantie qui coche tous ces critères est couverte (ligne de 15,00 €) ; une garantie
     * identique sauf le type de tarif (réduit au lieu de standard) n'est pas couverte (aucune ligne).
     *
     * @param entity      the entity the rule and garanties belong to
     * @param periodMonth the first day of the calculation period
     */
    private void seedPar06(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        ProductRange range = new ProductRange();
        range.code = "PAR06-RNG";
        range.label = "Gamme PAR06";
        range.target = ProductTarget.INDIVIDUAL;
        range.persist();
        Insurer insurer = new Insurer();
        insurer.code = "PAR06-INS";
        insurer.name = "Assureur PAR06";
        insurer.role = InsurerRole.RISK_CARRIER;
        insurer.persist();
        Product product = product(entity, "PAR06-PROD");
        product.range = range;
        product.insurer = insurer;
        ProductOption option = new ProductOption();
        option.product = product;
        option.code = "PAR06-OPT";
        option.label = "Option PAR06";
        option.optionType = OptionType.BASE;
        option.persist();
        DistributorGroup group = new DistributorGroup();
        group.code = "PAR06-GRP";
        group.label = "Groupement PAR06";
        group.persist();

        // The rule's perimeter conjoins every criterion of section 5.4 with equality semantics.
        String scope = "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"PAR06-PROD\"],\"range\":[\"PAR06-RNG\"],"
                + "\"insurer\":[\"PAR06-INS\"],\"option\":[\"PAR06-OPT\"],\"tariffType\":[\"STANDARD\"],"
                + "\"college\":[\"EXECUTIVE\"],\"salesChannel\":[\"ALPTIS_CONNECT\"],\"remunerationMode\":[\"LINEAR\"],"
                + "\"distributor\":[\"P-PAR06\"],\"distributorType\":[\"INDEPENDENT\"],\"group\":[\"PAR06-GRP\"],"
                + "\"eventType\":[\"PREMIUM_COLLECTED\"]}";
        version(entity, "PAR06-PLAN", "PAR06-R", RuleFamily.LINEAR, LineNature.LINEAR, scope,
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);

        // Covered garantie: it carries every scoped value; its event yields a line.
        Coverage covered = coverage(entity, "PAR06C", product, periodMonth, "P-PAR06", StaffCategory.EXECUTIVE);
        covered.tariffType = TariffType.STANDARD;
        covered.option = option;
        covered.contract.salesChannel = SalesChannel.ALPTIS_CONNECT;
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-PAR06").firstResult();
        distributor.distributorType = DistributorType.INDEPENDENT;
        distributor.group = group;
        linearChoice(covered, "0.15", periodMonth, null);
        premium(entity, covered, "PAR06C-E", periodMonth.plusDays(4), "100.00");

        // Not covered: the same garantie but a reduced tariff — outside the STANDARD-tariff perimeter.
        Coverage notCovered = coverage(entity, "PAR06N", product, periodMonth, "P-PAR06", StaffCategory.EXECUTIVE);
        notCovered.tariffType = TariffType.REDUCED;
        notCovered.option = option;
        notCovered.contract.salesChannel = SalesChannel.ALPTIS_CONNECT;
        linearChoice(notCovered, "0.15", periodMonth, null);
        premium(entity, notCovered, "PAR06N-E", periodMonth.plusDays(4), "100.00");
    }

    /**
     * PAR-08 (parcours §4.2) : barème à paliers en mode marginal. Une surcommission de développement
     * (RCD) dont le barème santé marginal rémunère chaque tranche à son taux : une production nouvelle
     * annualisée de 60 000 € donne 25 000 € × 3 % + 15 000 € × 4 % = 1 350,00 €. La clôture de
     * développement écrit cette ligne ; les montants juste sous, sur et au-dessus de chaque borne sont
     * épinglés par SurcommissionLogicTest et SavingsFamilyProposeTest.
     *
     * @param entity      the entity the rule and distributor belong to
     * @param periodMonth the development close month
     */
    private void seedPar08(LegalEntity entity, LocalDate periodMonth) {
        int year = periodMonth.getYear();
        period(entity, periodMonth);
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "PAR08", product, LocalDate.of(year, 1, 10), "P-PAR08", null);
        coverage.annualPremium = new BigDecimal("60000");
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-PAR08").firstResult();
        RuleVersion version = version(entity, "PLAN-PAR08", "R-PAR08", RuleFamily.TIERED, LineNature.RCD,
                santeScope(entity), "{\"counterCode\":\"RCD\",\"calculationFrequency\":\"MONTHLY\"}",
                LocalDate.of(year, 1, 1), null, null);
        santeDevelopmentScale(version);
        condition(version, QualityIndicatorType.UNPAID_RATE, ConditionOperator.LT, "0.05");
        enrol(version.rule, distributor, AgreementType.RCD_AMENDMENT, true);
        indicator(entity, distributor, QualityIndicatorType.UNPAID_RATE, "0.02", year);
        event(entity, coverage, "PAR08-SUB", EventType.SUBSCRIPTION, LocalDate.of(year, 1, 15), null);
    }

    /**
     * PAR-09 (parcours §4.2) : politique de reprise d'une règle précomptée. Trois garanties précomptées,
     * chacune sous une règle à politique de reprise distincte, chacune produisant un apport de 300,00 €
     * puis résiliée : sans reprise, aucune ligne négative ; par paliers d'ancienneté (100 % la première
     * année), reprise de −300,00 € ; au prorata sur 24 mois à 12 mois écoulés, reprise de −150,00 €. Les
     * événements en attente se calculent tous dans la même exécution (l'âge vient de la date d'effet).
     *
     * @param entity      the entity the rules and garanties belong to
     * @param periodMonth the calculation period
     */
    private void seedPar09(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        Product product = product(entity, "SANTE_SELECT");
        // No clawback policy: a termination claws nothing back.
        par09Coverage(entity, product, periodMonth, "N", null, periodMonth.plusMonths(5));
        // Age-step clawback, 100% in the first year: a termination at ~151 days claws the whole acquisition.
        par09Coverage(entity, product, periodMonth, "A",
                "{\"type\":\"AGE_STEPS\",\"natures\":[\"ACQUISITION\"],\"triggers\":[\"TERMINATION\"],"
                        + "\"steps\":[{\"maxDays\":365,\"rate\":1.0}]}",
                periodMonth.plusMonths(5));
        // Pro-rata clawback over 24 months: a termination at ~380 days (12 months elapsed) claws half.
        par09Coverage(entity, product, periodMonth, "P",
                "{\"type\":\"PRO_RATA\",\"months\":24,\"natures\":[\"ACQUISITION\"],\"triggers\":[\"TERMINATION\"]}",
                periodMonth.plusDays(380));
    }

    /**
     * Seeds one precomptée garantie for PAR-09: an upfront rule scoped to its own distributor with the
     * given clawback policy, a first-year premium yielding a 300,00 € acquisition, and a termination.
     *
     * @param entity      the entity
     * @param product     the product
     * @param periodMonth the effect date and period
     * @param suffix      the garantie suffix (N, A, P), also the distributor's
     * @param clawback    the clawback policy JSON, or null for none
     * @param termination the termination date, which fixes the clawback age
     */
    private void par09Coverage(LegalEntity entity, Product product, LocalDate periodMonth, String suffix,
            String clawback, LocalDate termination) {
        Coverage coverage = coverage(entity, "PAR09" + suffix, product, periodMonth, "P-PAR09" + suffix, null);
        upfrontChoice(coverage, "0.30", "0.10", periodMonth);
        String scope = "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE_SELECT\"],"
                + "\"eventType\":[\"PREMIUM_COLLECTED\"],\"distributor\":[\"P-PAR09" + suffix + "\"]}";
        version(entity, "PLAN-PAR09" + suffix, "R-PAR09" + suffix, RuleFamily.UPFRONT, LineNature.ACQUISITION, scope,
                UPFRONT_PARAMS, periodMonth, null, clawback);
        premium(entity, coverage, "PAR09" + suffix + "-Y1", periodMonth.plusDays(14), "1000.00");
        event(entity, coverage, "PAR09" + suffix + "-END", EventType.TERMINATION, termination, null);
    }

    /**
     * PAR-12 (parcours §4.2) : cycle de vie d'une convention d'escompte. Une convention valide et une
     * garantie escomptée : le calcul ouvre une position d'escompte. La convention suspendue n'ouvre plus
     * de nouvelle position ; clôturée, elle ferme la position en cours (motif {@code AGREEMENT_CLOSED}) ;
     * son échéance bloque toute nouvelle position (fenêtre de validité, déjà appliquée par le moteur).
     *
     * @param entity      the entity the convention belongs to
     * @param periodMonth the calculation period and effect date
     */
    private void seedPar12(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        escompteCoverage(entity, periodMonth, "PAR12", "P-PAR12", "1000");
        convention(entity, "P-PAR12", 100, LocalDate.of(periodMonth.getYear(), 12, 31));
    }

    /**
     * CAL-04 (parcours §4.3) : recalcul partiel scopé à un distributeur. Deux distributeurs, chacun une
     * garantie linéaire : le calcul complet produit une ligne de 15,00 € (P-CAL04A) et une de 20,00 €
     * (P-CAL04B). Un recalcul partiel scopé au distributeur P-CAL04A ne retraite que sa garantie (une
     * seule unité) ; la ligne de P-CAL04B ne change pas.
     *
     * @param entity      the entity the garanties belong to
     * @param periodMonth the calculation period
     */
    private void seedCal04(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        Product product = product(entity, "SANTE_SELECT");
        version(entity, "PLAN-CAL04", "R-CAL04-LIN", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15,0.20]}", periodMonth, null, null);
        Coverage a = coverage(entity, "CAL04A", product, periodMonth, "P-CAL04A", null);
        linearChoice(a, "0.15", periodMonth, null);
        premium(entity, a, "CAL04A-E", periodMonth.plusDays(4), "100.00");
        Coverage b = coverage(entity, "CAL04B", product, periodMonth, "P-CAL04B", null);
        linearChoice(b, "0.20", periodMonth, null);
        premium(entity, b, "CAL04B-E", periodMonth.plusDays(4), "100.00");
    }

    /**
     * CAL-06 (parcours §4.3) : événement sans règle de base. Une garantie avec son choix linéaire et un
     * encaissement, mais aucune règle : l'événement est mis en attente sur « Aucune règle », sans ligne.
     * La règle ajoutée (scénario {@code CAL-06-RULE}) puis le calcul relancé, l'événement est re-traité et
     * produit sa ligne de 15,00 € — une mise en attente due à la configuration est rejouée à chaque calcul.
     *
     * @param entity      the entity the garantie belongs to
     * @param periodMonth the calculation period
     */
    private void seedCal06(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CAL06", product, periodMonth, "P-CAL06", null);
        linearChoice(coverage, "0.15", periodMonth, null);
        premium(entity, coverage, "CAL06-E", periodMonth.plusDays(4), "100.00");
    }

    /**
     * CAL-06 complement: adds the linear base rule the {@link #seedCal06} garantie was missing, so a
     * recalculation releases its held event.
     *
     * @param entity      the entity
     * @param periodMonth the calculation period
     */
    private void seedCal06Rule(LegalEntity entity, LocalDate periodMonth) {
        version(entity, "PLAN-CAL06", "R-CAL06-LIN", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
    }

    /**
     * CAL-08 (parcours §4.3) : garantie escomptée en co-courtage, cédée en cours de période. Une seule
     * garantie partagée entre un apporteur principal (60 %) et un co-courtier (40 %) : le moteur la traite
     * en une seule unité, l'escompte ouvre une position unique, et les lignes se répartissent sur les deux
     * comptes au prorata des parts. Une cession mi-période réaffecte l'apporteur principal à un repreneur,
     * sans multiplier l'unité ni la position.
     *
     * @param entity      the entity the garantie belongs to
     * @param periodMonth the calculation period and effect date
     */
    private void seedCal08(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        Coverage coverage = escompteCoverage(entity, periodMonth, "CAL08", "P-CAL08A", "1200");
        convention(entity, "P-CAL08A", 100, LocalDate.of(periodMonth.getYear(), 12, 31));
        // Co-courtage: the main broker keeps 60 %, a co-broker takes 40 %.
        CoverageAssignment main = CoverageAssignment.<CoverageAssignment>find("coverage", coverage).firstResult();
        main.share = new BigDecimal("0.6");
        Distributor coBroker = new Distributor();
        coBroker.partnerCode = "P-CAL08B";
        coBroker.legalName = "Co-courtier";
        coBroker.persist();
        ProductionCode coPc = new ProductionCode();
        coPc.entity = entity;
        coPc.code = "PC-CAL08B";
        coPc.distributor = coBroker;
        coPc.persist();
        CoverageAssignment co = new CoverageAssignment();
        co.coverage = coverage;
        co.productionCode = coPc;
        co.role = AssignmentRole.CO_BROKER;
        co.share = new BigDecimal("0.4");
        co.validFrom = periodMonth;
        co.persist();
        premium(entity, coverage, "CAL08-E1", periodMonth.plusDays(14), "500.00");
        // Ceded mid-period: the main broker's portfolio is transferred to a new broker, debt repaid.
        Distributor repreneur = new Distributor();
        repreneur.partnerCode = "P-CAL08C";
        repreneur.legalName = "Repreneur";
        repreneur.persist();
        ProductionCode repPc = new ProductionCode();
        repPc.entity = entity;
        repPc.code = "PC-CAL08C";
        repPc.distributor = repreneur;
        repPc.persist();
        PortfolioTransfer transfer = new PortfolioTransfer();
        transfer.entity = entity;
        transfer.fromDistributor = main.productionCode.distributor;
        transfer.toDistributor = repreneur;
        transfer.effectDate = periodMonth.plusMonths(1);
        transfer.advanceDebtHandling = AdvanceDebtHandling.REPAID_BY_TRANSFEROR;
        transfer.status = TransferStatus.PENDING;
        transfer.persist();
    }

    /**
     * CAL-09 (parcours §4.3) : plafond d'adhésions d'une convention atteint dans la période. Une
     * convention plafonnée à une adhésion et deux garanties escomptées du même distributeur : l'admission
     * se fait par date d'enregistrement, donc la garantie enregistrée le plus tôt reçoit l'escompte même
     * si elle a été créée après l'autre (identifiant plus grand) ; la suivante est traitée sans escompte.
     *
     * @param entity      the entity the convention belongs to
     * @param periodMonth the calculation period and effect date
     */
    private void seedCal09(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        Product product = product(entity, "SANTE_SELECT");
        version(entity, "PLAN-CAL09", "R-CAL09", RuleFamily.UPFRONT, LineNature.ACQUISITION, premiumScope(entity),
                UPFRONT_PARAMS, periodMonth, null, null);
        Distributor distributor = new Distributor();
        distributor.partnerCode = "P-CAL09";
        distributor.legalName = "P-CAL09";
        distributor.persist();
        // B is created first (lower id) but registered the 10th; A is created second but registered the 5th.
        Coverage b = escompteShared(entity, product, distributor, "CAL09B", periodMonth, periodMonth.plusDays(9));
        Coverage a = escompteShared(entity, product, distributor, "CAL09A", periodMonth, periodMonth.plusDays(4));
        premium(entity, b, "CAL09B-E", periodMonth.plusDays(14), "500.00");
        premium(entity, a, "CAL09A-E", periodMonth.plusDays(14), "500.00");
        convention(entity, "P-CAL09", 1, LocalDate.of(periodMonth.getYear(), 12, 31));
    }

    /**
     * Creates an escompté upfront garantie sharing a given distributor, with its own production code and a
     * registration date, for the contract-cap admission test (CAL-09).
     *
     * @param entity       the entity
     * @param product      the product
     * @param distributor  the shared distributor
     * @param num          the contract number (and code prefix)
     * @param effect       the effect date
     * @param registration the contract registration date
     * @return the created garantie
     */
    private Coverage escompteShared(LegalEntity entity, Product product, Distributor distributor, String num,
            LocalDate effect, LocalDate registration) {
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "SYS";
        contract.contractNumber = num;
        contract.product = product;
        contract.registrationDate = registration;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = num + "-C1";
        coverage.effectDate = effect;
        coverage.annualPremium = new BigDecimal("1000");
        coverage.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = entity;
        pc.code = "PC-" + num;
        pc.distributor = distributor;
        pc.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = AssignmentRole.MAIN;
        assignment.share = BigDecimal.ONE;
        assignment.validFrom = effect;
        assignment.persist();
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.UPFRONT;
        choice.acquisitionRate = new BigDecimal("0.30");
        choice.servicingRate = new BigDecimal("0.10");
        choice.advanceRequested = true;
        choice.validFrom = effect;
        choice.persist();
        return coverage;
    }

    /**
     * CAL-10 (parcours §4.3) : escompte fractionné. Une convention qui verse la moitié de l'escompte à la
     * date d'effet et le solde deux mois après ; deux garanties escomptées (apport total 400,00 € chacune,
     * soit 200,00 € par versement). La première (CAL10A) va au terme : le second versement est écrit à sa
     * date (total escompté 400,00 €). La seconde (CAL10B) est résiliée en février, avant le second
     * versement : la part non versée est annulée, l'escompte reste à 200,00 €, et Δ se calcule sur ce seul
     * montant versé.
     *
     * @param entity      the entity the convention belongs to
     * @param periodMonth the effect date and first period
     */
    private void seedCal10(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        period(entity, periodMonth.plusMonths(1));
        period(entity, periodMonth.plusMonths(2));
        period(entity, periodMonth.plusMonths(3));
        Product product = product(entity, "SANTE_SELECT");
        Distributor distributor = new Distributor();
        distributor.partnerCode = "P-CAL10";
        distributor.legalName = "P-CAL10";
        distributor.persist();
        escompteShared(entity, product, distributor, "CAL10A", periodMonth, periodMonth);
        escompteShared(entity, product, distributor, "CAL10B", periodMonth, periodMonth);
        AdvanceAgreement convention = convention(entity, "P-CAL10", 100,
                LocalDate.of(periodMonth.getYear(), 12, 31));
        convention.split = "{\"firstShare\":0.5,\"balanceMonths\":2}";
    }

    /**
     * CAL-10 complement: terminates the {@link #seedCal10} garantie B, so the next calculation ruptures it
     * before its balance versement falls due — the unversed part is annulled.
     *
     * @param entity      the entity
     * @param periodMonth the effect date and first period
     */
    private void seedCal10End(LegalEntity entity, LocalDate periodMonth) {
        Coverage b = Coverage.<Coverage>find("coverageCode = ?1 and contract.entity = ?2", "CAL10B-C1", entity)
                .firstResult();
        event(entity, b, "CAL10B-END", EventType.TERMINATION, periodMonth.plusMonths(1).plusDays(14), null);
    }

    /**
     * CAL-11 (parcours §4.3) : annulation d'un encaissement apuré. Une garantie escomptée dont un
     * encaissement est imputé (apuré) sur l'escompte, puis annulé : le cumul apuré est diminué d'autant,
     * et la ligne inverse reste hors solde (statut {@code OFFSET}, jamais payée) — elle défait
     * l'apurement, pas un paiement.
     *
     * @param entity      the entity the garantie belongs to
     * @param periodMonth the calculation period and effect date
     */
    private void seedCal11(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth);
        Coverage coverage = escompteCoverage(entity, periodMonth, "CAL11", "P-CAL11", "1000");
        convention(entity, "P-CAL11", 100, LocalDate.of(periodMonth.getYear(), 12, 31));
        BusinessEvent origin = premium(entity, coverage, "CAL11-E1", periodMonth.plusDays(4), "100.00");
        BusinessEvent cancellation = event(entity, coverage, "CAL11-CANCEL", EventType.PREMIUM_CANCELLED,
                periodMonth.plusDays(10), null);
        cancellation.reversesEvent = origin;
    }

    /**
     * CYC-01 (parcours §4.4) : chacune des transitions d'état du cycle (section 7.1). Une période ouverte
     * (sans mois précédent, pour franchir la garde du calcul) que le parcours fait avancer état par état,
     * chaque transition par le rôle habilité, l'historique des transitions gardant la trace.
     *
     * @param entity      the entity the period belongs to
     * @param periodMonth the period month (chosen with no prior period)
     */
    private void seedCyc01(LegalEntity entity, LocalDate periodMonth) {
        Period p = period(entity, periodMonth);
        p.state = PeriodState.OPEN;
    }

    /**
     * CYC-02 (parcours §4.4) : une transition tentée par un rôle non habilité est refusée. Une période
     * contrôlée, dont le passage en validée métier n'est permis qu'au validateur : un autre rôle qui le
     * tente est refusé.
     *
     * @param entity      the entity the period belongs to
     * @param periodMonth the period month
     */
    private void seedCyc02(LegalEntity entity, LocalDate periodMonth) {
        Period p = period(entity, periodMonth);
        p.state = PeriodState.CONTROLLED;
    }

    /**
     * CYC-03 (parcours §4.4) : retour arrière autorisé et refus après validation finance. Une période en
     * « paiement préparé » que la finance renvoie en « contrôlée » (refus finance, avec motif) ; et une
     * période « validée finance », d'où aucun retour arrière n'est permis.
     *
     * @param entity      the entity the periods belong to
     * @param periodMonth the first period month (the second is the following month)
     */
    private void seedCyc03(LegalEntity entity, LocalDate periodMonth) {
        period(entity, periodMonth).state = PeriodState.PAYMENT_PREPARED;
        period(entity, periodMonth.plusMonths(1)).state = PeriodState.FINANCE_VALIDATED;
    }

    /**
     * CYC-04 (parcours §4.4) : deux périodes ouvertes en même temps. La première a sa date d'arrêté
     * fixée au dernier jour de son mois ; une garantie reçoit deux encaissements, l'un reçu avant
     * l'arrêté (il part sur la première période) et l'autre reçu après (il part sur la suivante, section
     * 7.2). Chaque période produit une seule ligne {@code LINEAR} de 15,00 €.
     *
     * @param entity      the entity the garantie belongs to
     * @param periodMonth the first period's month (the second is the following month)
     */
    private void seedCyc04(LegalEntity entity, LocalDate periodMonth) {
        Period first = period(entity, periodMonth);
        first.state = PeriodState.LOADED;
        first.dataCutoffDate = periodMonth.plusMonths(1).minusDays(1);
        Period next = period(entity, periodMonth.plusMonths(1));
        next.state = PeriodState.LOADED;
        next.dataCutoffDate = periodMonth.plusMonths(2).minusDays(1);
        Product product = product(entity, "SANTE_SELECT");
        version(entity, "PLAN-CYC04", "R-CYC04", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        Coverage coverage = coverage(entity, "CYC04", product, periodMonth, "P-CYC04", null);
        linearChoice(coverage, "0.15", periodMonth, null);
        // Received 2027-10-15: on or before the first period's cutoff → first period.
        premium(entity, coverage, "CYC04-IN", periodMonth.plusDays(14), "100.00");
        // Received 2027-11-05: after the first period's cutoff → next period.
        premium(entity, coverage, "CYC04-LATE", periodMonth.plusMonths(1).plusDays(4), "100.00");
    }

    /**
     * CYC-05 (parcours §4.4) : second niveau de validation selon le paramétrage (WKF-02). Un mois dont
     * le total est modeste et proche du mois précédent, sans ajustement, ne demande qu'une validation ;
     * le mois suivant, dont le total dépasse le seuil de second niveau de l'entité (100 000 € par
     * défaut), exige un second validateur distinct. Le mois précédent est validé métier (garde du
     * calcul) ; les lignes sont posées directement comme état d'entrée.
     *
     * @param entity      the entity the periods belong to
     * @param periodMonth the not-required period's month (previous month and next month surround it)
     */
    private void seedCyc05(LegalEntity entity, LocalDate periodMonth) {
        Period previous = period(entity, periodMonth.minusMonths(1));
        previous.state = PeriodState.BUSINESS_VALIDATED;
        forecastLine(entity, previous, "15.00", null);
        Period notRequired = period(entity, periodMonth);
        notRequired.state = PeriodState.CONTROLLED;
        forecastLine(entity, notRequired, "15.00", null);
        Period required = period(entity, periodMonth.plusMonths(1));
        required.state = PeriodState.CONTROLLED;
        forecastLine(entity, required, "150000.00", null);
    }

    /**
     * CTL-01 (parcours §4.4) : une anomalie pour chacune des dix familles de contrôle de la section 8.6.
     * La période reçoit un jeu de données minimal déclenchant un contrôle par famille :
     * {@code COMPLETENESS} (aucun flux importé → flux attendu manquant), {@code DATA_QUALITY} (prime
     * nulle), {@code CALCULATION} (événement en attente « aucune règle »), {@code ADVANCE} (convention
     * d'escompte valide sans distributeur), {@code CLAWBACK} (reprise supérieure à l'acquis d'une
     * garantie), {@code SUPPLEMENT} (surcommission sans accord signé), {@code ACCOUNT} (compte débiteur),
     * {@code PAYMENT} (lot dont le total ne correspond pas à ses ordres), {@code ACCOUNTING} (écriture
     * déséquilibrée) et {@code EXTERNAL_FLOW} (ligne externe dont le montant rompt avec base × taux).
     * Chaque anomalie est affichée avec sa gravité et son constat (valeur observée / attendue).
     *
     * @param entity      the entity the period belongs to
     * @param periodMonth the controlled period's month
     */
    private void seedCtl01(LegalEntity entity, LocalDate periodMonth) {
        Period period = period(entity, periodMonth);
        period.state = PeriodState.CALCULATED;

        // DATA_QUALITY: an event whose premium is non-positive (DATA-PREMIUM).
        controlEvent(entity, period, "CTL01-PREMIUM", BigDecimal.ZERO, ProcessingStatus.PENDING, null, periodMonth);
        // CALCULATION: an event held for lack of a base rule (CALC-NO-RULE).
        controlEvent(entity, period, "CTL01-NORULE", new BigDecimal("100.00"), ProcessingStatus.ON_HOLD,
                HoldReason.NO_RULE, periodMonth);

        // ADVANCE: a valid advance agreement with no member distributor (ADV-NO-MEMBER).
        AdvanceAgreement agreement = new AdvanceAgreement();
        agreement.entity = entity;
        agreement.status = AdvanceAgreementStatus.VALID;
        agreement.persist();

        // CLAWBACK: a coverage whose clawbacks exceed its acquired commissions (CLAWBACK-EXCESS).
        Product product = product(entity, "SANTE_SELECT");
        Coverage clawed = coverage(entity, "CTL01-CB", product, periodMonth, "P-CTL01-CB", null);
        controlLine(entity, period, clawed, LineNature.CLAWBACK, "-100.00", null, null);
        // SUPPLEMENT: a supplement line carrying no signed agreement (SUPP-NO-AGREEMENT).
        controlLine(entity, period, null, LineNature.COMPLEMENT, "50.00", null, null);
        // EXTERNAL_FLOW: an external line whose amount breaks with basis × rate (EXT-EXTERNAL-MISMATCH).
        controlLine(entity, period, null, LineNature.EXTERNAL, "999.00", "100.00", "0.10");

        // ACCOUNT: a distributor account left in debit (ACCOUNT-DEBTOR).
        Distributor distributor = Distributor.<Distributor>find("partnerCode", "P-CTL01-CB").firstResult();
        DistributorAccount account = new DistributorAccount();
        account.entity = entity;
        account.payeeType = PayeeType.DISTRIBUTOR;
        account.payeeId = distributor.getId();
        account.persist();
        AccountEntry entry = new AccountEntry();
        entry.account = account;
        entry.period = period;
        entry.entryType = AccountEntryType.OPENING_BALANCE;
        entry.entryDate = periodMonth;
        entry.amount = new BigDecimal("-100.00");
        entry.persist();

        // PAYMENT: a payment batch whose total does not match its (absent) orders (PAY-LOT-BALANCE).
        PaymentBatch batch = new PaymentBatch();
        batch.entity = entity;
        batch.period = period;
        batch.batchType = BatchType.CYCLE;
        batch.status = BatchStatus.DRAFT;
        batch.totalAmount = new BigDecimal("100.00");
        batch.persist();

        // ACCOUNTING: an unbalanced accounting entry — no debit side (ACC-BALANCED).
        AccountingEntry accounting = new AccountingEntry();
        accounting.entity = entity;
        accounting.period = period;
        accounting.accountingEvent = AccountingEvent.EARNING;
        accounting.creditAccount = "706";
        accounting.amount = new BigDecimal("100.00");
        accounting.persist();

        // COMPLETENESS needs no setup: no inbound flow was imported, so an expected flow is missing
        // (COMP-FLOWS).
    }

    /**
     * Poses an eligible business event on a period for a control scenario.
     *
     * @param entity      the entity
     * @param period      the eligible period
     * @param sourceKey   the source key
     * @param basisAmount the premium basis
     * @param status      the processing status
     * @param holdReason  the hold reason, or null
     * @param date        the event and received date
     */
    private void controlEvent(LegalEntity entity, Period period, String sourceKey, BigDecimal basisAmount,
                              ProcessingStatus status, HoldReason holdReason, LocalDate date) {
        BusinessEvent event = new BusinessEvent();
        event.entity = entity;
        event.eligiblePeriod = period;
        event.sourceKey = sourceKey;
        event.eventDate = date;
        event.receivedDate = date;
        event.basisAmount = basisAmount;
        event.processingStatus = status;
        event.holdReason = holdReason;
        event.persist();
    }

    /**
     * Poses a commission line on a period for a control scenario.
     *
     * @param entity   the entity
     * @param period   the period
     * @param coverage the coverage, or null
     * @param nature   the line nature
     * @param amount   the signed amount
     * @param basis    the basis amount (external line), or null
     * @param rate     the rate (external line), or null
     */
    private void controlLine(LegalEntity entity, Period period, Coverage coverage, LineNature nature, String amount,
                             String basis, String rate) {
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.period = period;
        line.coverage = coverage;
        line.direction = Direction.OUTBOUND;
        line.nature = nature;
        line.status = LineStatus.EARNED;
        line.amount = new BigDecimal(amount);
        line.basisAmount = basis == null ? null : new BigDecimal(basis);
        line.rate = rate == null ? null : new BigDecimal(rate);
        line.attachmentMonth = period.periodMonth;
        line.persist();
    }

    /**
     * CAL-07 (parcours §4.3) : deux règles de base concurrentes (même groupe, même priorité, mêmes
     * périmètres) s'appliquent au même encaissement ; le moteur ne tranche pas et met l'événement à
     * l'écart avec le motif « règles concurrentes » (P8/H-41), sans produire de ligne.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCal07(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CAL07", product, periodMonth, "P-CAL07", null);
        linearChoice(coverage, "0.15", periodMonth, null);
        version(entity, "PLAN-CAL07-A", "R-CAL07-A", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        version(entity, "PLAN-CAL07-B", "R-CAL07-B", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, null, null);
        premium(entity, coverage, "CAL07-E1", periodMonth.plusDays(4), "100.00");
    }

    /**
     * CAL-12 (parcours §4.3) : annulation après changement de barème. Un encaissement de janvier est
     * commissionné à 15 % (version d'origine), puis le barème passe à 12 % (nouvelle version) ; quand
     * l'encaissement de janvier est annulé après ce changement, la ligne inverse est calculée avec la
     * version d'origine (15 %, D12) — une ligne de 15,00 € et sa ligne inverse de −15,00 €, non −12,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (January)
     */
    private void seedCal12(LegalEntity entity, LocalDate periodMonth) {
        LocalDate boundary = periodMonth.plusMonths(2);
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CAL12", product, periodMonth, "P-CAL12", null);
        linearChoice(coverage, "0.15", periodMonth, boundary.minusDays(1));
        linearChoice(coverage, "0.12", boundary, null);
        version(entity, "PLAN-CAL12-V1", "R-CAL12-V1", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.15]}", periodMonth, boundary.minusDays(1), null);
        version(entity, "PLAN-CAL12-V2", "R-CAL12-V2", RuleFamily.LINEAR, LineNature.LINEAR, premiumScope(entity),
                "{\"allowedRates\":[0.12]}", boundary, null, null);
        BusinessEvent origin = premium(entity, coverage, "CAL12-JAN", periodMonth.plusDays(14), "100.00");
        BusinessEvent cancellation = event(entity, coverage, "CAL12-CANCEL", EventType.PREMIUM_CANCELLED,
                boundary.plusDays(14), null);
        cancellation.reversesEvent = origin;
    }

    /**
     * CR-86 (lot de l'épargne retraite) : une période portant une commission de cycle (110 €) et une
     * commission d'épargne retraite (50 €, gamme `EPARGNE`), chacune sur un bénéficiaire payable
     * (coordonnées bancaires, inscrit à l'entité). La préparation du paiement crée deux lots distincts —
     * un lot de cycle de 110,00 € et un lot d'épargne retraite de 50,00 € daté du 20 — et la période ne
     * passe `PAID` qu'une fois les ordres des deux lots réglés (§10.5/§10.6). Les lignes acquises sont
     * posées comme état d'entrée, le code de production les aiguille vers leur compte à la préparation.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr86(LegalEntity entity, LocalDate periodMonth) {
        Period period = period(entity, periodMonth);
        Product sante = product(entity, "SANTE_SELECT");
        Coverage cycleCoverage = coverage(entity, "CR86C", sante, periodMonth, "P-CR86C", null);
        payable(entity, cycleCoverage, periodMonth);
        earnedOnCode(entity, period, cycleCoverage, "110.00");
        ProductRange savingsRange = new ProductRange();
        savingsRange.code = "EPARGNE-CR86";
        savingsRange.label = "Épargne retraite";
        savingsRange.target = ProductTarget.INDIVIDUAL;
        savingsRange.retirementSavings = Boolean.TRUE;
        savingsRange.persist();
        Product per = new Product();
        per.entity = entity;
        per.code = "PER-CR86";
        per.label = "Plan Épargne Retraite";
        per.range = savingsRange;
        per.persist();
        Coverage savingsCoverage = coverage(entity, "CR86S", per, periodMonth, "P-CR86S", null);
        payable(entity, savingsCoverage, periodMonth);
        earnedOnCode(entity, period, savingsCoverage, "50.00");
    }

    /**
     * Makes a coverage's production code payable: registers its distributor in the entity (active) and
     * gives the code bank details, so a payment order can be issued and settled.
     *
     * @param entity      the entity
     * @param coverage    the coverage carrying the production code
     * @param periodMonth the period month (validity start)
     */
    private void payable(LegalEntity entity, Coverage coverage, LocalDate periodMonth) {
        ProductionCode pc = CoverageAssignment.<CoverageAssignment>find("coverage", coverage)
                .firstResult().productionCode;
        DistributorEntity link = new DistributorEntity();
        link.distributor = pc.distributor;
        link.entity = entity;
        link.status = PartnerStatus.ACTIVE;
        link.validFrom = periodMonth;
        link.persist();
        BankAccount bank = new BankAccount();
        bank.entity = entity;
        bank.holderType = PayeeType.PRODUCTION_CODE;
        bank.holderId = pc.getId();
        bank.iban = "FR7630001007941234567890185";
        bank.bic = "AGRIFRPP";
        bank.holderName = pc.code;
        bank.validFrom = periodMonth;
        bank.persist();
    }

    /**
     * CR-80 (cession avec escompte en cours, variante « créance remboursée par le cédant ») : une garantie
     * escomptée (480 € avancés, 200 € apurés après un encaissement de 500 € en janvier) est cédée au
     * 1er février ; le cédant rembourse la créance, de sorte que la position d'escompte passe `CANCELLED`
     * et que la part non apurée est régularisée sur le compte du cédant par une ligne de −280,00 €. La
     * garantie passe au code de production du repreneur. Deux périodes sont semées (janvier ouvre
     * l'escompte, février applique la cession). La variante « créance transférée » est prouvée au moteur.
     *
     * @param entity      the entity
     * @param periodMonth the January month that opens the discount (February applies the cession)
     */
    private void seedCr80(LegalEntity entity, LocalDate periodMonth) {
        LocalDate effect = LocalDate.of(2026, 1, 1);
        period(entity, effect);
        period(entity, LocalDate.of(2026, 2, 1));
        Coverage coverage = escompteCoverage(entity, effect, "CR80", "P-CR80", "1200");
        convention(entity, "P-CR80", 100, LocalDate.of(2026, 12, 31));
        premium(entity, coverage, "CR80-E1", LocalDate.of(2026, 1, 15), "500.00");
        Distributor repreneur = new Distributor();
        repreneur.partnerCode = "P-CR80-B";
        repreneur.legalName = "Repreneur";
        repreneur.persist();
        ProductionCode pc2 = new ProductionCode();
        pc2.entity = entity;
        pc2.code = "PC-CR80-B";
        pc2.distributor = repreneur;
        pc2.persist();
        PortfolioTransfer transfer = new PortfolioTransfer();
        transfer.entity = entity;
        transfer.fromDistributor = Distributor.<Distributor>find("partnerCode", "P-CR80").firstResult();
        transfer.toDistributor = repreneur;
        transfer.effectDate = LocalDate.of(2026, 2, 1);
        transfer.advanceDebtHandling = AdvanceDebtHandling.REPAID_BY_TRANSFEROR;
        transfer.status = TransferStatus.PENDING;
        transfer.persist();
    }

    /**
     * CR-78 (tolérance de solde) : escompte de 400 € sur une prime annualisée de 1 000 €, apuré à 399,96 €
     * par douze encaissements mensuels de 83,33 € ; au 360e jour, le reliquat de 0,04 € est sous la
     * tolérance de 1 € de l'entité, donc la position est soldée (`SETTLED`) sans ligne de régularisation,
     * et une écriture `ADVANCE_WRITE_OFF` de 0,04 € est passée à la validation métier.
     *
     * @param entity      the entity
     * @param periodMonth the settlement month (December, around the 360th day)
     */
    private void seedCr78(LegalEntity entity, LocalDate periodMonth) {
        LocalDate effect = LocalDate.of(periodMonth.getYear(), 1, 1);
        Coverage coverage = escompteCoverage(entity, effect, "CR78", "P-CR78", "1000");
        convention(entity, "P-CR78", 100, LocalDate.of(effect.getYear(), 12, 31));
        for (int i = 0; i < 12; i++) {
            premium(entity, coverage, "CR78-E" + i, effect.plusMonths(i).plusDays(10), "83.33");
        }
    }

    /**
     * CR-85 (rejet bancaire) : un distributeur payable (coordonnées bancaires, inscrit à l'entité) porte
     * une commission acquise de 110 € ; le cycle la mène jusqu'à un ordre de virement de 110,00 €. Le rejet
     * bancaire de l'ordre (écriture `PAYMENT_REJECTION`, anomalie de contrôle) ramène la ligne à payable,
     * représentée au lot suivant. La ligne acquise et les coordonnées sont posées comme état d'entrée.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr85(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR85", product, periodMonth, "P-CR85", null);
        ProductionCode pc = CoverageAssignment.<CoverageAssignment>find("coverage", coverage)
                .firstResult().productionCode;
        DistributorEntity link = new DistributorEntity();
        link.distributor = Distributor.<Distributor>find("partnerCode", "P-CR85").firstResult();
        link.entity = entity;
        link.status = PartnerStatus.ACTIVE;
        link.validFrom = periodMonth;
        link.persist();
        earnedOnCode(entity, period(entity, periodMonth), coverage, "110.00");
        BankAccount bank = new BankAccount();
        bank.entity = entity;
        bank.holderType = PayeeType.PRODUCTION_CODE;
        bank.holderId = pc.getId();
        bank.iban = "FR7630001007941234567890185";
        bank.bic = "AGRIFRPP";
        bank.holderName = "P-CR85";
        bank.validFrom = periodMonth;
        bank.persist();
    }

    /** Parameters of an outbound retirement discount with annual readjustment at 8 %, zero tolerance. */
    private static final String OUT_READJUST =
            "{\"settlementRule\":\"FIFTH_ANNIVERSARY\",\"agreementRequired\":false,\"rate\":0.08,"
            + "\"offsetting\":false,\"annualReadjustment\":true,\"readjustmentTolerance\":0}";

    /**
     * CR-84 (réajustement annuel) : base annualisée de 3 600 € escomptée à 8 % (position d'escompte de
     * 288 € posée en entrée), puis 4 200 € réellement encaissés sur douze mois ; au premier anniversaire,
     * le réajustement verse un supplément d'escompte de 48,00 € (600 × 8 %) en ligne {@code ADVANCE}. La
     * variante déficitaire (3 000 € encaissés → reprise de −48,00 €) est prouvée au niveau moteur.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month (the first anniversary, January 2026)
     */
    private void seedCr84(LegalEntity entity, LocalDate periodMonth) {
        LocalDate effect = LocalDate.of(2025, 1, 15);
        Product product = product(entity, "PER");
        Coverage coverage = coverage(entity, "CR84", product, effect, "P-CR84", null);
        linearChoice(coverage, "0.0145", effect, null);
        RuleVersion rule = version(entity, "PLAN-CR84", "R-CR84", RuleFamily.ADVANCE, LineNature.ADVANCE,
                savingsScope(entity), OUT_READJUST, effect, null, null);
        rule.exclusionGroup = "SAVINGS";
        discountPosition(coverage, "288.00", effect);
        event(entity, coverage, "CR84-S1", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 6, 1), "2100.00");
        event(entity, coverage, "CR84-S2", EventType.SCHEDULED_PAYMENT, LocalDate.of(2025, 12, 1), "2100.00");
    }

    /**
     * Creates an active advance position on a coverage carrying a linear advance, used as the entry state
     * of an annual-readjustment case (CR-84).
     *
     * @param coverage the coverage
     * @param advanced the linear amount already advanced
     * @param anchor   the anchor date (the fifth-anniversary settlement is five years later)
     */
    private void discountPosition(Coverage coverage, String advanced, LocalDate anchor) {
        AdvancePosition position = new AdvancePosition();
        position.coverage = coverage;
        position.status = AdvanceStatus.ACTIVE;
        position.settlementDueDate = anchor.plusYears(5);
        position.advancedAcquisition = BigDecimal.ZERO;
        position.advancedServicing = BigDecimal.ZERO;
        position.advancedLinear = new BigDecimal(advanced);
        position.offsetAcquisition = BigDecimal.ZERO;
        position.offsetServicing = BigDecimal.ZERO;
        position.offsetLinear = BigDecimal.ZERO;
        position.persist();
    }

    /**
     * CR-50 (rétrocession à un mandataire) : une commission reçue de l'assureur de 100 € rétrocédée à
     * 60 % donne une ligne `RETROCESSION` de 60,00 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr50(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE");
        Coverage coverage = coverage(entity, "CR50", product, periodMonth, "P-CR50", null);
        version(entity, "PLAN-CR50", "R-CR50", RuleFamily.SHARE, LineNature.RETROCESSION,
                "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE\"],\"eventType\":[\"INSURER_COMMISSION\"]}",
                "{\"sourceNature\":\"INSURER\",\"rate\":0.60}", periodMonth, null, null);
        event(entity, coverage, "CR50-E1", EventType.INSURER_COMMISSION, periodMonth.plusDays(14), "100");
    }

    /**
     * CR-51 (commission externe) : une ligne reçue (assiette 500 €, taux 3,5 %, montant 17,50 €) est
     * intégrée telle quelle, sans recalcul ; une ligne `EXTERNAL` de 17,50 €.
     *
     * @param entity      the entity
     * @param periodMonth the calculation month
     */
    private void seedCr51(LegalEntity entity, LocalDate periodMonth) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, "CR51", product, periodMonth, "P-CR51", null);
        version(entity, "PLAN-CR51", "R-CR51", RuleFamily.EXTERNAL, LineNature.EXTERNAL,
                "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE_SELECT\"]}", "{}", periodMonth, null, null);
        BusinessEvent e = event(entity, coverage, "CR51-E1", EventType.PREMIUM_COLLECTED, periodMonth.plusDays(4),
                null);
        e.precomputedBasis = new BigDecimal("500.00");
        e.precomputedRate = new BigDecimal("0.035");
        e.precomputedAmount = new BigDecimal("17.50");
    }

    /**
     * Creates a payee rule on a production code splitting the commission between a distributor and a group.
     *
     * @param entity          the entity
     * @param pc              the production code the rule keys off
     * @param distributor     the distributor payee
     * @param distributorShare the distributor share, as a fraction
     * @param group           the group payee
     * @param groupShare      the group share, as a fraction
     */
    private void payeeRule(LegalEntity entity, ProductionCode pc, Distributor distributor, String distributorShare,
                           DistributorGroup group, String groupShare) {
        PayeeRule rule = new PayeeRule();
        rule.entity = entity;
        rule.productionCode = pc;
        rule.persist();
        payeeShare(rule, PayeeType.DISTRIBUTOR, distributor.getId(), distributorShare);
        payeeShare(rule, PayeeType.GROUP, group.getId(), groupShare);
    }

    /**
     * Adds one share to a payee rule.
     *
     * @param rule    the payee rule
     * @param type    the payee type
     * @param payeeId the payee's technical id
     * @param share   the share, as a fraction
     */
    private void payeeShare(PayeeRule rule, PayeeType type, Long payeeId, String share) {
        PayeeRuleShare s = new PayeeRuleShare();
        s.payeeRule = rule;
        s.payeeType = type;
        s.payeeId = payeeId;
        s.share = new BigDecimal(share);
        s.persist();
    }

    /**
     * Attaches the retirement-complement tiers to a rule version: a whole-amount scale giving the rate
     * of the reached tier (10 % below 10 000 €, then 12 %, 15 %, 20 %, 25 %), the lower bound included.
     *
     * @param version the tiered complement rule version
     */
    private void complementScale(RuleVersion version) {
        Scale scale = new Scale();
        scale.ruleVersion = version;
        scale.mode = ScaleMode.WHOLE;
        scale.measure = ScaleMeasure.AMOUNT;
        scale.output = ScaleOutput.RATE;
        scale.persist();
        tier(scale, "0", "0.10");
        tier(scale, "10000", "0.12");
        tier(scale, "14000", "0.15");
        tier(scale, "20000", "0.20");
        tier(scale, "50000", "0.25");
    }

    /**
     * Adds one tier to a scale, counted from the first unit.
     *
     * @param scale      the scale
     * @param lowerBound the included lower bound
     * @param rate       the tier rate, as a fraction
     */
    private void tier(Scale scale, String lowerBound, String rate) {
        ScaleTier tier = new ScaleTier();
        tier.scale = scale;
        tier.lowerBound = new BigDecimal(lowerBound);
        tier.rate = new BigDecimal(rate);
        tier.fromFirstUnit = Boolean.TRUE;
        tier.persist();
    }

    /**
     * Injects a pending unpaid-notice event of a given rank on a coverage.
     *
     * @param entity   the entity
     * @param coverage the coverage
     * @param key      the source key
     * @param date     the event date
     * @param rank     the unpaid rank
     */
    private void unpaidNotice(LegalEntity entity, Coverage coverage, String key, LocalDate date, short rank) {
        BusinessEvent notice = event(entity, coverage, key, EventType.UNPAID_NOTICE, date, null);
        notice.unpaidRank = rank;
    }

    /**
     * Creates an escompte garantie: a product, contract (registered at the effect date), coverage with an
     * annualised premium, distributor, production code and assignment, an upfront 30/10 choice with the
     * advance requested, and the upfront rule. Returns the coverage.
     *
     * @param entity  the entity
     * @param effect  the coverage effect date
     * @param num     the contract number (keys the plan/rule/codes)
     * @param partner the distributor partner code
     * @param premium the annualised premium
     * @return the coverage
     */
    private Coverage escompteCoverage(LegalEntity entity, LocalDate effect, String num, String partner,
                                      String premium) {
        Product product = product(entity, "SANTE_SELECT");
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "SYS";
        contract.contractNumber = num;
        contract.product = product;
        contract.registrationDate = effect;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = num + "-C1";
        coverage.effectDate = effect;
        coverage.annualPremium = new BigDecimal(premium);
        coverage.persist();
        Distributor distributor = new Distributor();
        distributor.partnerCode = partner;
        distributor.legalName = partner;
        distributor.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = entity;
        pc.code = "PC-" + num;
        pc.distributor = distributor;
        pc.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = AssignmentRole.MAIN;
        assignment.share = BigDecimal.ONE;
        assignment.validFrom = effect;
        assignment.persist();
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.UPFRONT;
        choice.acquisitionRate = new BigDecimal("0.30");
        choice.servicingRate = new BigDecimal("0.10");
        choice.advanceRequested = true;
        choice.validFrom = effect;
        choice.persist();
        version(entity, "PLAN-" + num, "R-" + num, RuleFamily.UPFRONT, LineNature.ACQUISITION, premiumScope(entity),
                UPFRONT_PARAMS, effect, null, CLAWBACK);
        return coverage;
    }

    /**
     * Creates a VALID discount convention for a distributor, with a contract cap and a window end.
     *
     * @param entity       the entity
     * @param partner      the distributor partner code
     * @param maxContracts the contract cap
     * @param validTo      the end of the convention window
     * @return the created agreement
     */
    private AdvanceAgreement convention(LegalEntity entity, String partner, int maxContracts, LocalDate validTo) {
        AdvanceAgreement agreement = new AdvanceAgreement();
        agreement.entity = entity;
        agreement.distributor = Distributor.find("partnerCode", partner).firstResult();
        agreement.validFrom = LocalDate.of(2025, 1, 1);
        agreement.validTo = validTo;
        agreement.anticipationDays = 0;
        agreement.maxContracts = maxContracts;
        agreement.unpaidRankThreshold = (short) 2;
        agreement.status = AdvanceAgreementStatus.VALID;
        agreement.persist();
        return agreement;
    }

    /**
     * The premium perimeter restricted to a college, for the collective-college case.
     *
     * @param entity  the entity
     * @param college the staff category code
     * @return the scope JSON
     */
    private String collegeScope(LegalEntity entity, String college) {
        return "{\"entity\":[\"" + entity.code + "\"],\"product\":[\"SANTE_SELECT\"],"
                + "\"eventType\":[\"PREMIUM_COLLECTED\"],\"college\":[\"" + college + "\"]}";
    }

    /**
     * Finds the entity's period for a month, or creates it in a calculable state.
     *
     * @param entity      the entity
     * @param periodMonth the month
     * @return the period
     */
    private Period period(LegalEntity entity, LocalDate periodMonth) {
        Period existing = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (existing != null) {
            return existing;
        }
        Period period = new Period();
        period.entity = entity;
        period.periodMonth = periodMonth;
        period.state = PeriodState.LOADED;
        period.persist();
        return period;
    }

    /**
     * Creates a product for the entity.
     *
     * @param entity the entity
     * @param code   the product code
     * @return the product
     */
    private Product product(LegalEntity entity, String code) {
        Product product = new Product();
        product.entity = entity;
        product.code = code;
        product.label = code;
        product.persist();
        return product;
    }

    /**
     * Creates a coverage on a new contract, with its main production-code assignment and a new
     * distributor.
     *
     * @param entity         the entity
     * @param contractNumber the contract number
     * @param product        the product
     * @param effectDate     the coverage effect date
     * @param partnerCode    the distributor partner code
     * @param college        the staff category (college), or null
     * @return the coverage
     */
    private Coverage coverage(LegalEntity entity, String contractNumber, Product product, LocalDate effectDate,
                              String partnerCode, StaffCategory college) {
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "SYS";
        contract.contractNumber = contractNumber;
        contract.product = product;
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = contractNumber + "-C1";
        coverage.effectDate = effectDate;
        coverage.staffCategory = college;
        coverage.persist();
        Distributor distributor = Distributor.<Distributor>find("partnerCode", partnerCode).firstResult();
        if (distributor == null) {
            distributor = new Distributor();
            distributor.partnerCode = partnerCode;
            distributor.legalName = partnerCode;
            distributor.persist();
        }
        ProductionCode pc = new ProductionCode();
        pc.entity = entity;
        pc.code = "PC-" + contractNumber;
        pc.distributor = distributor;
        pc.persist();
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = AssignmentRole.MAIN;
        assignment.share = BigDecimal.ONE;
        assignment.validFrom = effectDate;
        assignment.persist();
        return coverage;
    }

    /**
     * Creates a linear remuneration choice for a coverage.
     *
     * @param coverage the coverage
     * @param rate     the linear rate, as a fraction
     * @param from     the start of validity
     * @param to       the end of validity, or null
     */
    private void linearChoice(Coverage coverage, String rate, LocalDate from, LocalDate to) {
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.LINEAR;
        choice.linearRate = new BigDecimal(rate);
        choice.validFrom = from;
        choice.validTo = to;
        choice.persist();
    }

    /**
     * Creates an upfront remuneration choice for a coverage.
     *
     * @param coverage    the coverage
     * @param acquisition the acquisition rate, as a fraction
     * @param servicing   the servicing rate, as a fraction
     * @param from        the start of validity
     */
    private void upfrontChoice(Coverage coverage, String acquisition, String servicing, LocalDate from) {
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = RemunerationMode.UPFRONT;
        choice.acquisitionRate = new BigDecimal(acquisition);
        choice.servicingRate = new BigDecimal(servicing);
        choice.validFrom = from;
        choice.persist();
    }

    /**
     * Creates an active rule version for the entity, creating its plan and rule as needed.
     *
     * @param entity     the entity
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @param family     the calculation family
     * @param nature     the output nature
     * @param scope      the scope JSON
     * @param parameters the parameters JSON
     * @param from       the start of validity
     * @param to         the end of validity, or null
     * @param clawback   the clawback policy JSON, or null when the rule has none
     * @return the created rule version
     */
    private RuleVersion version(LegalEntity entity, String planCode, String ruleCode, RuleFamily family,
                                LineNature nature, String scope, String parameters, LocalDate from, LocalDate to,
                                String clawback) {
        return version(entity, Direction.OUTBOUND, planCode, ruleCode, family, nature, scope, parameters, from, to,
                clawback);
    }

    /**
     * Creates an active rule version in a plan of the given direction, creating its plan and rule.
     *
     * @param entity     the entity
     * @param direction  the plan direction (outbound paid, inbound received)
     * @param planCode   the plan code
     * @param ruleCode   the rule code
     * @param family     the calculation family
     * @param nature     the output nature
     * @param scope      the scope JSON
     * @param parameters the parameters JSON
     * @param from       the start of validity
     * @param to         the end of validity, or null
     * @param clawback   the clawback policy JSON, or null when the rule has none
     * @return the created rule version
     */
    private RuleVersion version(LegalEntity entity, Direction direction, String planCode, String ruleCode,
                                RuleFamily family, LineNature nature, String scope, String parameters, LocalDate from,
                                LocalDate to, String clawback) {
        CommissionPlan plan = new CommissionPlan();
        plan.entity = entity;
        plan.code = planCode;
        plan.label = planCode;
        plan.direction = direction;
        plan.vintage = (short) 2026;
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = ruleCode;
        rule.label = ruleCode;
        rule.family = family;
        rule.persist();
        RuleVersion version = new RuleVersion();
        version.rule = rule;
        version.versionNo = 1;
        version.status = VersionStatus.ACTIVE;
        version.scope = scope;
        version.parameters = parameters;
        version.exclusionGroup = "BASE";
        version.priority = 10;
        version.outputNature = nature;
        version.validFrom = from;
        version.validTo = to;
        version.clawbackPolicy = clawback;
        version.persist();
        return version;
    }

    /** The reference upfront clawback policy: 100% the first year, 50% the second, on acquisition. */
    private static final String CLAWBACK =
            "{\"type\":\"AGE_STEPS\",\"natures\":[\"ACQUISITION\"],\"triggers\":[\"TERMINATION\",\"WITHDRAWAL\"],"
            + "\"steps\":[{\"maxDays\":365,\"rate\":1.0},{\"maxDays\":730,\"rate\":0.5}]}";

    /**
     * Creates an upfront garantie with its 30/10 choice and an active upfront rule carrying the reference
     * clawback policy, and returns the coverage.
     *
     * @param entity         the entity
     * @param effect         the coverage effect date (and rule start)
     * @param contractNumber the contract number (also used to key the plan/rule)
     * @param partnerCode    the distributor partner code
     * @return the coverage
     */
    private Coverage upfrontCoverage(LegalEntity entity, LocalDate effect, String contractNumber, String partnerCode) {
        Product product = product(entity, "SANTE_SELECT");
        Coverage coverage = coverage(entity, contractNumber, product, effect, partnerCode, null);
        upfrontChoice(coverage, "0.30", "0.10", effect);
        version(entity, "PLAN-" + contractNumber, "R-" + contractNumber, RuleFamily.UPFRONT, LineNature.ACQUISITION,
                premiumScope(entity), UPFRONT_PARAMS, effect, null, CLAWBACK);
        return coverage;
    }

    /**
     * Injects a pending premium event on a coverage.
     *
     * @param entity   the entity
     * @param coverage the coverage
     * @param key      the source key
     * @param date     the event date
     * @param basis    the basis amount
     */
    private BusinessEvent premium(LegalEntity entity, Coverage coverage, String key, LocalDate date, String basis) {
        return event(entity, coverage, key, EventType.PREMIUM_COLLECTED, date, basis);
    }

    /**
     * Injects a pending event on a coverage.
     *
     * @param entity   the entity
     * @param coverage the coverage
     * @param key      the source key
     * @param type     the event type
     * @param date     the event date
     * @param basis    the basis amount, or null for a non-financial event (termination, death, …)
     */
    private BusinessEvent event(LegalEntity entity, Coverage coverage, String key, EventType type, LocalDate date,
                                String basis) {
        BusinessEvent event = new BusinessEvent();
        event.entity = entity;
        event.sourceSystem = "SYS";
        event.sourceKey = key;
        event.eventType = type;
        event.contract = coverage.contract;
        event.coverage = coverage;
        event.eventDate = date;
        event.receivedDate = date;
        event.basisAmount = basis == null ? null : new BigDecimal(basis);
        event.processingStatus = ProcessingStatus.PENDING;
        event.persist();
        return event;
    }
}
