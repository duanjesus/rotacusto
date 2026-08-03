/// Restaurante real perto de uma parada pra lanche calculada (Fase 13) —
/// infraestrutura via OpenStreetMap, ao contrário de [RoadAlert]/[TrafficReport]:
/// sem id próprio do back-end, sem expiração, sem voto.
class Restaurant {
  final String nome;
  final double lat;
  final double lon;
  final double distanciaKm;

  Restaurant({required this.nome, required this.lat, required this.lon, required this.distanciaKm});

  factory Restaurant.fromJson(Map<String, dynamic> json) {
    return Restaurant(
      nome: json['nome'] as String,
      lat: (json['lat'] as num).toDouble(),
      lon: (json['lon'] as num).toDouble(),
      distanciaKm: (json['distanciaKm'] as num).toDouble(),
    );
  }

  Map<String, dynamic> toJson() => {'nome': nome, 'lat': lat, 'lon': lon, 'distanciaKm': distanciaKm};
}
