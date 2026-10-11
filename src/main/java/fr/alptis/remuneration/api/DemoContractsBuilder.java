package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.AdvanceDebtHandling;
import fr.alptis.remuneration.domain.AssignmentOrigin;
import fr.alptis.remuneration.domain.AssignmentRole;
import fr.alptis.remuneration.domain.BaseEntity;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.ContractStatus;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Periodicity;
import fr.alptis.remuneration.domain.PersonType;
import fr.alptis.remuneration.domain.Policyholder;
import fr.alptis.remuneration.domain.PortfolioTransfer;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.RemunerationMode;
import fr.alptis.remuneration.domain.ReplacementOrder;
import fr.alptis.remuneration.domain.SalesChannel;
import fr.alptis.remuneration.domain.StaffCategory;
import fr.alptis.remuneration.domain.TariffType;
import fr.alptis.remuneration.domain.TransferStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the contracts, coverages, assignments and remuneration choices of the demonstration dataset:
 * one wired scene per calculation family (so the cycle runner can make each family produce lines) plus
 * a bulk portfolio that carries the dataset past 150 contracts and 200 coverages, with co-broking
 * shares, portfolio transfers (one processed, one in progress) and a replacement order.
 *
 * <p>No business event is created here: the engine pulls every pending event of an entity into
 * whichever period is run, so events are emitted month by month by the cycle runner. This block only
 * records, in the shared world, the scene coverages the runner will drive.</p>
 */
@ApplicationScoped
public class DemoContractsBuilder {

    /** The author stamped on every object of this block. */
    private static final String AUTHOR = "demo-contracts";

    /** The partner code enrolled for development and portfolio remuneration in both entities. */
    private static final String ENROLLED = "P20001";

    /** The common effect date of the demonstration portfolio. */
    private static final LocalDate EFFECT = LocalDate.of(2026, 1, 1);

    /** The application date/time provider. */
    private final DateTimeProvider clock;

    /** Running counter feeding unique contract numbers. */
    private int sequence;

    /**
     * Creates the builder.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public DemoContractsBuilder(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Builds the contracts of both entities into the shared world.
     *
     * @param world the shared demo world
     */
    void build(DemoWorld world) {
        for (LegalEntity entity : world.entities.values()) {
            List<Policyholder> holders = policyholders(entity);
            scenes(world, entity, holders.get(0));
            bulk(world, entity, holders);
        }
        transfers(world);
        replacementOrder(world);
    }

    /**
     * Creates a few policyholders for an entity.
     *
     * @param entity the entity
     * @return the created policyholders
     */
    private List<Policyholder> policyholders(LegalEntity entity) {
        String[][] rows = {
                {"Durand", "Claire", "NATURAL"},
                {"Lefèvre", "Paul", "NATURAL"},
                {"Société Horizon", "", "LEGAL"},
                {"Mercier", "Louise", "NATURAL"},
                {"Groupe Atlas", "", "LEGAL"},
        };
        List<Policyholder> holders = new ArrayList<>();
        int i = 0;
        for (String[] row : rows) {
            Policyholder holder = new Policyholder();
            holder.entity = entity;
            holder.sourceSystem = "DEMO";
            holder.externalId = entity.code + "-PH-" + (++i);
            holder.lastName = row[0];
            holder.firstName = row[1];
            holder.personType = PersonType.valueOf(row[2]);
            holder.birthDate = row[2].equals("NATURAL") ? LocalDate.of(1980, 6, 1) : null;
            stamp(holder);
            holder.persist();
            holders.add(holder);
        }
        return holders;
    }

    /**
     * Wires one coverage per family for an entity and records it in the world. Alptis carries most
     * scenes on the mock-up cabinet; the development and portfolio scenes sit on the enrolled
     * distributor in both entities.
     *
     * @param world  the shared demo world
     * @param entity the entity
     * @param holder the policyholder to attach
     */
    private void scenes(DemoWorld world, LegalEntity entity, Policyholder holder) {
        boolean alptis = entity.code.equals(DemoCodes.ALPTIS);
        // The mock-up cabinet carries only the linear 110 € scene in Alptis; the other scenes sit on a
        // second active distributor so the cabinet's September bordereau stays a clean 110,00 €.
        String linearMain = alptis ? DemoCodes.CABINET : ENROLLED;
        String other = alptis ? "P20002" : ENROLLED;

        Coverage linear = scene(world, entity, holder, DemoCodes.F_LINEAR, DemoCodes.P_LINEAR, linearMain,
                new BigDecimal("1200.00"));
        choice(linear, RemunerationMode.LINEAR, new BigDecimal("0.100000"), null, null, false);

        Coverage upfront = scene(world, entity, holder, DemoCodes.F_UPFRONT, DemoCodes.P_UPFRONT, other,
                new BigDecimal("1000.00"));
        choice(upfront, RemunerationMode.UPFRONT, null, new BigDecimal("0.200000"), new BigDecimal("0.100000"),
                false);

        // Alptis carries the discount advance (escompte) on its own upfront coverage against the valid
        // convention of P20002, near its contract cap. The engine opens the advance in June and the
        // coverage's monthly premiums apure it from one month to the next (section 6.4), so the dataset
        // shows a real discount followed over several months.
        if (alptis) {
            escompteScene(world, entity, holder);
        }

        scene(world, entity, holder, DemoCodes.F_FIXED, DemoCodes.P_FORFAIT, other, new BigDecimal("600.00"));
        scene(world, entity, holder, DemoCodes.F_EXTERNAL, DemoCodes.P_EXTERNE, other, new BigDecimal("0.00"));
        scene(world, entity, holder, DemoCodes.F_SHARE, DemoCodes.P_COLLECTIVE, other, new BigDecimal("0.00"));
        scene(world, entity, holder, DemoCodes.F_OUTSTANDING, DemoCodes.P_PER, other, new BigDecimal("0.00"));

        // One retirement-savings coverage serves both the savings advance (runReal) and the annual
        // complement (runAnnualClose), which both key off its scheduled payments.
        Coverage savings = scene(world, entity, holder, DemoCodes.F_SAVINGS, DemoCodes.P_PER, other,
                new BigDecimal("0.00"));
        world.putScene(entity.code, DemoCodes.F_COMPLEMENT, savings);

        scene(world, entity, holder, DemoCodes.F_RCD, DemoCodes.P_COLLECTIVE, ENROLLED, new BigDecimal("30000.00"));
        scene(world, entity, holder, DemoCodes.F_PORTFOLIO, DemoCodes.P_COLLECTIVE, ENROLLED,
                new BigDecimal("50000.00"));
    }

    /**
     * Creates the discount-advance (escompte) coverage: an upfront coverage on P20002 that requests the
     * advance against the valid convention. The engine opens one advance position in June; the coverage's
     * monthly premiums (emitted by the cycle runner) apure it from one month to the next, so the discount
     * is a real one followed over several months. Recorded under {@link DemoCodes#F_ESCOMPTE}.
     *
     * @param world  the shared demo world
     * @param entity the entity
     * @param holder the policyholder
     */
    private void escompteScene(DemoWorld world, LegalEntity entity, Policyholder holder) {
        Contract contract = contract(entity, holder, world.product(entity.code, DemoCodes.P_UPFRONT),
                entity.code + "-ESCOMPTE");
        Coverage coverage = coverage(contract, contract.contractNumber + "-C1", new BigDecimal("2000.00"));
        // A June effect keeps the advance within its settlement window while the June run opens it.
        coverage.effectDate = LocalDate.of(2026, 6, 1);
        assign(coverage, world.productionCode(entity.code, "PC-P20002"), AssignmentRole.MAIN, BigDecimal.ONE,
                AssignmentOrigin.SUBSCRIPTION);
        choice(coverage, RemunerationMode.UPFRONT, null, new BigDecimal("0.200000"), new BigDecimal("0.100000"),
                true);
        world.putScene(entity.code, DemoCodes.F_ESCOMPTE, coverage);
    }

    /**
     * Creates one scene contract with a single coverage and a main assignment, and records the coverage.
     *
     * @param world         the shared demo world
     * @param entity        the entity
     * @param holder        the policyholder
     * @param tag           the family scene tag
     * @param productCode   the product code
     * @param partnerCode   the main distributor's partner code
     * @param annualPremium the coverage annual premium
     * @return the created coverage
     */
    private Coverage scene(DemoWorld world, LegalEntity entity, Policyholder holder, String tag,
                           String productCode, String partnerCode, BigDecimal annualPremium) {
        Contract contract = contract(entity, holder, world.product(entity.code, productCode),
                entity.code + "-" + tag);
        Coverage coverage = coverage(contract, contract.contractNumber + "-C1", annualPremium);
        assign(coverage, world.productionCode(entity.code, "PC-" + partnerCode), AssignmentRole.MAIN,
                BigDecimal.ONE, AssignmentOrigin.SUBSCRIPTION);
        world.putScene(entity.code, tag, coverage);
        return coverage;
    }

    /**
     * Builds the bulk portfolio of an entity: linear health contracts spread over the entity's active
     * production codes, a few of them co-brokered, so the dataset clears 150 contracts and 200
     * coverages.
     *
     * @param world   the shared demo world
     * @param entity  the entity
     * @param holders the policyholders to spread across the portfolio
     */
    private void bulk(DemoWorld world, LegalEntity entity, List<Policyholder> holders) {
        List<ProductionCode> codes = activeCodes(world, entity);
        Product product = world.product(entity.code, DemoCodes.P_LINEAR);
        for (int i = 0; i < 80; i++) {
            Contract contract = contract(entity, holders.get(i % holders.size()), product,
                    entity.code + "-BULK-" + i);
            ProductionCode primary = codes.get(i % codes.size());
            Coverage coverage = linearCoverage(contract, contract.contractNumber + "-C1",
                    new BigDecimal("900.00").add(BigDecimal.valueOf(i)));
            // Every ninth coverage is co-brokered between two production codes (multi-apporteur).
            if (i % 9 == 0) {
                assign(coverage, primary, AssignmentRole.MAIN, new BigDecimal("0.700000"),
                        AssignmentOrigin.SUBSCRIPTION);
                assign(coverage, codes.get((i + 1) % codes.size()), AssignmentRole.CO_BROKER,
                        new BigDecimal("0.300000"), AssignmentOrigin.SUBSCRIPTION);
            } else {
                assign(coverage, primary, AssignmentRole.MAIN, BigDecimal.ONE, AssignmentOrigin.SUBSCRIPTION);
            }
            // Every third contract carries a second guarantee, so coverages outnumber contracts.
            if (i % 3 == 0) {
                Coverage second = linearCoverage(contract, contract.contractNumber + "-C2",
                        new BigDecimal("450.00").add(BigDecimal.valueOf(i)));
                assign(second, primary, AssignmentRole.MAIN, BigDecimal.ONE, AssignmentOrigin.SUBSCRIPTION);
            }
        }
    }

    /**
     * Creates a linear coverage with a 10 % linear choice (the caller adds its assignments).
     *
     * @param contract      the owning contract
     * @param code          the coverage code
     * @param annualPremium the annual premium
     * @return the created coverage
     */
    private Coverage linearCoverage(Contract contract, String code, BigDecimal annualPremium) {
        Coverage coverage = coverage(contract, code, annualPremium);
        choice(coverage, RemunerationMode.LINEAR, new BigDecimal("0.100000"), null, null, false);
        return coverage;
    }

    /**
     * Returns the active production codes of an entity, in index order.
     *
     * @param world  the shared demo world
     * @param entity the entity
     * @return the active production codes
     */
    private List<ProductionCode> activeCodes(DemoWorld world, LegalEntity entity) {
        List<ProductionCode> codes = new ArrayList<>();
        for (ProductionCode pc : world.productionCodes.values()) {
            // The mock-up cabinet stays off the bulk portfolio so its only line is the September scene.
            if (pc.entity.code.equals(entity.code) && !pc.distributor.partnerCode.equals(DemoCodes.CABINET)
                    && pc.status == fr.alptis.remuneration.domain.PartnerStatus.ACTIVE) {
                codes.add(pc);
            }
        }
        return codes;
    }

    /**
     * Creates one contract.
     *
     * @param entity the entity
     * @param holder the policyholder
     * @param product the product
     * @param number  the contract number
     * @return the persisted contract
     */
    private Contract contract(LegalEntity entity, Policyholder holder, Product product, String number) {
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "DEMO";
        contract.contractNumber = number + "-" + (++sequence);
        contract.product = product;
        contract.policyholder = holder;
        contract.signatureDate = EFFECT;
        contract.registrationDate = EFFECT;
        contract.effectDate = EFFECT;
        contract.status = ContractStatus.IN_FORCE;
        contract.periodicity = Periodicity.MONTHLY;
        contract.salesChannel = SalesChannel.ALPTIS_CONNECT;
        contract.newBusiness = Boolean.TRUE;
        stamp(contract);
        contract.persist();
        return contract;
    }

    /**
     * Creates one coverage.
     *
     * @param contract      the owning contract
     * @param code          the coverage code
     * @param annualPremium the annual premium
     * @return the persisted coverage
     */
    private Coverage coverage(Contract contract, String code, BigDecimal annualPremium) {
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = code;
        coverage.effectDate = EFFECT;
        coverage.status = ContractStatus.IN_FORCE;
        coverage.annualPremium = annualPremium;
        coverage.tariffType = TariffType.STANDARD;
        coverage.staffCategory = StaffCategory.NON_EXECUTIVE;
        coverage.singlePremium = Boolean.FALSE;
        coverage.commissionable = Boolean.TRUE;
        stamp(coverage);
        coverage.persist();
        return coverage;
    }

    /**
     * Creates one coverage assignment.
     *
     * @param coverage the coverage
     * @param pc       the production code
     * @param role     the assignment role
     * @param share    the share fraction
     * @param origin   the assignment origin
     */
    private void assign(Coverage coverage, ProductionCode pc, AssignmentRole role, BigDecimal share,
                        AssignmentOrigin origin) {
        CoverageAssignment assignment = new CoverageAssignment();
        assignment.coverage = coverage;
        assignment.productionCode = pc;
        assignment.role = role;
        assignment.share = share;
        assignment.origin = origin;
        assignment.validFrom = EFFECT;
        stamp(assignment);
        assignment.persist();
    }

    /**
     * Creates one remuneration choice.
     *
     * @param coverage    the coverage
     * @param mode        the remuneration mode
     * @param linearRate  the linear rate, or null
     * @param acquisition the acquisition rate, or null
     * @param servicing   the servicing rate, or null
     * @param advance     whether the discount advance is requested
     */
    private void choice(Coverage coverage, RemunerationMode mode, BigDecimal linearRate, BigDecimal acquisition,
                        BigDecimal servicing, boolean advance) {
        RemunerationChoice choice = new RemunerationChoice();
        choice.coverage = coverage;
        choice.mode = mode;
        choice.linearRate = linearRate;
        choice.acquisitionRate = acquisition;
        choice.servicingRate = servicing;
        choice.advanceRequested = advance;
        choice.validFrom = EFFECT;
        stamp(choice);
        choice.persist();
    }

    /**
     * Creates two portfolio transfers on Alptis: one processed (debt repaid by the transferor) and one
     * in progress (debt taken over by the transferee), with a transferred assignment for the processed
     * one.
     *
     * @param world the shared demo world
     */
    private void transfers(DemoWorld world) {
        LegalEntity entity = world.entity(DemoCodes.ALPTIS);
        PortfolioTransfer done = new PortfolioTransfer();
        done.entity = entity;
        done.fromDistributor = world.distributor("P20006");
        done.toDistributor = world.distributor("P20002");
        done.effectDate = LocalDate.of(2026, 5, 1);
        done.scope = "[]";
        done.advanceDebtHandling = AdvanceDebtHandling.REPAID_BY_TRANSFEROR;
        done.status = TransferStatus.PROCESSED;
        stamp(done);
        done.persist();

        PortfolioTransfer ongoing = new PortfolioTransfer();
        ongoing.entity = entity;
        ongoing.fromDistributor = world.distributor("P20008");
        ongoing.toDistributor = world.distributor("P20002");
        ongoing.effectDate = LocalDate.of(2026, 11, 1);
        ongoing.scope = "[]";
        ongoing.advanceDebtHandling = AdvanceDebtHandling.TAKEN_OVER;
        ongoing.status = TransferStatus.PENDING;
        stamp(ongoing);
        ongoing.persist();

        // One UNEP transfer too, so the transfers screen is not empty for UNEP users.
        PortfolioTransfer unep = new PortfolioTransfer();
        unep.entity = world.entity(DemoCodes.UNEP);
        unep.fromDistributor = world.distributor("P20002");
        unep.toDistributor = world.distributor("P20001");
        unep.effectDate = LocalDate.of(2026, 4, 1);
        unep.scope = "[]";
        unep.advanceDebtHandling = AdvanceDebtHandling.REPAID_BY_TRANSFEROR;
        unep.status = TransferStatus.PROCESSED;
        stamp(unep);
        unep.persist();
    }

    /**
     * Creates one replacement order moving a contract's production code to another on Alptis.
     *
     * @param world the shared demo world
     */
    private void replacementOrder(DemoWorld world) {
        replacementOrder(world, DemoCodes.ALPTIS, "PC-" + DemoCodes.CABINET, "PC-P20002");
        replacementOrder(world, DemoCodes.UNEP, "PC-P20001", "PC-P20002");
    }

    /**
     * Creates one replacement order on an entity's linear scene contract.
     *
     * @param world    the shared demo world
     * @param entity   the entity code
     * @param fromCode the production code replaced
     * @param toCode   the replacing production code
     */
    private void replacementOrder(DemoWorld world, String entity, String fromCode, String toCode) {
        Coverage scene = world.scene(entity, DemoCodes.F_LINEAR);
        ReplacementOrder order = new ReplacementOrder();
        order.contract = scene.contract;
        order.fromProductionCode = world.productionCode(entity, fromCode);
        order.toProductionCode = world.productionCode(entity, toCode);
        order.receivedDate = LocalDate.of(2026, 9, 15);
        order.effectDate = LocalDate.of(2026, 10, 1);
        stamp(order);
        order.persist();
    }

    /**
     * Stamps the creation audit columns on a freshly built entity.
     *
     * @param entity the entity to stamp
     */
    private void stamp(BaseEntity entity) {
        entity.setCreatedAt(clock.now());
        entity.setCreatedBy(AUTHOR);
    }
}
