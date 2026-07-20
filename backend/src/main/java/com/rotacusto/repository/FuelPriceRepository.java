package com.rotacusto.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rotacusto.entity.FuelPrice;

public interface FuelPriceRepository extends JpaRepository<FuelPrice, Long> {
}
