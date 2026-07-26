package com.despensia.scan.service;

import com.despensia.scan.domain.InventoryScan;
import com.despensia.scan.repository.ScanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final ScanRepository scanRepository;
    private final ProcessImageUseCase processImageUseCase;

    public ScanService(ScanRepository scanRepository, ProcessImageUseCase processImageUseCase) {
        this.scanRepository = scanRepository;
        this.processImageUseCase = processImageUseCase;
    }

    /**
     * Process an image scan and persist the result.
     *
     * @param scanType the type of scan (PRODUCT or RECEIPT)
     * @param imagePath path to the image file
     * @return the result from the use case
     */
    public Object scan(InventoryScan.ScanType scanType, String imagePath) {
        InventoryScan scan = new InventoryScan(scanType, imagePath);
        scan = scanRepository.save(scan);

        Object result = processImageUseCase.process(scan.getScanType(), scan.getImagePath());

        log.info("Scan completed: id={}, type={}, status={}", scan.getId(), scan.getScanType(), scan.getStatus());
        return result;
    }
}
