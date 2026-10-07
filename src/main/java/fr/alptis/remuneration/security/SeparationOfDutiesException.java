package fr.alptis.remuneration.security;

/**
 * Raised when an action breaks the separation-of-duties rule of section 3, for instance when a
 * user tries to approve an object it created itself. Carries a French message for the interface.
 */
public class SeparationOfDutiesException extends RuntimeException {

    /**
     * Creates the exception with a user-facing French message.
     *
     * @param message the French message to show
     */
    public SeparationOfDutiesException(String message) {
        super(message);
    }
}
