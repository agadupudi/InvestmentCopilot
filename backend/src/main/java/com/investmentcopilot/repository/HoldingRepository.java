package com.investmentcopilot.repository;

import com.investmentcopilot.model.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Integer> {
    List<Holding> findAllByOrderBySymbolAsc();
}
