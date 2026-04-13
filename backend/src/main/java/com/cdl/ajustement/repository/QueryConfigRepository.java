package com.cdl.ajustement.repository;

import com.cdl.ajustement.entity.QueryConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QueryConfigRepository extends JpaRepository<QueryConfig, Long> {
    Optional<QueryConfig> findByConfigName(String configName);
}
