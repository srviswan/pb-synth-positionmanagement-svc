package com.bank.esps.infrastructure.persistence.repository.state;

import com.bank.esps.infrastructure.persistence.entity.state.SmPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmPositionRepository extends JpaRepository<SmPositionEntity, String> {
}
