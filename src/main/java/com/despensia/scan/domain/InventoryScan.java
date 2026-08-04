package com.despensia.scan.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_scan")
public class InventoryScan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false)
    private ScanType scanType;

    @Column(name = "image_path", nullable = false)
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScanStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    /**
     * Raw JSON blob storing the scan result (PRODUCT or RECEIPT data).
     * Populated after successful processing; NULL for PENDING/PROCESSING/FAILED scans.
     */
    @Column(name = "result_data")
    private String resultData;

    @OneToMany(mappedBy = "inventoryScan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceiptLineItem> lineItems;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public InventoryScan() {}

    public InventoryScan(ScanType scanType, String imagePath) {
        this.scanType = scanType;
        this.imagePath = imagePath;
        this.status = ScanStatus.PENDING;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ScanType getScanType() { return scanType; }
    public void setScanType(ScanType scanType) { this.scanType = scanType; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public ScanStatus getStatus() { return status; }
    public void setStatus(ScanStatus status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getResultData() { return resultData; }
    public void setResultData(String resultData) { this.resultData = resultData; }

    public List<ReceiptLineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<ReceiptLineItem> lineItems) { this.lineItems = lineItems; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public enum ScanType {
        PRODUCT,
        RECEIPT
    }

    public enum ScanStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
