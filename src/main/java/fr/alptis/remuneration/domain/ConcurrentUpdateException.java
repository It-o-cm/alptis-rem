package fr.alptis.remuneration.domain;

/**
 * Raised when a caller tries to save an object that was modified concurrently (optimistic lock
 * conflict). Carries a French message for the interface and maps to HTTP 409.
 */
public class ConcurrentUpdateException extends RuntimeException {

    /**
     * Creates the exception with a user-facing French message.
     *
     * @param message the French message to show
     */
    public ConcurrentUpdateException(String message) {
        super(message);
    }
}
