package io.vmgen.api.dto.build;

/**
 * Carries OVA download metadata returned by VmRequestService.
 * The controller uses this to set response headers before streaming.
 */
public record BuildDownloadInfo(
        String storagePath,
        String filename,
        long sizeBytes,
        String sha256
) {}