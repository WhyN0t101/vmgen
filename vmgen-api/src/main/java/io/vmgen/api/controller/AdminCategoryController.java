package io.vmgen.api.controller;

import io.vmgen.api.dto.category.CategoryDetailDto;
import io.vmgen.api.dto.category.CategoryListDto;
import io.vmgen.api.dto.category.CategoryReorderRequest;
import io.vmgen.api.dto.category.CreateCategoryRequest;
import io.vmgen.api.dto.category.UpdateCategoryRequest;
import io.vmgen.api.dto.common.StatusResponse;
import io.vmgen.api.service.UserService;
import io.vmgen.api.service.VulnerabilityCategoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final VulnerabilityCategoryService categoryService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<CategoryListDto>> listCategories(
            @RequestParam(name = "activeOnly", defaultValue = "true") boolean activeOnly
    ) {
        return ResponseEntity.ok(categoryService.list(activeOnly));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDetailDto> getCategory(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.getDetail(id));
    }

    @PostMapping
    public ResponseEntity<CategoryDetailDto> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            Authentication authentication
    ) {
        String adminUsername = userService.requireAdminUser(authentication).getUsername();
        CategoryDetailDto created = categoryService.create(request, adminUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDetailDto> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @PostMapping("/{id}/deprecate")
    public ResponseEntity<StatusResponse> deprecateCategory(@PathVariable UUID id) {
        categoryService.deprecate(id);
        return ResponseEntity.ok(new StatusResponse("ok"));
    }

    @PostMapping("/reorder")
    public ResponseEntity<StatusResponse> reorderCategories(
            @Valid @RequestBody List<CategoryReorderRequest> requests
    ) {
        categoryService.reorder(requests);
        return ResponseEntity.ok(new StatusResponse("ok"));
    }
}

