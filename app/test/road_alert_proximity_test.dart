import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

import 'package:rotacusto_app/domain/models/road_alert.dart';
import 'package:rotacusto_app/domain/models/road_alert_type.dart';
import 'package:rotacusto_app/domain/navigation/road_alert_proximity.dart';

RoadAlert _alert({int id = 1, required double lat, required double lng, RoadAlertType tipo = RoadAlertType.buraco}) {
  final agora = DateTime(2026, 7, 16);
  return RoadAlert(id: id, tipo: tipo, lat: lat, lng: lng, criadoEm: agora, expiraEm: agora.add(const Duration(hours: 2)));
}

void main() {
  test('não anuncia nada quando não há alertas perto', () {
    final checker = RoadAlertProximityChecker(avisoDistanciaM: 500);
    final longe = _alert(lat: -23.0, lng: -44.0);

    final resultado = checker.checkProximity(const LatLng(-22.9, -43.1), [longe]);

    expect(resultado, isNull);
  });

  test('anuncia o alerta dentro do raio de aviso', () {
    final checker = RoadAlertProximityChecker(avisoDistanciaM: 500);
    // ~100m ao norte da posição atual.
    final perto = _alert(lat: -22.9, lng: -43.1);

    final resultado = checker.checkProximity(const LatLng(-22.899, -43.1), [perto]);

    expect(resultado, isNotNull);
    expect(resultado!.id, perto.id);
  });

  test('não anuncia o mesmo alerta duas vezes', () {
    final checker = RoadAlertProximityChecker(avisoDistanciaM: 500);
    final perto = _alert(lat: -22.9, lng: -43.1);
    const posicao = LatLng(-22.899, -43.1);

    final primeiro = checker.checkProximity(posicao, [perto]);
    final segundo = checker.checkProximity(posicao, [perto]);

    expect(primeiro, isNotNull);
    expect(segundo, isNull);
  });

  test('anuncia o alerta mais próximo quando há mais de um dentro do raio', () {
    final checker = RoadAlertProximityChecker(avisoDistanciaM: 2000);
    final maisPerto = _alert(id: 1, lat: -22.9005, lng: -43.1);
    final maisLonge = _alert(id: 2, lat: -22.910, lng: -43.1);

    final resultado = checker.checkProximity(const LatLng(-22.9, -43.1), [maisLonge, maisPerto]);

    expect(resultado!.id, 1);
  });

  test('reset() permite anunciar de novo um alerta já anunciado', () {
    final checker = RoadAlertProximityChecker(avisoDistanciaM: 500);
    final perto = _alert(lat: -22.9, lng: -43.1);
    const posicao = LatLng(-22.899, -43.1);

    checker.checkProximity(posicao, [perto]);
    checker.reset();
    final resultado = checker.checkProximity(posicao, [perto]);

    expect(resultado, isNotNull);
  });
}
