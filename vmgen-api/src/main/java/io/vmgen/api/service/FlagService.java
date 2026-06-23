package io.vmgen.api.service;

import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.dto.flag.FlagSubmitRequest;
import io.vmgen.api.dto.flag.FlagSubmitResponse;
import io.vmgen.api.exceptions.AccountLockedException;
import io.vmgen.api.exceptions.ForbiddenException;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.exceptions.ValidationException;
import io.vmgen.api.models.flag.Flag;
import io.vmgen.api.models.user.User;
import io.vmgen.api.models.vm.VmRequest;
import io.vmgen.api.models.vm.VmRequestState;
import io.vmgen.api.repository.UserRepository;
import io.vmgen.api.repository.flag.FlagRepository;
import io.vmgen.api.repository.vm.VmRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlagService {

    private static final int MAX_ATTEMPTS_PER_BUILD = 5;
    private static final int LOCKOUT_BUILD_THRESHOLD = 3;
    private static final Duration LOCKOUT_DURATION = Duration.ofHours(1);

    private final VmRequestRepository vmRequestRepository;
    private final FlagRepository flagRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public FlagSubmitResponse submitFlag(UUID buildId, FlagSubmitRequest request, User user, AuditContext ctx) {
        // 1. Check account lockout
        if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil())) {
            long secondsRemaining = Duration.between(Instant.now(), user.getLockedUntil()).toSeconds();
            throw new AccountLockedException(secondsRemaining);
        }

        // 2. Load build under a row-level write lock — must exist, belong to this user,
        // and be READY. The lock serialises concurrent submissions for the same build so
        // the per-build attempt gate below cannot be bypassed by simultaneous requests.
        VmRequest build = vmRequestRepository.findByIdForUpdate(buildId)
                .orElseThrow(() -> new ResourceNotFoundException("Build not found: " + buildId));

        if (!build.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Build does not belong to current user");
        }

        if (build.getState() != VmRequestState.READY) {
            throw new ValidationException(
                    "Validation failed",
                    List.of("Flag submission requires build to be in READY state"));
        }

        // 3. Check per-build attempt limit
        if (build.getFailedFlagAttempts() >= MAX_ATTEMPTS_PER_BUILD) {
            throw new ValidationException(
                    "Validation failed",
                    List.of("Maximum flag attempts reached for this build"));
        }

        // 4. Load registered flag hashes for this build
        List<Flag> flags = flagRepository.findByVmRequest_Id(buildId);
        if (flags.isEmpty()) {
            throw new ValidationException(
                    "Validation failed",
                    List.of("No flags registered for this build"));
        }

        // 5. Constant-time comparison: hash submitted value, check against each stored hash
        String submittedHash = hashFlag(request.getFlagValue().trim().toUpperCase());
        Flag matchedFlag = null;
        for (Flag flag : flags) {
            if (MessageDigest.isEqual(
                    submittedHash.getBytes(StandardCharsets.UTF_8),
                    flag.getFlagValueHash().getBytes(StandardCharsets.UTF_8))) {
                matchedFlag = flag;
                break;
            }
        }

        if (matchedFlag != null) {
            if (matchedFlag.getSolvedAt() == null) {
                matchedFlag.setSolvedAt(Instant.now());
                flagRepository.save(matchedFlag);
            }
            auditService.logFlagSubmitted(user, buildId, matchedFlag.getScript().getId(), true, ctx);
            log.info("Correct flag submitted for build {} by user {}", buildId, user.getId());
            return new FlagSubmitResponse(true, MAX_ATTEMPTS_PER_BUILD - build.getFailedFlagAttempts());
        }

        // 6. Wrong flag — increment failed counter and check for account lockout
        vmRequestRepository.incrementFlagFailures(buildId);
        int newFailedAttempts = build.getFailedFlagAttempts() + 1;

        auditService.logFlagSubmitted(user, buildId, null, false, ctx);
        log.info("Incorrect flag for build {} by user {} (attempt {}/{})",
                buildId, user.getId(), newFailedAttempts, MAX_ATTEMPTS_PER_BUILD);

        if (newFailedAttempts >= MAX_ATTEMPTS_PER_BUILD) {
            long exhaustedBuilds = vmRequestRepository.countExhaustedBuilds(user.getId(), MAX_ATTEMPTS_PER_BUILD);
            if (exhaustedBuilds >= LOCKOUT_BUILD_THRESHOLD) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
                userRepository.save(user);
                auditService.logAccountLocked(user, buildId, ctx);
                log.warn("Account locked for user {} after {} exhausted builds", user.getId(), exhaustedBuilds);
            }
        }

        return new FlagSubmitResponse(false, MAX_ATTEMPTS_PER_BUILD - newFailedAttempts);
    }

    private String hashFlag(String rawFlag) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                    .digest(rawFlag.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
