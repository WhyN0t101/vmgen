package io.vmgen.api.repository;

import io.vmgen.api.models.VmRequest;
import io.vmgen.api.models.VmRequestState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    @Query("""
        SELECT vr FROM VmRequest vr 
        WHERE vr.state IN ('building', 'exporting') 
        AND vr.lastProgressAt < :threshold
        """)
    List<VmRequest> findStaleJobs(@Param("threshold") Instant threshold);

    /**
     * Find jobs by user.
     */
    List<VmRequest> findByUserIdOrderBySubmittedAtDesc(UUID userId);

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
}
