package com.despensia.product.repository;

import com.despensia.product.domain.ProductItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductItemRepository extends JpaRepository<ProductItem, UUID> {

    Optional<ProductItem> findByName(String name);

    Optional<ProductItem> findByReceiptId(String receiptId);
}
