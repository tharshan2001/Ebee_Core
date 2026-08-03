package com.ebee.combo.service;

import com.ebee.combo.dto.ComboRequest;
import com.ebee.combo.dto.ComboResponse;
import com.ebee.common.result.Result;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ComboService {

    ComboResponse createCombo(ComboRequest request);

    ComboResponse updateCombo(UUID id, ComboRequest request);

    void deleteCombo(UUID id);

    Result<ComboResponse> getComboById(UUID id);

    Page<ComboResponse> listActiveCombos(Pageable pageable);

    Page<ComboResponse> listActiveCombos(String search, Pageable pageable);

    Page<ComboResponse> listAllCombos(Pageable pageable);

    Page<ComboResponse> listAllCombos(String search, Pageable pageable);
}
