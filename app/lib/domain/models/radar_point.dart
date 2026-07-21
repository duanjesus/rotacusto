import 'radar_type.dart';

/// Radar fixo — infraestrutura permanente via OpenStreetMap, ao contrário de
/// [RoadAlert]/[TrafficReport]: sem id próprio do back-end, sem expiração, sem
/// voto (Fase 12/12.1).
class RadarPoint {
  final RadarType tipo;
  final double lat;
  final double lon;

  RadarPoint({required this.tipo, required this.lat, required this.lon});

  factory RadarPoint.fromJson(Map<String, dynamic> json) {
    return RadarPoint(
      tipo: RadarType.fromApiValue(json['tipo'] as String),
      lat: (json['lat'] as num).toDouble(),
      lon: (json['lon'] as num).toDouble(),
    );
  }

  Map<String, dynamic> toJson() => {'tipo': tipo.apiValue, 'lat': lat, 'lon': lon};
}
