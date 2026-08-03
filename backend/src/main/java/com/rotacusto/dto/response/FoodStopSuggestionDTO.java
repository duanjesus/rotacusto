package com.rotacusto.dto.response;

import java.util.List;

public record FoodStopSuggestionDTO(double lat, double lon, List<RestaurantResponseDTO> restaurantes) {
}
