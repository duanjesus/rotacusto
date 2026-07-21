import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

import 'package:rotacusto_app/domain/models/traffic_report.dart';
import 'package:rotacusto_app/domain/models/traffic_severity.dart';
import 'package:rotacusto_app/domain/navigation/traffic_report_proximity.dart';

TrafficReport _relato(
    {int id = 1, required double lat, required double lng, TrafficSeverity severidade = TrafficSeverity.leve}) {
  final agora = DateTime(2026, 7, 16);
  return TrafficReport(
      id: id, severidade: severidade, lat: lat, lng: lng, criadoEm: agora, expiraEm: agora.add(const Duration(minutes: 15)));
}

void main() {
  test('não anuncia nada quando não há relato perto', () {
    final checker = TrafficReportProximityChecker(avisoDistanciaM: 300);
    final longe = _relato(lat: -23.0, lng: -44.0);

    final resultado = checker.checkProximity(const LatLng(-22.9, -43.1), [longe]);

    expect(resultado, isNull);
  });

  test('anuncia o relato dentro do raio de aviso', () {
    final checker = TrafficReportProximityChecker(avisoDistanciaM: 300);
    // ~100m ao norte da posição atual.
    final perto = _relato(lat: -22.9, lng: -43.1);

    final resultado = checker.checkProximity(const LatLng(-22.899, -43.1), [perto]);

    expect(resultado, isNotNull);
    expect(resultado!.id, perto.id);
  });

  test('não anuncia o mesmo relato duas vezes', () {
    final checker = TrafficReportProximityChecker(avisoDistanciaM: 300);
    final perto = _relato(lat: -22.9, lng: -43.1);
    const posicao = LatLng(-22.899, -43.1);

    final primeiro = checker.checkProximity(posicao, [perto]);
    final segundo = checker.checkProximity(posicao, [perto]);

    expect(primeiro, isNotNull);
    expect(segundo, isNull);
  });

  test('anuncia o relato mais próximo quando há mais de um dentro do raio', () {
    final checker = TrafficReportProximityChecker(avisoDistanciaM: 2000);
    final maisPerto = _relato(id: 1, lat: -22.9005, lng: -43.1);
    final maisLonge = _relato(id: 2, lat: -22.910, lng: -43.1);

    final resultado = checker.checkProximity(const LatLng(-22.9, -43.1), [maisLonge, maisPerto]);

    expect(resultado!.id, 1);
  });

  test('reset() permite anunciar de novo um relato já anunciado', () {
    final checker = TrafficReportProximityChecker(avisoDistanciaM: 300);
    final perto = _relato(lat: -22.9, lng: -43.1);
    const posicao = LatLng(-22.899, -43.1);

    checker.checkProximity(posicao, [perto]);
    checker.reset();
    final resultado = checker.checkProximity(posicao, [perto]);

    expect(resultado, isNotNull);
  });
}
