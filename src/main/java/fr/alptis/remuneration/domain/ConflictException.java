package fr.alptis.remuneration.domain;

/**
 * Raised when an action conflicts with the current state, for instance creating a row that already
 * exists. Carries a French message for the interface and maps to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    /**
     * Creates the exception with a user-facing French message.
     *
     * @param message the French message to show
     */
    public ConflictException(String message) {
        super(message);
    }
}
