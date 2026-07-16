import 'package:latlong2/latlong.dart';

import 'fuel_station.dart';
import 'route_step.dart';
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
  final List<FuelStation> postosNaRota;
  final FuelStation? postoSugerido;
  final List<RouteStep> passosRota;
  final List<LatLng> paradasNaRota;

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
    required this.postosNaRota,
    required this.postoSugerido,
    required this.passosRota,
    required this.paradasNaRota,
  });

  /// Combina ida + volta calculadas separadamente (não é só multiplicar por
  /// 2: a volta pode ter pedágios diferentes da ida — praças de sentido
  /// único, tarifa por eixo variando com o sentido, rota escolhida pelo
  /// roteador podendo diferir — cada perna é uma consulta real ao back-end).
  factory TripCostBreakdown.combine(TripCostBreakdown ida, TripCostBreakdown volta) {
    return TripCostBreakdown(
      distanciaKm: ida.distanciaKm + volta.distanciaKm,
      duracaoMin: ida.duracaoMin + volta.duracaoMin,
      custoCombustivel: ida.custoCombustivel + volta.custoCombustivel,
      custoDesgaste: ida.custoDesgaste + volta.custoDesgaste,
      custoPedagio: ida.custoPedagio + volta.custoPedagio,
      custoLanche: ida.custoLanche + volta.custoLanche,
      total: ida.total + volta.total,
      geometriaRota: [...ida.geometriaRota, ...volta.geometriaRota],
      pedagiosNaRota: [...ida.pedagiosNaRota, ...volta.pedagiosNaRota],
      postosNaRota: [...ida.postosNaRota, ...volta.postosNaRota],
      postoSugerido: ida.postoSugerido ?? volta.postoSugerido,
      // way_points da volta apontam pra índices dentro DA GEOMETRIA DELA —
      // precisam do deslocamento pelo tamanho da geometria da ida pra
      // continuarem válidos contra geometriaRota já concatenada acima.
      passosRota: [
        ...ida.passosRota,
        ...volta.passosRota.map((p) => p.offsetWayPoints(ida.geometriaRota.length)),
      ],
      // Paradas não têm índice de way-point pra deslocar (diferente de
      // passosRota) — só coordenadas, concatenação simples já basta.
      paradasNaRota: [...ida.paradasNaRota, ...volta.paradasNaRota],
    );
  }

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
      postosNaRota: (json['postosNaRota'] as List<dynamic>)
          .map((p) => FuelStation.fromJson(p as Map<String, dynamic>))
          .toList(),
      postoSugerido: json['postoSugerido'] != null
          ? FuelStation.fromJson(json['postoSugerido'] as Map<String, dynamic>)
          : null,
      passosRota: (json['passosRota'] as List<dynamic>? ?? [])
          .map((p) => RouteStep.fromJson(p as Map<String, dynamic>))
          .toList(),
      paradasNaRota: (json['paradasNaRota'] as List<dynamic>? ?? [])
          .map((p) => LatLng((p['lat'] as num).toDouble(), (p['lon'] as num).toDouble()))
          .toList(),
    );
  }
}
