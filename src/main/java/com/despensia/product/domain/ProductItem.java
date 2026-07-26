package com.despensia.product.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "product_item")
@Getter
@Setter
@NoArgsConstructor
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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
