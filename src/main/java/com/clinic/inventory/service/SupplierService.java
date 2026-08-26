package com.clinic.inventory.service;

import com.clinic.inventory.dto.SupplierDtos;
import com.clinic.inventory.entity.Supplier;
import com.clinic.inventory.enums.ReceivingStatus;
import com.clinic.inventory.exception.*;
import com.clinic.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final ReceivingTransactionRepository receivingRepository;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public Page<SupplierDtos.Response> list(Boolean active, Pageable pageable) {
        Page<Supplier> page = active == null ? supplierRepository.findAll(pageable) : supplierRepository.findByActive(active, pageable);
        return page.map(mapper::supplier);
    }

    @Transactional
    public SupplierDtos.Response create(SupplierDtos.UpsertRequest request) {
        if (supplierRepository.findByNameIgnoreCase(request.name()).isPresent()) throw new BusinessRuleException("Supplier already exists");
        return mapper.supplier(supplierRepository.save(fromRequest(new Supplier(), request)));
    }

    @Transactional
    public SupplierDtos.Response update(Long id, SupplierDtos.UpsertRequest request) {
        Supplier supplier = require(id);
        var sameName = supplierRepository.findByNameIgnoreCase(request.name());
        if (sameName.isPresent() && !sameName.get().getId().equals(id)) throw new BusinessRuleException("Supplier already exists");
        return mapper.supplier(supplierRepository.save(fromRequest(supplier, request)));
    }

    @Transactional
    public void softDelete(Long id) {
        Supplier supplier = require(id);
        if (receivingRepository.existsBySupplierIdAndStatusIn(id, List.of(ReceivingStatus.PENDING, ReceivingStatus.RETURNED)))
            throw new BusinessRuleException("Supplier cannot be deleted while it has pending or returned receiving transactions");
        if (receivingRepository.existsBySupplierIdAndStatusAndDateReceivedGreaterThanEqual(id, ReceivingStatus.APPROVED, LocalDate.now().minusYears(3)))
            throw new BusinessRuleException("Supplier cannot be deleted because it has an approved receiving transaction within the past 3 years");
        if (receivingRepository.countApprovedActiveItemFromSupplier(id, ReceivingStatus.APPROVED, com.clinic.inventory.enums.ItemStatus.ACTIVE) > 0)
            throw new BusinessRuleException("Supplier cannot be deleted because an active system item was received from this supplier");
        supplier.setActive(false);
        supplierRepository.save(supplier);
    }

    @Transactional
    public SupplierDtos.Response reactivate(Long id) {
        Supplier supplier = require(id); supplier.setActive(true); return mapper.supplier(supplierRepository.save(supplier));
    }

    public Supplier require(Long id) { return supplierRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Supplier not found")); }
    private Supplier fromRequest(Supplier s, SupplierDtos.UpsertRequest r) {
        s.setName(r.name().trim()); s.setContactPerson(trim(r.contactPerson())); s.setContactNo(trim(r.contactNo())); s.setAddress(trim(r.address())); return s;
    }
    private String trim(String v) { return v == null ? null : v.trim(); }
}
