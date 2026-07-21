import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

import 'package:rotacusto_app/domain/models/radar_point.dart';
import 'package:rotacusto_app/domain/models/radar_type.dart';
import 'package:rotacusto_app/domain/navigation/radar_proximity.dart';

RadarPoint _radar({RadarType tipo = RadarType.velocidade, required double lat, required double lon}) {
  return RadarPoint(tipo: tipo, lat: lat, lon: lon);
}

void main() {
  test('não anuncia nada quando não há radar perto', () {
    final checker = RadarProximityChecker(avisoDistanciaM: 500);
    final longe = _radar(lat: -23.0, lon: -44.0);

    final resultado = checker.checkProximity(const LatLng(-22.9, -43.1), [longe]);

    expect(resultado, isNull);
  });

  test('anuncia o radar dentro do raio de aviso', () {
    final checker = RadarProximityChecker(avisoDistanciaM: 500);
    // ~100m ao norte da posição atual.
    final perto = _radar(lat: -22.9, lon: -43.1);

    final resultado = checker.checkProximity(const LatLng(-22.899, -43.1), [perto]);

    expect(resultado, isNotNull);
    expect(resultado!.lat, perto.lat);
  });

  test('não anuncia o mesmo radar duas vezes', () {
    final checker = RadarProximityChecker(avisoDistanciaM: 500);
    final perto = _radar(lat: -22.9, lon: -43.1);
    const posicao = LatLng(-22.899, -43.1);

    final primeiro = checker.checkProximity(posicao, [perto]);
    final segundo = checker.checkProximity(posicao, [perto]);

    expect(primeiro, isNotNull);
    expect(segundo, isNull);
  });

  test('anuncia o radar mais próximo quando há mais de um dentro do raio', () {
    final checker = RadarProximityChecker(avisoDistanciaM: 2000);
    final maisPerto = _radar(lat: -22.9005, lon: -43.1);
    final maisLonge = _radar(lat: -22.910, lon: -43.1);

    final resultado = checker.checkProximity(const LatLng(-22.9, -43.1), [maisLonge, maisPerto]);

    expect(resultado!.lat, -22.9005);
  });

  test('reset() permite anunciar de novo um radar já anunciado', () {
    final checker = RadarProximityChecker(avisoDistanciaM: 500);
    final perto = _radar(lat: -22.9, lon: -43.1);
    const posicao = LatLng(-22.899, -43.1);

    checker.checkProximity(posicao, [perto]);
    checker.reset();
    final resultado = checker.checkProximity(posicao, [perto]);

    expect(resultado, isNotNull);
  });

  test('detecta radar de avanço de sinal normalmente — checker é agnóstico ao tipo', () {
    final checker = RadarProximityChecker(avisoDistanciaM: 500);
    final perto = _radar(tipo: RadarType.avancoSinal, lat: -22.9, lon: -43.1);

    final resultado = checker.checkProximity(const LatLng(-22.899, -43.1), [perto]);

    expect(resultado, isNotNull);
    expect(resultado!.tipo, RadarType.avancoSinal);
  });
}
