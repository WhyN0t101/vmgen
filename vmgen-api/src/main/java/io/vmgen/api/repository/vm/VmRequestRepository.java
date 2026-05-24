package io.vmgen.api.repository.vm;

import io.vmgen.api.models.vm.VmRequest;
import io.vmgen.api.models.vm.VmRequestState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface VmRequestRepository extends JpaRepository<VmRequest, UUID> {

    /**
     * Atomically claim the next queued job for a worker.
     * Uses FOR UPDATE SKIP LOCKED to prevent race conditions.
     * Returns the number of rows updated (0 or 1).
     * 
     * <p>Query Analysis (EXPLAIN ANALYZE on 1000 rows, 20% queued):
     * <pre>
     * CTE Scan + LockRows + Index Scan on idx_vm_requests_state
     * Execution time: ~0.5ms
     * </pre>
     */
    @Modifying
    @Query(value = """
        WITH claimed AS (
            SELECT id 
            FROM vm_requests 
            WHERE state = 'QUEUED' 
            ORDER BY submitted_at 
            LIMIT 1 
            FOR UPDATE SKIP LOCKED
        )
        UPDATE vm_requests 
        SET 
            state = 'BUILDING',
            claimed_at = NOW(),
            claimed_by_worker_id = :workerId,
            building_started_at = NOW(),
            last_progress_at = NOW(),
            worker_last_heartbeat_at = NOW()
        WHERE id = (SELECT id FROM claimed)
        """, nativeQuery = true)
    int claimNextJobUpdate(@Param("workerId") String workerId);

    /**
     * Find the job just claimed by a worker (most recently claimed).
     */
    Optional<VmRequest> findFirstByClaimedByWorkerIdAndStateOrderByClaimedAtDesc(
            String claimedByWorkerId, VmRequestState state);

    /**
     * Find jobs claimed by a specific worker.
     */
    List<VmRequest> findByClaimedByWorkerIdAndStateIn(String workerId, List<VmRequestState> states);

    /**
     * Find jobs by state.
     */
    List<VmRequest> findByState(VmRequestState state);

    /**
     * Find jobs that haven't had progress updates (for timeout detection).
     */
    @Query("SELECT vr FROM VmRequest vr WHERE vr.state IN :states AND (vr.lastProgressAt IS NULL OR vr.lastProgressAt < :threshold)")
    List<VmRequest> findStaleJobs(@Param("states") List<VmRequestState> states, @Param("threshold") Instant threshold);

    /**
     * Find jobs that have been running longer than the given wall-clock duration (absolute timeout).
     */
    @Query("SELECT vr FROM VmRequest vr WHERE vr.state IN :states AND vr.submittedAt < :threshold")
    List<VmRequest> findLongRunningJobs(@Param("states") List<VmRequestState> states, @Param("threshold") Instant threshold);

    /**
     * Find jobs by user (paginated).
     */
    Page<VmRequest> findByUser_IdOrderBySubmittedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find a VmRequest with its template eagerly loaded (avoids N+1 query).
     */
    @Query("SELECT vr FROM VmRequest vr LEFT JOIN FETCH vr.template WHERE vr.id = :id")
    Optional<VmRequest> findByIdWithTemplate(@Param("id") UUID id);

    /**
     * Find a VmRequest with user and template eagerly loaded.
     */
    @Query("SELECT vr FROM VmRequest vr LEFT JOIN FETCH vr.user LEFT JOIN FETCH vr.template WHERE vr.id = :id")
    Optional<VmRequest> findByIdWithUserAndTemplate(@Param("id") UUID id);

    /**
     * Increment the failed flag attempt counter for a single build.
     */
    @Modifying
    @Query("UPDATE VmRequest vr SET vr.failedFlagAttempts = vr.failedFlagAttempts + 1 WHERE vr.id = :buildId")
    void incrementFlagFailures(@Param("buildId") UUID buildId);

    /**
     * Count builds owned by a user whose failed flag attempts have reached or exceeded the threshold.
     * Used to determine when to trigger a flag-based account lockout.
     */
    @Query("SELECT COUNT(vr) FROM VmRequest vr WHERE vr.user.id = :userId AND vr.failedFlagAttempts >= :threshold")
    long countExhaustedBuilds(@Param("userId") UUID userId, @Param("threshold") int threshold);

    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (completed_at - submitted_at)))
            FROM vm_requests
            WHERE state = 'READY' AND completed_at IS NOT NULL
              AND completed_at > NOW() - INTERVAL '30 days'
            """, nativeQuery = true)
    Double averageBuildDurationSeconds();

    /**
     * Used by the archival scheduled task to expire OVA artefacts after the retention period.
     */
    @Query("SELECT vr FROM VmRequest vr WHERE vr.state = 'READY' AND vr.completedAt < :cutoff")
    List<VmRequest> findReadyBuildsCompletedBefore(@Param("cutoff") Instant cutoff);
}
