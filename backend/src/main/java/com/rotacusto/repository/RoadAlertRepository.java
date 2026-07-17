package com.rotacusto.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rotacusto.entity.RoadAlert;

public interface RoadAlertRepository extends JpaRepository<RoadAlert, Long> {

    List<RoadAlert> findByExpiraEmAfter(Instant agora);

    @Modifying
    @Query("DELETE FROM RoadAlert r WHERE r.expiraEm < :agora")
    void deleteByExpiraEmBefore(@Param("agora") Instant agora);
}
