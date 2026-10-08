package fr.alptis.remuneration.domain;

/**
 * Signature state of an agreement (section 5.4); an enrollment to a reserved rule activates only once its agreement is signed.
 */
public enum SignatureStatus {
    /** The agreement is recorded but not yet signed; dependent enrollments stay inactive. */
    NOT_SIGNED,
    /** The agreement is signed; dependent enrollments may become active. */
    SIGNED
}
