package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SnapshotRepository extends JpaRepository<SnapshotEntity, Long> {
    
    Optional<SnapshotEntity> findByPositionKey(String positionKey);
}
