package fr.alptis.remuneration.domain;

/**
 * Raised when a requested business resource does not exist or is out of the user's entity scope.
 * Carries a French message for the interface and maps to HTTP 404. Distinct from the framework's
 * routing not-found so the single-page application's deep links still fall through to it.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates the exception with a user-facing French message.
     *
     * @param message the French message to show
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
