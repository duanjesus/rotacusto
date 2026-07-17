import 'package:flutter_test/flutter_test.dart';

import 'package:rotacusto_app/domain/models/traffic_severity.dart';
import 'package:rotacusto_app/domain/navigation/traffic_detector.dart';

void main() {
  final t0 = DateTime(2026, 7, 16, 12, 0, 0);

  test('velocidade normal nunca dispara', () {
    final detector = TrafficDetector();

    for (var i = 0; i < 5; i++) {
      final resultado = detector.registrarLeitura(
        velocidadeAtualMps: 18,
        velocidadeEsperadaMps: 20,
        agora: t0.add(Duration(seconds: i * 5)),
      );
      expect(resultado, isNull);
    }
  });

  test('1 leitura isolada abaixo do limiar não dispara', () {
    final detector = TrafficDetector();

    final resultado = detector.registrarLeitura(
      velocidadeAtualMps: 2,
      velocidadeEsperadaMps: 20,
      agora: t0,
    );

    expect(resultado, isNull);
  });

  test('3 leituras consecutivas com razão ~0.6 disparam LEVE', () {
    final detector = TrafficDetector();

    TrafficSeverity? ultimo;
    for (var i = 0; i < 3; i++) {
      ultimo = detector.registrarLeitura(
        velocidadeAtualMps: 12,
        velocidadeEsperadaMps: 20,
        agora: t0.add(Duration(seconds: i * 5)),
      );
    }

    expect(ultimo, TrafficSeverity.leve);
  });

  test('3 leituras consecutivas com razão ~0.2 disparam INTENSO', () {
    final detector = TrafficDetector();

    TrafficSeverity? ultimo;
    for (var i = 0; i < 3; i++) {
      ultimo = detector.registrarLeitura(
        velocidadeAtualMps: 4,
        velocidadeEsperadaMps: 20,
        agora: t0.add(Duration(seconds: i * 5)),
      );
    }

    expect(ultimo, TrafficSeverity.intenso);
  });

  test('cooldown impede um segundo relato logo em seguida', () {
    final detector = TrafficDetector(cooldown: const Duration(seconds: 90));

    for (var i = 0; i < 3; i++) {
      detector.registrarLeitura(
        velocidadeAtualMps: 4,
        velocidadeEsperadaMps: 20,
        agora: t0.add(Duration(seconds: i * 5)),
      );
    }
    final logoDepois = detector.registrarLeitura(
      velocidadeAtualMps: 4,
      velocidadeEsperadaMps: 20,
      agora: t0.add(const Duration(seconds: 30)),
    );
    final depoisDoCooldown = detector.registrarLeitura(
      velocidadeAtualMps: 4,
      velocidadeEsperadaMps: 20,
      agora: t0.add(const Duration(seconds: 120)),
    );

    expect(logoDepois, isNull);
    expect(depoisDoCooldown, TrafficSeverity.intenso);
  });

  test('voltar à velocidade normal e cair de novo reacumula do zero', () {
    final detector = TrafficDetector();

    detector.registrarLeitura(velocidadeAtualMps: 4, velocidadeEsperadaMps: 20, agora: t0);
    detector.registrarLeitura(
        velocidadeAtualMps: 4, velocidadeEsperadaMps: 20, agora: t0.add(const Duration(seconds: 5)));
    // Volta ao normal antes de completar as 3 leituras — zera o contador.
    detector.registrarLeitura(
        velocidadeAtualMps: 19, velocidadeEsperadaMps: 20, agora: t0.add(const Duration(seconds: 10)));

    final terceira = detector.registrarLeitura(
        velocidadeAtualMps: 4, velocidadeEsperadaMps: 20, agora: t0.add(const Duration(seconds: 15)));

    expect(terceira, isNull, reason: 'só a primeira leitura lenta depois do reset, ainda não completou 3');
  });

  test('velocidade esperada zero ou negativa não classifica', () {
    final detector = TrafficDetector();

    final resultado = detector.registrarLeitura(
      velocidadeAtualMps: 4,
      velocidadeEsperadaMps: 0,
      agora: t0,
    );

    expect(resultado, isNull);
  });

  test('reset() permite disparar de novo imediatamente', () {
    final detector = TrafficDetector();

    for (var i = 0; i < 3; i++) {
      detector.registrarLeitura(
        velocidadeAtualMps: 4,
        velocidadeEsperadaMps: 20,
        agora: t0.add(Duration(seconds: i * 5)),
      );
    }
    detector.reset();

    TrafficSeverity? ultimo;
    for (var i = 0; i < 3; i++) {
      ultimo = detector.registrarLeitura(
        velocidadeAtualMps: 4,
        velocidadeEsperadaMps: 20,
        agora: t0.add(Duration(seconds: 100 + i * 5)),
      );
    }

    expect(ultimo, TrafficSeverity.intenso);
  });
}
