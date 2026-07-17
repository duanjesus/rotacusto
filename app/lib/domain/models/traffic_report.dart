import 'traffic_severity.dart';

class TrafficReport {
  final int id;
  final TrafficSeverity severidade;
  final double lat;
  final double lng;
  final DateTime criadoEm;
  final DateTime expiraEm;

  TrafficReport({
    required this.id,
    required this.severidade,
    required this.lat,
    required this.lng,
    required this.criadoEm,
    required this.expiraEm,
  });

  factory TrafficReport.fromJson(Map<String, dynamic> json) {
    return TrafficReport(
      id: json['id'] as int,
      severidade: TrafficSeverity.fromApiValue(json['severidade'] as String),
      lat: (json['lat'] as num).toDouble(),
      lng: (json['lng'] as num).toDouble(),
      criadoEm: DateTime.parse(json['criadoEm'] as String),
      expiraEm: DateTime.parse(json['expiraEm'] as String),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'severidade': severidade.apiValue,
        'lat': lat,
        'lng': lng,
        'criadoEm': criadoEm.toIso8601String(),
        'expiraEm': expiraEm.toIso8601String(),
      };
}
