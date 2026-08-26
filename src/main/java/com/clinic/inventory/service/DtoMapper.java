package com.clinic.inventory.service;

import com.clinic.inventory.dto.*;
import com.clinic.inventory.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class DtoMapper {
    public ItemDtos.Response item(Item i) {
        return new ItemDtos.Response(i.getId(), i.getCode(), i.getName(), i.getCategory(),
                i.getUnitOfMeasure().getId(), i.getUnitOfMeasure().getName(),
                i.getReorderLevel(), i.getReorderQuantity(), i.getStatus());
    }

    public SupplierDtos.Response supplier(Supplier s) {
        return new SupplierDtos.Response(s.getId(), s.getName(), s.getContactPerson(), s.getContactNo(), s.getAddress(), s.isActive());
    }

    public ReceivingDtos.LineResponse receivingLine(ReceivingLine l) {
        return new ReceivingDtos.LineResponse(l.getId(), l.getItem().getId(), l.getItem().getCode(), l.getItem().getName(),
                l.getItem().getCategory(), l.getQuantity(), l.getItem().getUnitOfMeasure().getName(), l.getBrand(), l.getBatchNumber(),
                l.getExpiryDate(), l.getModel(), l.getSerialNumber(), l.getAssetTag(), l.getLocation().getId(), l.getLocation().getName());
    }

    public ReceivingDtos.Response receiving(ReceivingTransaction r) {
        return new ReceivingDtos.Response(r.getId(), r.getSupplier().getId(), r.getSupplier().getName(), r.getReferenceNumber(),
                r.getDateReceived(), r.getRemarks(), r.getReturnReason(), r.getStatus(), r.getReceivedBy().getFullName(),
                r.getApprovedBy() == null ? null : r.getApprovedBy().getFullName(),
                r.getLines().stream().map(this::receivingLine).toList());
    }

    public BatchDtos.Response batch(Batch b) {
        boolean editable = b.getReceivingTransaction().getStatus() != com.clinic.inventory.enums.ReceivingStatus.APPROVED;
        return new BatchDtos.Response(b.getId(), b.getItem().getId(), b.getItem().getCode(), b.getItem().getName(), b.getBatchNumber(),
                b.getBrand(), b.getExpiryDate(), b.getOnHand(), b.getItem().getUnitOfMeasure().getName(), b.getLocation().getName(),
                b.getStatus(), editable);
    }

    public EquipmentDtos.Response equipment(EquipmentUnit e) {
        return new EquipmentDtos.Response(e.getId(), e.getItem().getId(), e.getItem().getCode(), e.getItem().getName(),
                e.getAssetTag(), e.getSerialNumber(), e.getBrand(), e.getModel(), e.getLocation().getId(), e.getLocation().getName(),
                e.getAcquiredDate(), e.getStatus());
    }

    public IssuanceDtos.LineResponse issuanceLine(IssuanceLine l) {
        return new IssuanceDtos.LineResponse(l.getId(), l.getItem().getId(), l.getItem().getName(), l.getBatch().getId(),
                l.getBatch().getBatchNumber(), l.getQuantity(), l.getItem().getUnitOfMeasure().getName());
    }

    public IssuanceDtos.Response issuance(IssuanceTransaction i) {
        return new IssuanceDtos.Response(i.getId(), i.getReferenceNumber(), i.getDateIssued(), i.getEmployeeNumber(), i.getEmployeeName(),
                i.getDepartment(), i.getSupervisor(), i.getChiefComplaint(), i.getDisposition(), i.getRemarks(), i.getRecordedBy().getFullName(),
                i.getLines().stream().sorted(Comparator.comparing(l -> l.getBatch().getExpiryDate(), Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(this::issuanceLine).toList());
    }

    public DisposalDtos.Response disposal(DisposalRecord d) {
        return new DisposalDtos.Response(d.getId(), d.getReferenceNumber(), d.getDisposalDate(), d.getItem().getId(), d.getItem().getName(),
                d.getBatch() == null ? null : d.getBatch().getId(), d.getEquipmentUnit() == null ? null : d.getEquipmentUnit().getId(),
                d.getQuantity(), d.getReason(), d.getRemarks(), d.getRecordedBy().getFullName());
    }

    public TransactionDtos.Response transaction(TransactionLog t) {
        return new TransactionDtos.Response(t.getId(), t.getTransactionDate(), t.getTransactionType(), t.getReferenceNumber(),
                t.getUser().getFullName(), t.getAffectedItem() == null ? null : t.getAffectedItem().getId(),
                t.getAffectedItem() == null ? null : t.getAffectedItem().getName(),
                t.getAffectedItem() == null ? null : t.getAffectedItem().getCategory(), t.getQuantityBefore(), t.getQuantityAfter(), t.getDetail());
    }
}
