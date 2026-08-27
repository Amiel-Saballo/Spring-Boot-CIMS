package com.clinic.inventory.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.inventory.dto.ReceivingDtos;
import com.clinic.inventory.entity.Batch;
import com.clinic.inventory.entity.ClinicLocation;
import com.clinic.inventory.entity.EquipmentUnit;
import com.clinic.inventory.entity.Item;
import com.clinic.inventory.entity.ReceivingLine;
import com.clinic.inventory.entity.ReceivingTransaction;
import com.clinic.inventory.entity.Supplier;
import com.clinic.inventory.entity.UserAccount;
import com.clinic.inventory.enums.BatchStatus;
import com.clinic.inventory.enums.EquipmentStatus;
import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.ItemStatus;
import com.clinic.inventory.enums.ReceivingStatus;
import com.clinic.inventory.enums.TransactionType;
import com.clinic.inventory.exception.BusinessRuleException;
import com.clinic.inventory.exception.ResourceNotFoundException;
import com.clinic.inventory.repository.BatchRepository;
import com.clinic.inventory.repository.ClinicLocationRepository;
import com.clinic.inventory.repository.EquipmentUnitRepository;
import com.clinic.inventory.repository.ItemRepository;
import com.clinic.inventory.repository.ReceivingLineRepository;
import com.clinic.inventory.repository.ReceivingTransactionRepository;
import com.clinic.inventory.repository.SupplierRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReceivingService {
    private final ReceivingTransactionRepository receivingRepository;
    private final ItemRepository itemRepository;
    private final SupplierRepository supplierRepository;
    private final ClinicLocationRepository locationRepository;
    private final BatchRepository batchRepository;
    private final EquipmentUnitRepository equipmentRepository;
    private final ReceivingLineRepository lineRepository;
    private final TransactionLogService transactionLogService;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public Page<ReceivingDtos.Response> list(ReceivingStatus status,
            Long receivedBy, Pageable pageable) {
        Page<ReceivingTransaction> page;
        if (status != null)
            page = receivingRepository.findByStatus(status, pageable);
        else if (receivedBy != null)
            page = receivingRepository.findByReceivedById(receivedBy, pageable);
        else
            page = receivingRepository.findAll(pageable);
        return page.map(mapper::receiving);
    }

    @Transactional(readOnly = true)
    public ReceivingDtos.Response get(Long id) {
        return mapper.receiving(require(id));
    }

    @Transactional
    public ReceivingDtos.Response create(ReceivingDtos.CreateRequest request,
            UserAccount user) {
        if (receivingRepository.findByReferenceNumber(request.referenceNumber())
                .isPresent())
            throw new BusinessRuleException(
                    "Receiving reference number already exists");
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Supplier not found"));
        if (!supplier.isActive())
            throw new BusinessRuleException(
                    "Inactive suppliers cannot be used for new receiving transactions");
        ReceivingTransaction tx = ReceivingTransaction.builder()
                .supplier(supplier).receivedBy(user)
                .status(ReceivingStatus.PENDING)
                .referenceNumber(request.referenceNumber().trim())
                .dateReceived(request.dateReceived())
                .remarks(trim(request.remarks())).build();
        tx.replaceLines(request.lines().stream().map(this::buildLine).toList());
        return mapper.receiving(receivingRepository.save(tx));
    }

    @Transactional
    public ReceivingDtos.Response updateReturned(Long id,
            ReceivingDtos.UpdateReturnedRequest request, UserAccount user) {
        ReceivingTransaction tx = requireReturnedOwned(id, user);
        receivingRepository.findByReferenceNumber(request.referenceNumber())
                .filter(x -> !x.getId().equals(id)).ifPresent(x -> {
                    throw new BusinessRuleException(
                            "Receiving reference number already exists");
                });
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Supplier not found"));
        if (!supplier.isActive())
            throw new BusinessRuleException(
                    "Inactive suppliers cannot be selected");
        tx.setSupplier(supplier);
        tx.setReferenceNumber(request.referenceNumber().trim());
        tx.setDateReceived(request.dateReceived());
        tx.setRemarks(trim(request.remarks()));
        tx.replaceLines(request.lines().stream().map(this::buildLine).toList());
        return mapper.receiving(receivingRepository.save(tx));
    }

    @Transactional
    public ReceivingDtos.Response updateReturnedLine(Long txId, Long lineId,
            ReceivingDtos.LineRequest request, UserAccount user) {
        ReceivingTransaction tx = requireReturnedOwned(txId, user);
        ReceivingLine line = tx.getLines().stream()
                .filter(x -> x.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Receiving line not found"));
        applyLine(line, request);
        return mapper.receiving(receivingRepository.save(tx));
    }

    @Transactional
    public ReceivingDtos.Response resubmit(Long id, UserAccount user) {
        ReceivingTransaction tx = requireReturnedOwned(id, user);
        validateLines(tx.getLines());
        tx.setStatus(ReceivingStatus.PENDING);
        tx.setReturnReason(null);
        tx.setApprovedBy(null);
        return mapper.receiving(receivingRepository.save(tx));
    }

    @Transactional
    public ReceivingDtos.Response cancel(Long id,
            ReceivingDtos.ReasonRequest request, UserAccount user) {
        ReceivingTransaction tx = require(id);
        if (!tx.getReceivedBy().getId().equals(user.getId()))
            throw new BusinessRuleException(
                    "Only the Nurse who submitted the request can cancel it");
        if (tx.getStatus() != ReceivingStatus.PENDING)
            throw new BusinessRuleException(
                    "Only pending receiving requests can be cancelled");
        tx.setStatus(ReceivingStatus.CANCELLED);
        tx.setCancellationReason(request.reason().trim());
        return mapper.receiving(receivingRepository.save(tx));
    }

    @Transactional
    public ReceivingDtos.Response returnToNurse(Long id,
            ReceivingDtos.ReasonRequest request, UserAccount supervisor) {
        ReceivingTransaction tx = require(id);
        if (tx.getStatus() != ReceivingStatus.PENDING)
            throw new BusinessRuleException(
                    "Only pending receiving requests can be returned");
        tx.setStatus(ReceivingStatus.RETURNED);
        tx.setReturnReason(request.reason().trim());
        tx.setApprovedBy(supervisor);
        return mapper.receiving(receivingRepository.save(tx));
    }

    @Transactional
    public ReceivingDtos.Response approve(Long id, UserAccount supervisor) {
        ReceivingTransaction tx = require(id);
        if (tx.getStatus() != ReceivingStatus.PENDING)
            throw new BusinessRuleException(
                    "Only pending receiving requests can be approved");
        validateLines(tx.getLines());

        for (ReceivingLine line : tx.getLines()) {
            Item item = line.getItem();
            if (item.getStatus() != ItemStatus.ACTIVE)
                throw new BusinessRuleException(
                        "Inactive item cannot be received: " + item.getCode());
            int before = totalQuantity(item);
            if (item.getCategory() == ItemCategory.EQUIPMENT) {
                ensureEquipmentUnique(line);
                equipmentRepository.save(EquipmentUnit.builder().item(item)
                        .receivingTransaction(tx)
                        .assetTag(line.getAssetTag().trim())
                        .serialNumber(line.getSerialNumber().trim())
                        .brand(trim(line.getBrand()))
                        .model(trim(line.getModel()))
                        .location(line.getLocation())
                        .acquiredDate(tx.getDateReceived())
                        .status(EquipmentStatus.IN_USE).build());
            } else {
                batchRepository.save(
                        Batch.builder().item(item).receivingTransaction(tx)
                                .batchNumber(blankToNull(line.getBatchNumber()))
                                .quantityReceived(line.getQuantity())
                                .onHand(line.getQuantity())
                                .expiryDate(line.getExpiryDate())
                                .brand(trim(line.getBrand()))
                                .location(line.getLocation())
                                .status(BatchStatus.ACTIVE).build());
            }
            int after = before + line.getQuantity();
            transactionLogService.log(TransactionType.RECEIVING,
                    tx.getReferenceNumber(), supervisor, item, before, after,
                    "Approved receiving of " + line.getQuantity() + " "
                            + item.getUnitOfMeasure().getName() + " of "
                            + item.getName() + ". Quantity: " + before + " -> "
                            + after + ".");
        }
        tx.setStatus(ReceivingStatus.APPROVED);
        tx.setApprovedBy(supervisor);
        tx.setReturnReason(null);
        return mapper.receiving(receivingRepository.save(tx));
    }

    private ReceivingLine buildLine(ReceivingDtos.LineRequest request) {
        ReceivingLine line = new ReceivingLine();
        applyLine(line, request);
        return line;
    }

    private void applyLine(ReceivingLine line,
            ReceivingDtos.LineRequest request) {
        Item item = itemRepository.findById(request.itemId()).orElseThrow(
                () -> new ResourceNotFoundException("Item not found"));
        ClinicLocation location = locationRepository
                .findById(request.locationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Location not found"));
        line.setItem(item);
        line.setLocation(location);
        line.setQuantity(request.quantity());
        line.setBrand(trim(request.brand()));
        if (item.getCategory() == ItemCategory.EQUIPMENT) {
            if (request.quantity() != 1)
                throw new BusinessRuleException(
                        "Each equipment unit must be entered on its own receiving line");
            if (isBlank(request.serialNumber()) || isBlank(request.assetTag()))
                throw new BusinessRuleException(
                        "Serial number and asset tag are required for equipment");
            line.setModel(trim(request.model()));
            line.setSerialNumber(request.serialNumber().trim());
            line.setAssetTag(request.assetTag().trim());
            line.setBatchNumber(null);
            line.setExpiryDate(null);
        } else {
            if (item.getCategory() == ItemCategory.MEDICINE) {
                if (request.expiryDate() == null) {
                    throw new BusinessRuleException(
                            "Expiry date is required for medicines");
                }
                if (request.expiryDate().isBefore(LocalDate.now())) {
                    throw new BusinessRuleException(
                            "Medicine expiry date cannot be in the past");
                }
            }

            line.setBatchNumber(blankToNull(request.batchNumber())); // optional
                                                                     // by
                                                                     // requirement
            line.setExpiryDate(request.expiryDate());
            line.setModel(null);
            line.setSerialNumber(null);
            line.setAssetTag(null);
        }
    }

    private void validateLines(List<ReceivingLine> lines) {
        if (lines == null || lines.isEmpty())
            throw new BusinessRuleException(
                    "At least one receiving line is required");
        for (ReceivingLine line : lines) {
            if (line.getLocation() == null)
                throw new BusinessRuleException(
                        "Location is required for every receiving item");
            if (line.getItem().getCategory() == ItemCategory.MEDICINE) {
                if (line.getExpiryDate() == null) {
                    throw new BusinessRuleException(
                            "Expiry date is required for medicine: "
                                    + line.getItem().getName());
                }
                if (line.getExpiryDate().isBefore(LocalDate.now())) {
                    throw new BusinessRuleException(
                            "Medicine is already expired: "
                                    + line.getItem().getName());
                }
            }
            if (line.getItem().getCategory() == ItemCategory.EQUIPMENT) {
                if (line.getQuantity() != 1)
                    throw new BusinessRuleException(
                            "Each equipment unit must be entered on its own receiving line");
                if (isBlank(line.getSerialNumber())
                        || isBlank(line.getAssetTag()))
                    throw new BusinessRuleException(
                            "Serial number and asset tag are required for equipment");
            }
        }
    }

    private void ensureEquipmentUnique(ReceivingLine line) {
        if (equipmentRepository.existsByAssetTagIgnoreCase(line.getAssetTag()))
            throw new BusinessRuleException(
                    "Asset tag already exists: " + line.getAssetTag());
        if (equipmentRepository
                .existsBySerialNumberIgnoreCase(line.getSerialNumber()))
            throw new BusinessRuleException(
                    "Serial number already exists: " + line.getSerialNumber());
    }

    public ReceivingTransaction require(Long id) {
        return receivingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Receiving transaction not found"));
    }

    private ReceivingTransaction requireReturnedOwned(Long id,
            UserAccount user) {
        ReceivingTransaction tx = require(id);
        if (tx.getStatus() != ReceivingStatus.RETURNED)
            throw new BusinessRuleException(
                    "Only returned receiving transactions can be edited");
        if (!tx.getReceivedBy().getId().equals(user.getId()))
            throw new BusinessRuleException(
                    "Only the submitting Nurse can edit this returned transaction");
        return tx;
    }

    private int totalQuantity(Item item) {
        if (item.getCategory() == ItemCategory.EQUIPMENT)
            return (int) equipmentRepository.findAll().stream()
                    .filter(e -> e.getItem().getId().equals(item.getId())
                            && e.getStatus() != EquipmentStatus.DISPOSED)
                    .count();
        return batchRepository.sumOnHandByItem(item.getId(), BatchStatus.ACTIVE)
                .intValue();
    }

    private String trim(String v) {
        return v == null ? null : v.trim();
    }

    private String blankToNull(String v) {
        return isBlank(v) ? null : v.trim();
    }

    private boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
