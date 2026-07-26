package com.despensia.scan.repository;

import com.despensia.scan.domain.InventoryScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScanRepository extends JpaRepository<InventoryScan, UUID> {
}
