package com.rotacusto.dto.response;

import com.rotacusto.entity.enums.RadarType;

public record RadarResponseDTO(RadarType tipo, double lat, double lon) {
}
