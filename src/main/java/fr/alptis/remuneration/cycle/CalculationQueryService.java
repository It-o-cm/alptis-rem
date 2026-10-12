package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ProcessingStatus;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read model of the calculation-executions screen (CAL-01, CAL-12): the runs of a period with their
 * status, counters and partial scope, and the units left in failure — the events the engine could
 * not place and held (section 7.2, P8). The screen launches runs through the engine; this service
 * only reports what they recorded.
 */
@ApplicationScoped
public class CalculationQueryService {

    /**
     * Returns the runs of an entity's period and the units left in failure.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the executions view
     */
    public ExecutionsView executions(String entityCode, LocalDate periodMonth) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        List<RunView> runs = new ArrayList<>();
        Period period = entity == null ? null
                : Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period != null) {
            for (CalculationRun run : CalculationRun.<CalculationRun>list("period = ?1 order by startedAt desc, id desc",
                    period)) {
                runs.add(toView(run));
            }
        }
        List<FailedUnit> failed = new ArrayList<>();
        if (entity != null) {
            for (BusinessEvent event : BusinessEvent.<BusinessEvent>list(
                    "entity = ?1 and processingStatus = ?2 order by eventDate desc, id desc", entity,
                    ProcessingStatus.ON_HOLD)) {
                failed.add(new FailedUnit(event.sourceKey,
                        event.contract == null ? null : event.contract.contractNumber,
                        event.coverage == null ? null : event.coverage.coverageCode,
                        event.holdReason == null ? null : event.holdReason.name()));
            }
        }
        return new ExecutionsView(runs, failed);
    }

    /**
     * Maps a run to its view, reading the events/coverages and lines from its counters.
     *
     * @param run the run
     * @return the view
     */
    private static RunView toView(CalculationRun run) {
        long processed = counter(run.counters, "events");
        if (processed == 0) {
            processed = counter(run.counters, "coverages");
        }
        return new RunView(run.getId(), run.runType.name(), run.status.name(), run.launchedBy, run.startedAt,
                run.finishedAt, processed, counter(run.counters, "lines"), partialScope(run.parameters));
    }

    /**
     * Reads one integer counter from a run's counters JSON (a JSON column, always valid), zero when the
     * counters are absent or the counter is not present.
     *
     * @param counters the counters JSON, possibly null
     * @param name     the counter name
     * @return the counter value, or zero
     */
    private static long counter(String counters, String name) {
        if (counters == null) {
            return 0;
        }
        Long value = new JsonObject(counters).getLong(name);
        return value == null ? 0 : value;
    }

    /**
     * Reads the partial scope label from a run's parameters JSON (a JSON column, always valid), null for
     * a full run.
     *
     * @param parameters the parameters JSON, possibly null
     * @return a "TYPE value" label, or null
     */
    private static String partialScope(String parameters) {
        if (parameters == null) {
            return null;
        }
        JsonObject partial = new JsonObject(parameters).getJsonObject("partial");
        if (partial == null) {
            return null;
        }
        return partial.getString("type") + " " + partial.getString("value");
    }

    /**
     * The calculation-executions view of a period.
     *
     * @param runs   the runs, newest first
     * @param failed the units left in failure (held events)
     */
    public record ExecutionsView(List<RunView> runs, List<FailedUnit> failed) {
    }

    /**
     * One calculation run.
     *
     * @param id           the run id
     * @param type         the run type code
     * @param status       the run status code
     * @param launchedBy   who launched it
     * @param startedAt    the start time
     * @param finishedAt   the end time, possibly null
     * @param processed    the number of events or coverages processed
     * @param lines        the number of lines produced
     * @param partialScope the partial scope ("TYPE value"), or null for a full run
     */
    public record RunView(Long id, String type, String status, String launchedBy, OffsetDateTime startedAt,
                          OffsetDateTime finishedAt, long processed, long lines, String partialScope) {
    }

    /**
     * One unit left in failure: an event the engine held.
     *
     * @param sourceKey      the event source key
     * @param contractNumber the contract number, possibly null
     * @param coverageCode   the coverage code, possibly null
     * @param holdReason     the hold reason code, possibly null
     */
    public record FailedUnit(String sourceKey, String contractNumber, String coverageCode, String holdReason) {
    }
}
