import 'package:latlong2/latlong.dart';

import '../models/traffic_report.dart';
import '../models/traffic_severity.dart';

/// Um trecho da rota a colorir no mapa por causa de um relato de trânsito
/// próximo (Fase 6.7).
class TrafficSegment {
  final List<LatLng> pontos;
  final TrafficSeverity severidade;

  const TrafficSegment({required this.pontos, required this.severidade});
}

/// [TrafficReport] só tem um ponto (lat/lng), não um trecho de via — pra
/// "colorir a via" sem inventar geometria de segmento nova (nem casar com
/// OSM way-id, infraestrutura que o projeto não tem), acha o ponto mais
/// próximo do relato dentro de [geometriaRota] (mesma técnica de "ponto mais
/// próximo" do `RouteProgressCalculator`) e usa uma janela de ~[raioM]
/// metros pra cada lado como o trecho a colorir. É uma aproximação visual, não
/// um recorte exato da via.
List<TrafficSegment> buildTrafficSegments(
  List<LatLng> geometriaRota,
  List<TrafficReport> relatorios, {
  double raioM = 150,
}) {
  if (geometriaRota.length < 2 || relatorios.isEmpty) return [];

  const distance = Distance();
  final segmentos = <TrafficSegment>[];

  for (final relatorio in relatorios) {
    final ponto = LatLng(relatorio.lat, relatorio.lng);

    var nearestIndex = 0;
    var nearestDist = double.infinity;
    for (var i = 0; i < geometriaRota.length; i++) {
      final d = distance(ponto, geometriaRota[i]);
      if (d < nearestDist) {
        nearestDist = d;
        nearestIndex = i;
      }
    }

    var inicio = nearestIndex;
    var acumulado = 0.0;
    while (inicio > 0 && acumulado < raioM) {
      acumulado += distance(geometriaRota[inicio - 1], geometriaRota[inicio]);
      inicio--;
    }

    var fim = nearestIndex;
    acumulado = 0.0;
    while (fim < geometriaRota.length - 1 && acumulado < raioM) {
      acumulado += distance(geometriaRota[fim], geometriaRota[fim + 1]);
      fim++;
    }

    // Garante ao menos 2 pontos pra formar um Polyline válido (pode
    // acontecer com uma janela pequena ou geometria bem esparsa).
    if (fim == inicio) {
      if (fim < geometriaRota.length - 1) {
        fim++;
      } else if (inicio > 0) {
        inicio--;
      } else {
        continue;
      }
    }

    segmentos.add(TrafficSegment(
      pontos: geometriaRota.sublist(inicio, fim + 1),
      severidade: relatorio.severidade,
    ));
  }

  return segmentos;
}
