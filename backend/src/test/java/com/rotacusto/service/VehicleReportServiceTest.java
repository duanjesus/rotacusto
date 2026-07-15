package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotacusto.dto.request.VehicleReportRequestDTO;
import com.rotacusto.entity.enums.VehicleType;

class VehicleReportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void appendsEachReportAsOneJsonLine() throws Exception {
        Path arquivo = tempDir.resolve("reports.log");
        VehicleReportService service = new VehicleReportService(new ObjectMapper(), arquivo.toString());

        service.report(new VehicleReportRequestDTO(VehicleType.CAMINHAO, "Volvo FH 460 2022"));
        service.report(new VehicleReportRequestDTO(VehicleType.MOTO, "Honda Elite 125"));

        List<String> linhas = Files.readAllLines(arquivo);
        assertEquals(2, linhas.size());
        assertTrue(linhas.get(0).contains("\"tipo\":\"CAMINHAO\""));
        assertTrue(linhas.get(0).contains("\"descricao\":\"Volvo FH 460 2022\""));
        assertTrue(linhas.get(1).contains("\"tipo\":\"MOTO\""));
    }

    @Test
    void createsTheFileOnFirstReportWhenItDoesNotExistYet() {
        Path arquivo = tempDir.resolve("reports2.log");
        VehicleReportService service = new VehicleReportService(new ObjectMapper(), arquivo.toString());

        service.report(new VehicleReportRequestDTO(VehicleType.VAN, "Renault Kangoo 2015"));

        assertTrue(Files.exists(arquivo));
    }
}
