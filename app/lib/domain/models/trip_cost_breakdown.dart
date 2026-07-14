import 'package:latlong2/latlong.dart';

import 'toll_plaza.dart';

class TripCostBreakdown {
  final double distanciaKm;
  final double duracaoMin;
  final double custoCombustivel;
  final double custoDesgaste;
  final double custoPedagio;
  final double custoLanche;
  final double total;
  final List<LatLng> geometriaRota;
  final List<TollPlaza> pedagiosNaRota;

  TripCostBreakdown({
    required this.distanciaKm,
    required this.duracaoMin,
    required this.custoCombustivel,
    required this.custoDesgaste,
    required this.custoPedagio,
    required this.custoLanche,
    required this.total,
    required this.geometriaRota,
    required this.pedagiosNaRota,
  });

  factory TripCostBreakdown.fromJson(Map<String, dynamic> json) {
    return TripCostBreakdown(
      distanciaKm: (json['distanciaKm'] as num).toDouble(),
      duracaoMin: (json['duracaoMin'] as num).toDouble(),
      custoCombustivel: (json['custoCombustivel'] as num).toDouble(),
      custoDesgaste: (json['custoDesgaste'] as num).toDouble(),
      custoPedagio: (json['custoPedagio'] as num).toDouble(),
      custoLanche: (json['custoLanche'] as num).toDouble(),
      total: (json['total'] as num).toDouble(),
      geometriaRota: (json['geometriaRota'] as List<dynamic>)
          .map((p) => LatLng((p['lat'] as num).toDouble(), (p['lon'] as num).toDouble()))
          .toList(),
      pedagiosNaRota: (json['pedagiosNaRota'] as List<dynamic>)
          .map((p) => TollPlaza.fromJson(p as Map<String, dynamic>))
          .toList(),
    );
  }
}
