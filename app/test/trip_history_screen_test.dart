import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

import 'package:rotacusto_app/domain/models/trip_cost_breakdown.dart';
import 'package:rotacusto_app/domain/models/trip_history_detail.dart';
import 'package:rotacusto_app/domain/models/trip_history_summary.dart';
import 'package:rotacusto_app/presentation/screens/trip_history_screen.dart';

TripCostBreakdown _breakdown() {
  return TripCostBreakdown(
    distanciaKm: 484.7,
    duracaoMin: 450,
    custoCombustivel: 200,
    custoDesgaste: 50,
    custoPedagio: 60,
    custoLanche: 25,
    total: 335,
    geometriaRota: const [LatLng(-22.9, -43.1), LatLng(-20.6, -40.4)],
    pedagiosNaRota: const [],
    postosNaRota: const [],
    postoSugerido: null,
    passosRota: const [],
    paradasNaRota: const [],
  );
}

TripHistorySummary _summary({int id = 1}) {
  return TripHistorySummary(
    id: id,
    origem: 'Copacabana, RJ',
    destino: 'Guarapari, ES',
    distanciaKm: 484.7,
    total: 335.0,
    calculadoEm: DateTime(2026, 7, 16),
  );
}

void main() {
  Widget wrap(Widget child) => MaterialApp(home: child);

  testWidgets('mostra indicador de carregamento antes da lista chegar', (tester) async {
    // Completer nunca resolvido (não Future.delayed) — sem Timer real
    // pendente, evita o "Timer is still pending" do binding de teste ao
    // encerrar o teste com a tela ainda "carregando".
    final completer = Completer<List<TripHistorySummary>>();
    await tester.pumpWidget(wrap(TripHistoryScreen(
      fetchTripHistory: () => completer.future,
      fetchTripHistoryDetail: (_) async => throw UnimplementedError(),
    )));

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('mostra mensagem quando não há viagens salvas', (tester) async {
    await tester.pumpWidget(wrap(TripHistoryScreen(
      fetchTripHistory: () async => [],
      fetchTripHistoryDetail: (_) async => throw UnimplementedError(),
    )));
    await tester.pumpAndSettle();

    expect(find.textContaining('Nenhuma viagem salva'), findsOneWidget);
  });

  testWidgets('lista as viagens salvas com origem, destino e total', (tester) async {
    await tester.pumpWidget(wrap(TripHistoryScreen(
      fetchTripHistory: () async => [_summary()],
      fetchTripHistoryDetail: (_) async => throw UnimplementedError(),
    )));
    await tester.pumpAndSettle();

    expect(find.text('Copacabana, RJ → Guarapari, ES'), findsOneWidget);
    expect(find.text('R\$ 335.00'), findsOneWidget);
  });

  testWidgets('mostra erro quando a lista falha ao carregar', (tester) async {
    await tester.pumpWidget(wrap(TripHistoryScreen(
      fetchTripHistory: () async => throw Exception('sem conexão'),
      fetchTripHistoryDetail: (_) async => throw UnimplementedError(),
    )));
    await tester.pumpAndSettle();

    expect(find.textContaining('Não foi possível carregar o histórico'), findsOneWidget);
  });

  testWidgets('tocar numa viagem abre o detalhe com o breakdown', (tester) async {
    int? idPedido;
    await tester.pumpWidget(wrap(TripHistoryScreen(
      fetchTripHistory: () async => [_summary()],
      fetchTripHistoryDetail: (id) async {
        idPedido = id;
        return TripHistoryDetail(
          origem: 'Copacabana, RJ',
          destino: 'Guarapari, ES',
          calculadoEm: DateTime(2026, 7, 16),
          breakdown: _breakdown(),
        );
      },
    )));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Copacabana, RJ → Guarapari, ES'));
    await tester.pumpAndSettle();

    expect(idPedido, 1);
    expect(find.widgetWithText(AppBar, 'Viagem salva'), findsOneWidget);
    expect(find.text('Total: R\$ 335.00'), findsOneWidget);
  });
}
