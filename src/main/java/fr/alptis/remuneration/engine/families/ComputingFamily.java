package fr.alptis.remuneration.engine.families;

import java.util.List;

/**
 * A calculation family that can turn an event into proposed lines (D2). The core families delivered
 * at step 5 (linear, fixed, external) implement this; the others gain it as the engine grows (steps
 * 6 to 8). A rule whose family is not yet a {@code ComputingFamily} cannot be calculated, and its
 * events are held.
 */
public interface ComputingFamily extends CalculationFamily {

    /**
     * Proposes the commission lines for an event, without touching any state (D2). The returned
     * lines carry their own amounts, rounded to the cent (P13), and their explanation (D14).
     *
     * @param input the read-only calculation input
     * @return the proposed lines (possibly empty)
     */
    List<ProposedLine> propose(CalculationInput input);
}
