package com.despensia.product.domain;

import com.despensia.product.domain.ProductItem.ProductType;

public interface DefaultExpirationStrategy {
    Integer defaultFor(ProductType type);
}
