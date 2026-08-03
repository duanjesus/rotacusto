import 'restaurant.dart';

/// Um ponto de parada pra lanche calculado (Fase 13, ver
/// `FoodStopCostCalculator.numeroDeParadas` no back-end) + os restaurantes
/// reais mais próximos dele, já ordenados por distância — o usuário escolhe.
class FoodStopSuggestion {
  final double lat;
  final double lon;
  final List<Restaurant> restaurantes;

  FoodStopSuggestion({required this.lat, required this.lon, required this.restaurantes});

  factory FoodStopSuggestion.fromJson(Map<String, dynamic> json) {
    return FoodStopSuggestion(
      lat: (json['lat'] as num).toDouble(),
      lon: (json['lon'] as num).toDouble(),
      restaurantes: (json['restaurantes'] as List<dynamic>)
          .map((r) => Restaurant.fromJson(r as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toJson() =>
      {'lat': lat, 'lon': lon, 'restaurantes': restaurantes.map((r) => r.toJson()).toList()};
}
