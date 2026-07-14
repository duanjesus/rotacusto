import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rotacusto_app/domain/models/vehicle_model_summary.dart';
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
    )));

    expect(find.text('Honda Civic'), findsOneWidget);
  });
}
