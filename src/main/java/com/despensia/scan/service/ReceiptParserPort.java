package com.despensia.scan.service;

import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.domain.Receipt;

/**
 * Port for parsing purchase receipts (OCR).
 *
 * Implementations read an image of a receipt and extract structured data:
 * store name, date, total amount, and line items.
 */
public interface ReceiptParserPort {

    /**
     * Parse a receipt image and return the extracted receipt data.
     *
     * @param scan the inventory scan record to associate results with
     * @return the parsed receipt with line items
     */
    Receipt parse(InventoryScan scan);
}
