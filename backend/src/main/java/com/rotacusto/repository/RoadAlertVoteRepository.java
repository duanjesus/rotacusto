package com.rotacusto.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rotacusto.entity.RoadAlertVote;

public interface RoadAlertVoteRepository extends JpaRepository<RoadAlertVote, Long> {

    boolean existsByRoadAlertIdAndDeviceId(Long roadAlertId, String deviceId);

    long countByRoadAlertIdAndConfirmou(Long roadAlertId, boolean confirmou);

    /**
     * Precisa apagar os votos de um alerta ANTES de apagar o alerta em
     * {@code RoadAlertService.limparExpirados()} — um DELETE em lote via JPQL não
     * segue cascade de anotação JPA, só respeitaria uma FK de verdade no banco
     * (ON DELETE CASCADE), que não configuramos por simplicidade.
     */
    @Modifying
    @Query("DELETE FROM RoadAlertVote v WHERE v.roadAlert.expiraEm < :agora")
    void deleteByRoadAlertExpiraEmBefore(@Param("agora") Instant agora);
}
