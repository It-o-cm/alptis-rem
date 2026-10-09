package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Employee;
import fr.alptis.remuneration.domain.EmployeeMeasure;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.JobCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.MeasureType;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Imports the sales-force referentials (FDV-01, FDV-02, section 9.10, flow IN-09): the collaborators
 * ({@code employees}) and their measures ({@code employee_measures}) received from the HR system and
 * the CRM. A measure whose collaborator is unknown is kept on hold; a malformed line is rejected.
 */
@ApplicationScoped
public class EmployeeImportService {

    /** The collaborators flow code. */
    public static final String EMPLOYEES = FlowCodes.EMPLOYEES;

    /** The employee-measures flow code. */
    public static final String EMPLOYEE_MEASURES = FlowCodes.EMPLOYEE_MEASURES;

    /** The application date/time provider. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public EmployeeImportService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Imports a collaborators file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importEmployees(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, EMPLOYEES, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "external_id").isEmpty() || value(row, "last_name").isEmpty()
                            || value(row, "first_name").isEmpty() || value(row, "job_code").isEmpty()
                            || value(row, "valid_from").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    if (parseJobCode(value(row, "job_code")) == null
                            || parseDate(value(row, "valid_from")) == null) {
                        return RejectionReasonCode.TYPE;
                    }
                    return null;
                },
                row -> {
                    String managerCode = value(row, "manager_external_id");
                    Employee manager = null;
                    if (!managerCode.isEmpty()) {
                        manager = Employee.find("entity = ?1 and externalId = ?2", entity, managerCode)
                                .firstResult();
                        if (manager == null) {
                            return ImportJournal.LineResult.ON_HOLD;
                        }
                    }
                    Employee employee = new Employee();
                    employee.entity = entity;
                    employee.externalId = value(row, "external_id");
                    employee.lastName = value(row, "last_name");
                    employee.firstName = value(row, "first_name");
                    employee.jobCode = parseJobCode(value(row, "job_code"));
                    employee.team = emptyToNull(value(row, "team"));
                    employee.region = emptyToNull(value(row, "region"));
                    employee.manager = manager;
                    employee.validFrom = parseDate(value(row, "valid_from"));
                    employee.validTo = parseDate(value(row, "valid_to"));
                    employee.persist();
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Imports an employee-measures file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importMeasures(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, EMPLOYEE_MEASURES, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "external_id").isEmpty() || value(row, "measure_type").isEmpty()
                            || value(row, "period_from").isEmpty() || value(row, "period_to").isEmpty()
                            || value(row, "value").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    if (parseMeasureType(value(row, "measure_type")) == null
                            || parseDate(value(row, "period_from")) == null
                            || parseDate(value(row, "period_to")) == null
                            || parseDecimal(value(row, "value")) == null) {
                        return RejectionReasonCode.TYPE;
                    }
                    return null;
                },
                row -> {
                    Employee employee = Employee.<Employee>find("entity = ?1 and externalId = ?2", entity,
                            value(row, "external_id")).firstResult();
                    if (employee == null) {
                        return ImportJournal.LineResult.ON_HOLD;
                    }
                    EmployeeMeasure measure = new EmployeeMeasure();
                    measure.employee = employee;
                    measure.measureType = parseMeasureType(value(row, "measure_type"));
                    measure.dimension = emptyToNull(value(row, "dimension"));
                    measure.periodFrom = parseDate(value(row, "period_from"));
                    measure.periodTo = parseDate(value(row, "period_to"));
                    measure.value = parseDecimal(value(row, "value"));
                    measure.persist();
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Parses a job code, returning null when unknown.
     *
     * @param raw the raw value
     * @return the job code or null
     */
    private static JobCode parseJobCode(String raw) {
        try {
            return JobCode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a measure type, returning null when unknown.
     *
     * @param raw the raw value
     * @return the measure type or null
     */
    private static MeasureType parseMeasureType(String raw) {
        try {
            return MeasureType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a date, returning null when malformed.
     *
     * @param raw the raw value
     * @return the date or null
     */
    private static LocalDate parseDate(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Parses a decimal, returning null when malformed.
     *
     * @param raw the raw value
     * @return the decimal or null
     */
    private static BigDecimal parseDecimal(String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reads a column value from a row, trimmed, empty when absent.
     *
     * @param row    the row
     * @param column the column name
     * @return the value
     */
    private static String value(Map<String, String> row, String column) {
        return row.getOrDefault(column, "").trim();
    }

    /**
     * Returns the value, or null when it is empty.
     *
     * @param value the value
     * @return the value or null
     */
    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
