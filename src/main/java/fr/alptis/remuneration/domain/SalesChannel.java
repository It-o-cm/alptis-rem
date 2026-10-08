package fr.alptis.remuneration.domain;

/**
 * Channel through which a contract was subscribed (section 5.2).
 */
public enum SalesChannel {
    /** Paper subscription. */
    PAPER,
    /** Through the Alptis Connect extranet. */
    ALPTIS_CONNECT,
    /** Remotely with electronic signature. */
    REMOTE_E_SIGNATURE,
    /** Any other channel. */
    OTHER
}
