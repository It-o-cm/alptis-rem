package fr.alptis.remuneration.security;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Enforces the separation-of-duties rule of section 3: no one approves what they created or
 * entered themselves. Reused wherever a two-signature object is approved (rule versions,
 * adjustments, payment batches).
 */
@ApplicationScoped
public class SeparationOfDuties {

    /**
     * Ensures the approver is not the creator of the object being approved.
     *
     * @param creator  the user who created or entered the object
     * @param approver the user attempting to approve it
     * @throws SeparationOfDutiesException if the approver is the creator
     */
    public void ensureDifferentApprover(String creator, String approver) {
        if (creator != null && creator.equals(approver)) {
            throw new SeparationOfDutiesException(
                    "Séparation des tâches : vous ne pouvez pas approuver ce que vous avez vous-même créé.");
        }
    }
}
