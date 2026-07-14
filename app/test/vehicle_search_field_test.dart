import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rotacusto_app/domain/models/vehicle_model.dart';
import 'package:rotacusto_app/domain/models/vehicle_type.dart';
import 'package:rotacusto_app/presentation/widgets/vehicle_search_field.dart';

VehicleModel _vehicle(String marca, String modelo, int ano) {
  return VehicleModel(
    id: 1,
    marca: marca,
    modelo: modelo,
    ano: ano,
    tipo: VehicleType.carro,
    consumoCidadeKmL: 10,
    consumoEstradaKmL: 12,
    numeroEixos: 2,
    custoDesgastePorKm: 0.4,
  );
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
    )));

    await tester.enterText(find.byType(TextField), 'C');
    await tester.pump(const Duration(milliseconds: 500));

    expect(callCount, 0);
  });

  testWidgets('shows vehicle suggestions after debounce and lets user pick one', (tester) async {
    VehicleModel? selected;

    await tester.pumpWidget(wrap(VehicleSearchField(
      fetchSuggestions: (q) async => [
        _vehicle('Toyota', 'Corolla', 2023),
        _vehicle('Toyota', 'Corolla Cross', 2024),
      ],
      onSelected: (v) => selected = v,
    )));

    await tester.enterText(find.byType(TextField), 'Corolla');
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();

    expect(find.text('Toyota Corolla (2023)'), findsOneWidget);
    expect(find.text('Toyota Corolla Cross (2024)'), findsOneWidget);

    await tester.tap(find.text('Toyota Corolla (2023)'));
    await tester.pump();

    expect(selected, isNotNull);
    expect(selected!.modelo, 'Corolla');
    expect(find.text('Toyota Corolla Cross (2024)'), findsNothing);
  });

  testWidgets('pre-fills the field with the initial value', (tester) async {
    await tester.pumpWidget(wrap(VehicleSearchField(
      initialValue: _vehicle('Honda', 'Civic', 2022),
      fetchSuggestions: (q) async => [],
      onSelected: (_) {},
    )));

    expect(find.text('Honda Civic (2022)'), findsOneWidget);
  });
}
