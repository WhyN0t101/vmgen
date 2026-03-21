package io.vmgen.api.controller;

import io.vmgen.api.dto.build.BuildDownloadInfo;
import io.vmgen.api.dto.build.BuildResponse;
import io.vmgen.api.dto.build.CreateBuildRequest;
import io.vmgen.api.dto.build.ProvenanceResponse;
import io.vmgen.api.dto.common.PagedResponse;
import io.vmgen.api.models.user.User;
import io.vmgen.api.service.UserService;
import io.vmgen.api.service.VmRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
    public ResponseEntity<Resource> downloadOva(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest http
    ) {
        User user = userService.requireUser(authentication);
        BuildDownloadInfo info = vmRequestService.getBuildDownloadInfo(id, user, auditContext(http, Map.of()));

        Resource file = new FileSystemResource(Paths.get(info.storagePath()));
        String filename = info.filename() != null ? info.filename() : id + ".ova";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(info.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header("X-Checksum-SHA256", info.sha256() != null ? info.sha256() : "")
                .body(file);
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
}
