package com.ebee.supplier.service;

import com.ebee.supplier.dto.SupplierRequest;
import com.ebee.supplier.dto.SupplierResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface SupplierService {

    SupplierResponse createSupplier(SupplierRequest request);

    SupplierResponse getSupplierById(UUID id);

    Page<SupplierResponse> getAllSuppliers(String search, Pageable pageable);

    List<SupplierResponse> searchSuppliers(String search);

    SupplierResponse updateSupplier(UUID id, SupplierRequest request);

    void deactivateSupplier(UUID id);
}
