package com.rotacusto.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rotacusto.entity.VehicleReport;

public interface VehicleReportRepository extends JpaRepository<VehicleReport, Long> {
}
