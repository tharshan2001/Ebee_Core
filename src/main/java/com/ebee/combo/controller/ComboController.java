package com.ebee.combo.controller;

import com.ebee.combo.dto.ComboRequest;
import com.ebee.combo.dto.ComboResponse;
import com.ebee.combo.service.ComboService;
import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ComboController {

    private final ComboService comboService;

    @PostMapping("/admin/combos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComboResponse>> createCombo(@Valid @RequestBody ComboRequest request) {
        ComboResponse combo = comboService.createCombo(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Combo created successfully", combo));
    }

    @PutMapping("/admin/combos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComboResponse>> updateCombo(
            @PathVariable UUID id, @Valid @RequestBody ComboRequest request) {
        ComboResponse combo = comboService.updateCombo(id, request);
        return ResponseEntity.ok(ApiResponse.success("Combo updated successfully", combo));
    }

    @DeleteMapping("/admin/combos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCombo(@PathVariable UUID id) {
        comboService.deleteCombo(id);
        return ResponseEntity.ok(ApiResponse.success("Combo deactivated successfully", null));
    }

    @GetMapping("/combos/{id}")
    public ResponseEntity<ApiResponse<ComboResponse>> getComboById(@PathVariable UUID id) {
        var result = comboService.getComboById(id);
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(result.getMessage()));
        }
        return ResponseEntity.ok(ApiResponse.success("Combo retrieved successfully", result.getData()));
    }

    @GetMapping("/combos")
    public ResponseEntity<ApiResponse<PageResponse<ComboResponse>>> listActiveCombos(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ComboResponse> combos = comboService.listActiveCombos(search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Combos retrieved successfully",
                PageResponse.from(combos)));
    }

    @GetMapping("/admin/combos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ComboResponse>>> listAllCombos(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ComboResponse> combos = comboService.listAllCombos(search, pageable);
        return ResponseEntity.ok(ApiResponse.success("All combos retrieved successfully",
                PageResponse.from(combos)));
    }
}
