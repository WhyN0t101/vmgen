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
     * Returns the claimed job's ID, or null if no jobs available.
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
}
