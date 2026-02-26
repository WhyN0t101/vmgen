package io.vmgen.api.repository;

import io.vmgen.api.models.user.Session;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findById(UUID id);

    @Modifying
    @Query("delete from Session s where s.id = :sessionId and s.user.id = :userId")
    int deleteByIdAndUserId(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId);

    @Modifying
    @Query("delete from Session s where s.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    @Query("select s from Session s join fetch s.user u where s.expiresAt > :now order by s.lastActivityAt desc")
    List<Session> findAllActiveWithUser(@Param("now") Instant now);

    @Modifying
    @Query("delete from Session s where s.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("delete from Session s where s.lastActivityAt <= :cutoff")
    int deleteIdle(@Param("cutoff") Instant cutoff);
}


