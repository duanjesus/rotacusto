import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rotacusto_app/domain/models/vehicle_model_summary.dart';
import 'package:rotacusto_app/domain/models/vehicle_type.dart';
import 'package:rotacusto_app/presentation/widgets/vehicle_search_field.dart';

VehicleModelSummary _vehicle(String marca, String modelo) {
  return VehicleModelSummary(marca: marca, modelo: modelo);
}

void main() {
  Widget wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

  testWidgets('does not fetch suggestions for queries shorter than 2 chars', (tester) async {
    var callCount = 0;

    await tester.pumpWidget(wrap(VehicleSearchField(
      fetchSuggestions: (q) async {
        callCount++;
        return [];
      },
      onSelected: (_) {},
      tipo: VehicleType.carro,
      onReportMissingVehicle: (_, _) async {},
    )));

    await tester.enterText(find.byType(TextField), 'C');
    await tester.pump(const Duration(milliseconds: 500));

    expect(callCount, 0);
  });

  testWidgets('shows vehicle suggestions after debounce and lets user pick one', (tester) async {
    VehicleModelSummary? selected;

    await tester.pumpWidget(wrap(VehicleSearchField(
      fetchSuggestions: (q) async => [
        _vehicle('Toyota', 'Corolla'),
        _vehicle('Toyota', 'Corolla Cross'),
      ],
      onSelected: (v) => selected = v,
      tipo: VehicleType.carro,
      onReportMissingVehicle: (_, _) async {},
    )));

    await tester.enterText(find.byType(TextField), 'Corolla');
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();

    expect(find.text('Toyota Corolla'), findsOneWidget);
    expect(find.text('Toyota Corolla Cross'), findsOneWidget);

    await tester.tap(find.text('Toyota Corolla'));
    await tester.pump();

    expect(selected, isNotNull);
    expect(selected!.modelo, 'Corolla');
    expect(find.text('Toyota Corolla Cross'), findsNothing);
  });

  testWidgets('pre-fills the field with the initial value', (tester) async {
    await tester.pumpWidget(wrap(VehicleSearchField(
      initialValue: _vehicle('Honda', 'Civic'),
      fetchSuggestions: (q) async => [],
      onSelected: (_) {},
      tipo: VehicleType.carro,
      onReportMissingVehicle: (_, _) async {},
    )));

    expect(find.text('Honda Civic'), findsOneWidget);
  });

  testWidgets('shows the missing-vehicle report link', (tester) async {
    await tester.pumpWidget(wrap(VehicleSearchField(
      fetchSuggestions: (q) async => [],
      onSelected: (_) {},
      tipo: VehicleType.caminhao,
      onReportMissingVehicle: (_, _) async {},
    )));

    expect(find.text('Não achou seu veículo? Relate aqui'), findsOneWidget);
  });

  testWidgets('tapping the report link opens a dialog that submits tipo+descrição on send', (tester) async {
    VehicleType? tipoRecebido;
    String? descricaoRecebida;

    await tester.pumpWidget(wrap(VehicleSearchField(
      fetchSuggestions: (q) async => [],
      onSelected: (_) {},
      tipo: VehicleType.moto,
      onReportMissingVehicle: (tipo, descricao) async {
        tipoRecebido = tipo;
        descricaoRecebida = descricao;
      },
    )));

    await tester.tap(find.text('Não achou seu veículo? Relate aqui'));
    await tester.pumpAndSettle();

    expect(find.text('Veículo não encontrado'), findsOneWidget);

    await tester.enterText(find.byType(TextField).last, 'Honda Elite 125 2018');
    await tester.tap(find.text('Enviar'));
    await tester.pumpAndSettle();

    expect(tipoRecebido, VehicleType.moto);
    expect(descricaoRecebida, 'Honda Elite 125 2018');
  });

  testWidgets('cancelling the dialog does not call onReportMissingVehicle', (tester) async {
    var called = false;

    await tester.pumpWidget(wrap(VehicleSearchField(
      fetchSuggestions: (q) async => [],
      onSelected: (_) {},
      tipo: VehicleType.carro,
      onReportMissingVehicle: (_, _) async => called = true,
    )));

    await tester.tap(find.text('Não achou seu veículo? Relate aqui'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();

    expect(called, isFalse);
  });
}
