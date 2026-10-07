package fr.alptis.remuneration.domain;

/**
 * Raised when a submitted value is invalid. Carries a French message for the interface and maps to
 * HTTP 400.
 */
public class ValidationException extends RuntimeException {

    /**
     * Creates the exception with a user-facing French message.
     *
     * @param message the French message to show
     */
    public ValidationException(String message) {
        super(message);
    }
}
