package com.despensia.scan.service;

import com.despensia.product.domain.ProductItem;
import com.despensia.scan.domain.InventoryScan;

/**
 * Port for scanning product images (visual analysis).
 *
 * Implementations read a product photo and extract product information:
 * name, type, estimated expiration, and other attributes.
 */
public interface ProductScanningPort {

    /**
     * Scan a product image and return the detected product information.
     *
     * @param scan the inventory scan record to associate results with
     * @return the detected product item
     */
    ProductItem scan(InventoryScan scan);
}
