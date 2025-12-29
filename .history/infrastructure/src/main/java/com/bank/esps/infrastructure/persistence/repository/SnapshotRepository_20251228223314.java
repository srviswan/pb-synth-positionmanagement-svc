package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SnapshotRepository extends JpaRepository<SnapshotEntity, Long> {
    
    Optional<SnapshotEntity> findByPositionKey(String positionKey);
    
    org.springframework.data.domain.Page<SnapshotEntity> findByAccount(String account, org.springframework.data.domain.Pageable pageable);
    
    org.springframework.data.domain.Page<SnapshotEntity> findByInstrument(String instrument, org.springframework.data.domain.Pageable pageable);
    
    org.springframework.data.domain.Page<SnapshotEntity> findByAccountAndInstrument(String account, String instrument, org.springframework.data.domain.Pageable pageable);
    
    org.springframework.data.domain.Page<SnapshotEntity> findByContractId(String contractId, org.springframework.data.domain.Pageable pageable);
}
