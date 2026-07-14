package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.entity.VehicleModel;
import com.rotacusto.repository.VehicleModelRepository;

@ExtendWith(MockitoExtension.class)
class VehicleModelServiceTest {

    @Mock
    private VehicleModelRepository repository;

    @Test
    void returnsEmptyListForQueriesShorterThanTwoChars() {
        VehicleModelService service = new VehicleModelService(repository);

        assertTrue(service.search("C").isEmpty());
        assertTrue(service.search("").isEmpty());
        verify(repository, never())
                .findByMarcaContainingIgnoreCaseOrModeloContainingIgnoreCase(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void searchesByMarcaOrModeloUsingTheSameQuery() {
        VehicleModelService service = new VehicleModelService(repository);
        VehicleModel corolla = new VehicleModel();
        corolla.setMarca("TOYOTA");
        corolla.setModelo("COROLLA");
        when(repository.findByMarcaContainingIgnoreCaseOrModeloContainingIgnoreCase("Corolla", "Corolla"))
                .thenReturn(List.of(corolla));

        List<VehicleModel> result = service.search("Corolla");

        assertEquals(1, result.size());
        assertEquals("COROLLA", result.get(0).getModelo());
    }

    @Test
    void capsResultsAtTwentyForDropdownUsability() {
        VehicleModelService service = new VehicleModelService(repository);
        List<VehicleModel> manyMatches = IntStream.range(0, 50)
                .mapToObj(i -> {
                    VehicleModel v = new VehicleModel();
                    v.setMarca("Marca" + i);
                    return v;
                })
                .toList();
        when(repository.findByMarcaContainingIgnoreCaseOrModeloContainingIgnoreCase("ma", "ma"))
                .thenReturn(manyMatches);

        List<VehicleModel> result = service.search("ma");

        assertEquals(20, result.size());
    }
}
