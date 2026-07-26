package com.despensia.product.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "product_item")
public class ProductItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;

    @Column(name = "estimated_days_remaining")
    private Integer estimatedDaysRemaining;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "expiration_source")
    private ExpirationSource expirationSource;

    @Column(name = "is_expired")
    private Boolean isExpired;

    // Nullable reference to the receipt from which this product was extracted (OCR receipt parsing)
    @Column(name = "receipt_id")
    private String receiptId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ProductItem() {}

    public ProductItem(String name, ProductType productType) {
        this.name = name;
        this.productType = productType;
    }

    public void updateEstimatedDays(Integer days) {
        this.estimatedDaysRemaining = days;
        this.expirationDate = LocalDate.now().plus(days, ChronoUnit.DAYS);
        this.expirationSource = ExpirationSource.USER_OVERRIDE;
        recalculateExpired();
    }

    public void applyDefaultStrategy(DefaultExpirationStrategy strategy) {
        Integer defaultDays = strategy.defaultFor(this.productType);
        this.estimatedDaysRemaining = defaultDays;
        this.expirationDate = LocalDate.now().plus(defaultDays, ChronoUnit.DAYS);
        this.expirationSource = ExpirationSource.fromProductType(this.productType);
        recalculateExpired();
    }

    private void recalculateExpired() {
        if (expirationDate != null) {
            this.isExpired = expirationDate.isBefore(LocalDate.now());
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ProductType getProductType() { return productType; }
    public void setProductType(ProductType productType) { this.productType = productType; }

    public Integer getEstimatedDaysRemaining() { return estimatedDaysRemaining; }
    public void setEstimatedDaysRemaining(Integer estimatedDaysRemaining) { this.estimatedDaysRemaining = estimatedDaysRemaining; }

    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }

    public ExpirationSource getExpirationSource() { return expirationSource; }
    public void setExpirationSource(ExpirationSource expirationSource) { this.expirationSource = expirationSource; }

    public Boolean getIsExpired() { return isExpired; }
    public void setIsExpired(Boolean isExpired) { this.isExpired = isExpired; }

    public String getReceiptId() { return receiptId; }
    public void setReceiptId(String receiptId) { this.receiptId = receiptId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public enum ProductType {
        PACKAGED, ORGANIC
    }

    public enum ExpirationSource {
        CONSERVATIVE_AVERAGE,
        AI_ESTIMATE,
        USER_OVERRIDE;

        static ExpirationSource fromProductType(ProductType type) {
            return switch (type) {
                case PACKAGED -> CONSERVATIVE_AVERAGE;
                case ORGANIC -> AI_ESTIMATE;
            };
        }
    }
}
