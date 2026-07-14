package com.rotacusto.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.VehicleType;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {

    List<VehicleModel> findByMarcaContainingIgnoreCase(String marca);

    List<VehicleModel> findByTipo(VehicleType tipo);

    List<VehicleModel> findByMarcaContainingIgnoreCaseAndTipo(String marca, VehicleType tipo);

    List<VehicleModel> findByMarcaContainingIgnoreCaseOrModeloContainingIgnoreCase(String marca, String modelo);
}
