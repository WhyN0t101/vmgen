package io.vmgen.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.dto.telemetry.TelemetryUploadResponse;
import io.vmgen.api.exceptions.ForbiddenException;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.exceptions.ValidationException;
import io.vmgen.api.models.telemetry.TelemetryUpload;
import io.vmgen.api.models.user.User;
import io.vmgen.api.models.vm.VmRequest;
import io.vmgen.api.models.vm.VmRequestState;
import io.vmgen.api.repository.telemetry.TelemetryRepository;
import io.vmgen.api.repository.vm.VmRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryService {

    private static final int MAX_SIZE_BYTES = 1_048_576; // 1 MB

    private final VmRequestRepository vmRequestRepository;
    private final TelemetryRepository telemetryRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public TelemetryUploadResponse uploadTelemetry(UUID buildId, JsonNode body, User user) {
        byte[] jsonBytes = serialize(body);

        if (jsonBytes.length > MAX_SIZE_BYTES) {
        throw new ValidationException(
                "Validation failed",
                List.of("Telemetry file exceeds 1 MB limit"));

    }
        VmRequest build = vmRequestRepository.findById(buildId)
                .orElseThrow(() -> new ResourceNotFoundException("Build not found: " + buildId));

        if (!build.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Build does not belong to current user");
        }

        if (!Boolean.TRUE.equals(build.getTelemetryEnabled())) {
        throw new ValidationException(
                "Validation failed",
                List.of("Telemetry is not enabled for this build"));

    }
        if (build.getState() != VmRequestState.READY) {
            throw new ValidationException(
                    "Validation failed",
                    List.of("Telemetry can only be submitted for READY builds"));

        }

        String sha256 = computeSha256(jsonBytes);
        String rawJson = new String(jsonBytes, StandardCharsets.UTF_8);

        // Upsert: overwrite any existing (non-deleted) upload for this build
        TelemetryUpload upload = telemetryRepository
                .findByVmRequest_IdAndDeletedAtIsNull(buildId)
                .orElseGet(TelemetryUpload::new);

        upload.setVmRequest(build);
        upload.setUser(user);
        upload.setFileSha256(sha256);
        upload.setFileSizeBytes((long) jsonBytes.length);
        upload.setRawJson(rawJson);
        upload.setUploadedAt(Instant.now());
        upload = telemetryRepository.save(upload);

        log.info("Telemetry uploaded for build {} by user {}", buildId, user.getId());
        return toResponse(upload);
    }

    @Transactional
    public void deleteTelemetry(UUID buildId, User user, AuditContext ctx) {
        VmRequest build = vmRequestRepository.findById(buildId)
                .orElseThrow(() -> new ResourceNotFoundException("Build not found: " + buildId));
        if (!build.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Build does not belong to current user");
        }
        TelemetryUpload upload = telemetryRepository
                .findByVmRequest_IdAndDeletedAtIsNull(buildId)
                .orElseThrow(() -> new ResourceNotFoundException("No telemetry found for build: " + buildId));
        upload.setDeletedAt(Instant.now());
        telemetryRepository.save(upload);
        log.info("Telemetry deleted for build {} by user {}", buildId, user.getId());
        auditService.logTelemetryDeleted(user, buildId, ctx);
    }

    @Transactional(readOnly = true)
    public TelemetryUploadResponse getAdminTelemetry(UUID buildId) {
        TelemetryUpload upload = telemetryRepository.findByVmRequest_IdAndDeletedAtIsNull(buildId)
                .orElseThrow(() -> new ResourceNotFoundException("No telemetry found for build: " + buildId));
        return toResponse(upload);
    }

    private TelemetryUploadResponse toResponse(TelemetryUpload upload) {
        return new TelemetryUploadResponse(
                upload.getId(),
                upload.getVmRequest().getId(),
                upload.getFileSha256(),
                upload.getFileSizeBytes(),
                upload.getUploadedAt(),
                upload.getRawJson()
        );
    }

    private byte[] serialize(JsonNode body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
        throw new ValidationException(
                "Validation failed",
                List.of("Failed to serialize telemetry JSON"));

    }

    }

    private String computeSha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
