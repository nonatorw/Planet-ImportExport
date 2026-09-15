package com.planet.importexport.importapi.support;

import java.util.Set;

/**
 * One queued or running job's arrival record, tracked by
 * {@link JobIntersectionGate}.
 *
 * @param jobId     the job's identifier
 * @param idsInFile the job's full {@code id} column value set, snapshotted
 *                  at arrival time
 */
record Arrival(
    String jobId,
    Set<String> idsInFile) {
}
