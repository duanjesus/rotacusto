/// Câmera de velocidade (radar fixo) — infraestrutura permanente via
/// OpenStreetMap, ao contrário de [RoadAlert]/[TrafficReport]: sem id
/// próprio do back-end, sem expiração, sem voto (Fase 12).
class RadarPoint {
  final double lat;
  final double lon;

  RadarPoint({required this.lat, required this.lon});

  factory RadarPoint.fromJson(Map<String, dynamic> json) {
    return RadarPoint(
      lat: (json['lat'] as num).toDouble(),
      lon: (json['lon'] as num).toDouble(),
    );
  }

  Map<String, dynamic> toJson() => {'lat': lat, 'lon': lon};
}
