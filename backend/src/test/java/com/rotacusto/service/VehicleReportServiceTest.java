package com.rotacusto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.rotacusto.dto.request.VehicleReportRequestDTO;
import com.rotacusto.entity.VehicleReport;
import com.rotacusto.entity.enums.VehicleType;
import com.rotacusto.repository.VehicleReportRepository;

class VehicleReportServiceTest {

    @Test
    void savesTheReportWithTypeDescriptionAndTimestamp() {
        VehicleReportRepository repository = mock(VehicleReportRepository.class);
        VehicleReportService service = new VehicleReportService(repository);
        Instant antes = Instant.now();

        service.report(new VehicleReportRequestDTO(VehicleType.CAMINHAO, "Volvo FH 460 2022"));

        ArgumentCaptor<VehicleReport> captor = ArgumentCaptor.forClass(VehicleReport.class);
        verify(repository).save(captor.capture());
        VehicleReport salvo = captor.getValue();
        assertThat(salvo.getTipo()).isEqualTo(VehicleType.CAMINHAO);
        assertThat(salvo.getDescricao()).isEqualTo("Volvo FH 460 2022");
        assertThat(salvo.getCriadoEm()).isBetween(antes, Instant.now());
    }
}
