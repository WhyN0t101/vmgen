package io.vmgen.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.vmgen.api.dto.build.BuildDownloadInfo;
import io.vmgen.api.dto.build.BuildResponse;
import io.vmgen.api.dto.build.CreateBuildRequest;
import io.vmgen.api.dto.build.ProvenanceResponse;
import io.vmgen.api.dto.common.PagedResponse;
import io.vmgen.api.dto.flag.FlagSubmitRequest;
import io.vmgen.api.dto.flag.FlagSubmitResponse;
import io.vmgen.api.dto.telemetry.TelemetryUploadResponse;
import io.vmgen.api.models.user.User;
import io.vmgen.api.service.FlagService;
import io.vmgen.api.service.TelemetryService;
import io.vmgen.api.service.UserService;
import io.vmgen.api.service.VmRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static io.vmgen.api.utils.HttpUtils.auditContext;

@RestController
@RequestMapping("/api/builds")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
@RequiredArgsConstructor
public class BuildController {

    private final VmRequestService vmRequestService;
    private final UserService userService;
    private final FlagService flagService;
    private final TelemetryService telemetryService;

    @PostMapping
    public ResponseEntity<BuildResponse> createBuild(
            @Valid @RequestBody CreateBuildRequest request,
            Authentication authentication,
            HttpServletRequest http
    ) {
        User user = userService.requireUser(authentication);
        BuildResponse response = vmRequestService.createBuild(user, request, auditContext(http, Map.of()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<BuildResponse>> getUserBuilds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        User user = userService.requireUser(authentication);
        return ResponseEntity.ok(vmRequestService.getUserBuilds(user, page, size));
    }


    @GetMapping("/{id}")
    public ResponseEntity<BuildResponse> getBuild(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        User user = userService.requireUser(authentication);
        return ResponseEntity.ok(vmRequestService.getBuild(id, user));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadOva(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest http,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        User user = userService.requireUser(authentication);
        BuildDownloadInfo info = vmRequestService.getBuildDownloadInfo(id, user, auditContext(http, Map.of()));

        Resource file = new FileSystemResource(Paths.get(info.storagePath()));
        String filename = info.filename() != null ? info.filename() : id + ".ova";
        long fileLength = info.sizeBytes();
        String checksum = info.sha256() != null ? info.sha256() : "";

        if (rangeHeader == null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header("X-Checksum-SHA256", checksum)
                    .body(file);
        }

        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                    .build();
        }

        // Multi-range not supported — fall back to full file
        if (ranges.size() != 1) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header("X-Checksum-SHA256", checksum)
                    .body(file);
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(fileLength);
        long end   = range.getRangeEnd(fileLength);

        if (start > end || start >= fileLength) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                    .build();
        }

        long contentLength = end - start + 1;
        ResourceRegion region = new ResourceRegion(file, start, contentLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(contentLength)
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header("X-Checksum-SHA256", checksum)
                .body(region);
    }

    @GetMapping("/{id}/provenance")
    public ResponseEntity<ProvenanceResponse> getProvenance(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest http
    ) {
        User user = userService.requireUser(authentication);
        return ResponseEntity.ok(vmRequestService.getBuildProvenance(id, user, auditContext(http, Map.of())));
    }

    @PostMapping(value = "/{id}/telemetry", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TelemetryUploadResponse> uploadTelemetry(
            @PathVariable UUID id,
            @RequestBody JsonNode body,
            Authentication authentication
    ) {
        User user = userService.requireUser(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(telemetryService.uploadTelemetry(id, body, user));
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<BuildResponse> cloneBuild(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest http
    ) {
        User user = userService.requireUser(authentication);
        BuildResponse response = vmRequestService.cloneBuild(id, user, auditContext(http, Map.of()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/flag")
    public ResponseEntity<FlagSubmitResponse> submitFlag(
            @PathVariable UUID id,
            @Valid @RequestBody FlagSubmitRequest request,
            Authentication authentication,
            HttpServletRequest http
    ) {
        User user = userService.requireUser(authentication);
        return ResponseEntity.ok(flagService.submitFlag(id, request, user, auditContext(http, Map.of())));
    }
}
