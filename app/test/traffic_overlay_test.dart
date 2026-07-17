import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

import 'package:rotacusto_app/domain/models/traffic_report.dart';
import 'package:rotacusto_app/domain/models/traffic_severity.dart';
import 'package:rotacusto_app/domain/navigation/traffic_overlay.dart';

TrafficReport _reportAt(double lat, double lng, {int id = 1, TrafficSeverity severidade = TrafficSeverity.intenso}) {
  final agora = DateTime(2026, 7, 16);
  return TrafficReport(
    id: id,
    severidade: severidade,
    lat: lat,
    lng: lng,
    criadoEm: agora,
    expiraEm: agora.add(const Duration(minutes: 15)),
  );
}

void main() {
  // Reta ao longo do meridiano, ~111m entre pontos consecutivos (0,001 grau
  // de latitude ≈ 111m em qualquer longitude) — dá pra prever exatamente
  // quantos pontos uma janela de raioM deve cobrir.
  final geometriaRota = List.generate(11, (i) => LatLng(-22.900 - i * 0.001, -43.10));

  test('geometria vazia não gera segmento nenhum', () {
    final segmentos = buildTrafficSegments([], [_reportAt(-22.905, -43.10)]);
    expect(segmentos, isEmpty);
  });

  test('sem relatos não gera segmento nenhum', () {
    final segmentos = buildTrafficSegments(geometriaRota, []);
    expect(segmentos, isEmpty);
  });

  test('janela de raioM cobre os pontos certos ao redor do ponto mais próximo', () {
    final relatorio = _reportAt(-22.905, -43.10, severidade: TrafficSeverity.medio);

    final segmentos = buildTrafficSegments(geometriaRota, [relatorio], raioM: 150);

    expect(segmentos, hasLength(1));
    expect(segmentos.first.severidade, TrafficSeverity.medio);
    // índice 5 é o ponto mais próximo (-22.905); ~150m pra cada lado cobre
    // dois passos de 111m, então índices 3 a 7.
    expect(segmentos.first.pontos, [
      geometriaRota[3],
      geometriaRota[4],
      geometriaRota[5],
      geometriaRota[6],
      geometriaRota[7],
    ]);
  });

  test('garante pelo menos 2 pontos mesmo com raioM zero', () {
    final duasPontas = [geometriaRota[0], geometriaRota[1]];
    final relatorio = _reportAt(geometriaRota[0].latitude, geometriaRota[0].longitude);

    final segmentos = buildTrafficSegments(duasPontas, [relatorio], raioM: 0);

    expect(segmentos, hasLength(1));
    expect(segmentos.first.pontos.length, greaterThanOrEqualTo(2));
  });

  test('múltiplos relatos geram um segmento cada', () {
    final relatorios = [
      _reportAt(-22.901, -43.10, id: 1, severidade: TrafficSeverity.leve),
      _reportAt(-22.909, -43.10, id: 2, severidade: TrafficSeverity.intenso),
    ];

    final segmentos = buildTrafficSegments(geometriaRota, relatorios, raioM: 100);

    expect(segmentos, hasLength(2));
    expect(segmentos.map((s) => s.severidade), containsAll([TrafficSeverity.leve, TrafficSeverity.intenso]));
  });
}
