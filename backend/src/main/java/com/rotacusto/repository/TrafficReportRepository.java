package com.rotacusto.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rotacusto.entity.TrafficReport;

public interface TrafficReportRepository extends JpaRepository<TrafficReport, Long> {

    List<TrafficReport> findByExpiraEmAfter(Instant agora);

    @Modifying
    @Query("DELETE FROM TrafficReport t WHERE t.expiraEm < :agora")
    void deleteByExpiraEmBefore(@Param("agora") Instant agora);
}
