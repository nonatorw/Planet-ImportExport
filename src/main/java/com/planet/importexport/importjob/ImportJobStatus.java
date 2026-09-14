package com.planet.importexport.importjob;

/**
 * The {@code import_jobs.status} lifecycle (design.md section 1.3, design.md section 3):
 *
 * <pre>
 * PENDING -&gt; RUNNING -&gt; COMPLETED
 *                     \-&gt; FAILED
 * </pre>
 *
 * <p>{@code PENDING} means the job record is persisted but the job has not yet been dispatched, or
 * is waiting on the ADR-0003 id-intersection serialization gate. {@code RUNNING} means chunked row
 * processing has started. {@code COMPLETED}/{@code FAILED} are terminal.
 *
 * <p>Transition enforcement lives here (not in the repository) so any future caller of {@link
 * #canTransitionTo(ImportJobStatus)} gets a single source of truth for legal moves, per design.md
 * section 3 step sequence. This class does not decide *when* a transition happens (that is Group
 * B's concern, e.g. {@code B9}) — only which transitions are legal.
 */
public enum ImportJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED;

    /**
     * Whether moving from this status to {@code target} is a legal transition per the lifecycle
     * documented on this enum. Terminal statuses ({@code COMPLETED}, {@code FAILED}) allow no
     * further transition.
     */
    public boolean canTransitionTo(ImportJobStatus target) {
        return switch (this) {
            case PENDING -> target == RUNNING;
            case RUNNING -> target == COMPLETED || target == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}
