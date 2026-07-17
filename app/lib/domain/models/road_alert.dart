import 'road_alert_type.dart';

class RoadAlert {
  final int id;
  final RoadAlertType tipo;
  final double lat;
  final double lng;
  final DateTime criadoEm;
  final DateTime expiraEm;

  RoadAlert({
    required this.id,
    required this.tipo,
    required this.lat,
    required this.lng,
    required this.criadoEm,
    required this.expiraEm,
  });

  factory RoadAlert.fromJson(Map<String, dynamic> json) {
    return RoadAlert(
      id: json['id'] as int,
      tipo: RoadAlertType.fromApiValue(json['tipo'] as String),
      lat: (json['lat'] as num).toDouble(),
      lng: (json['lng'] as num).toDouble(),
      criadoEm: DateTime.parse(json['criadoEm'] as String),
      expiraEm: DateTime.parse(json['expiraEm'] as String),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'tipo': tipo.apiValue,
        'lat': lat,
        'lng': lng,
        'criadoEm': criadoEm.toIso8601String(),
        'expiraEm': expiraEm.toIso8601String(),
      };
}
