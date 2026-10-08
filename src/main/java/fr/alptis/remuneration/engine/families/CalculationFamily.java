package fr.alptis.remuneration.engine.families;

import fr.alptis.remuneration.domain.RuleFamily;

/**
 * A calculation family of section 5.6: the nine ways a rule turns an event into commission
 * (linear, upfront, advance, tiered, portfolio, share, outstanding, fixed, external).
 *
 * <p>A family is a CDI bean discovered by the {@link fr.alptis.remuneration.engine.FamilyRegistry}.
 * At this step a family declares only its code and the JSON Schema of its parameters, which the
 * registry uses to validate every rule version (at entry and at import) and serves to the interface
 * so it can build the family's form. The calculation itself (producing a proposal from an event, a
 * rule version and a read-only state) is added with the engine at steps 5 to 8; adding a family
 * touches neither the engine nor the registry.</p>
 */
public interface CalculationFamily {

    /**
     * Returns the family code this bean implements.
     *
     * @return the family code
     */
    RuleFamily code();

    /**
     * Returns the JSON Schema (draft 2020-12) describing this family's {@code parameters} object.
     * The registry validates a rule version's parameters against it and the interface derives the
     * family form from it.
     *
     * @return the JSON Schema as a string
     */
    String parametersSchema();
}
