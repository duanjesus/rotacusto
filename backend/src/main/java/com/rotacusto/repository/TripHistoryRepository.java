package com.rotacusto.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rotacusto.entity.TripHistoryEntry;
import com.rotacusto.entity.User;

public interface TripHistoryRepository extends JpaRepository<TripHistoryEntry, Long> {

    List<TripHistoryEntry> findByUsuarioOrderByCalculadoEmDesc(User usuario);

    Optional<TripHistoryEntry> findByIdAndUsuario(Long id, User usuario);
}
