package fr.alptis.remuneration.domain;

/**
 * Commercial nature of a distributor (section 5.1).
 */
public enum DistributorType {
    /** Independent broker or wealth-management adviser. */
    INDEPENDENT,
    /** Member of a distributor group that may receive part of its remuneration. */
    GROUP_MEMBER,
    /** Distance-selling distributor. */
    REMOTE_SALES,
    /** Mandated agent paid by retrocession on commissions received by the entity. */
    AGENT
}
