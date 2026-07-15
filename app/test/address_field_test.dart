import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rotacusto_app/domain/models/address_suggestion.dart';
import 'package:rotacusto_app/presentation/widgets/address_field.dart';

void main() {
  Widget wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

  testWidgets('does not fetch suggestions for queries shorter than 3 chars', (tester) async {
    var callCount = 0;
    final controller = TextEditingController();

    await tester.pumpWidget(wrap(AddressField(
      controller: controller,
      label: 'Origem',
      fetchSuggestions: (q) async {
        callCount++;
        return [];
      },
      onSelected: (_) {},
    )));

    await tester.enterText(find.byType(TextField), 'Ri');
    await tester.pump(const Duration(milliseconds: 500));

    expect(callCount, 0);
  });

  testWidgets('shows suggestions after debounce and lets user pick one', (tester) async {
    AddressSuggestion? selected;
    final controller = TextEditingController();

    await tester.pumpWidget(wrap(AddressField(
      controller: controller,
      label: 'Origem',
      fetchSuggestions: (q) async => [
        AddressSuggestion(displayName: 'Rio das Ostras, RJ, Brasil', lat: -22.53, lon: -41.94),
        AddressSuggestion(displayName: 'Rio de Janeiro, RJ, Brasil', lat: -22.91, lon: -43.17),
      ],
      onSelected: (s) => selected = s,
    )));

    await tester.enterText(find.byType(TextField), 'Rio das Ost');
    // Ainda dentro da janela de debounce: nenhuma sugestão deve aparecer.
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.text('Rio das Ostras, RJ, Brasil'), findsNothing);

    // Passa do debounce (400ms) + deixa a Future resolver.
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump();

    expect(find.text('Rio das Ostras, RJ, Brasil'), findsOneWidget);
    expect(find.text('Rio de Janeiro, RJ, Brasil'), findsOneWidget);

    await tester.tap(find.text('Rio das Ostras, RJ, Brasil'));
    await tester.pump();

    expect(selected, isNotNull);
    expect(selected!.lat, -22.53);
    expect(controller.text, 'Rio das Ostras, RJ, Brasil');
    // Sugestões somem depois de escolher uma.
    expect(find.text('Rio de Janeiro, RJ, Brasil'), findsNothing);
  });

  testWidgets('clears previous selection when user edits the field manually', (tester) async {
    AddressSuggestion? selected = AddressSuggestion(displayName: 'placeholder', lat: 0, lon: 0);
    final controller = TextEditingController();

    await tester.pumpWidget(wrap(AddressField(
      controller: controller,
      label: 'Origem',
      fetchSuggestions: (q) async => [],
      onSelected: (s) => selected = s,
    )));

    await tester.enterText(find.byType(TextField), 'Nova busca');
    await tester.pump();

    expect(selected, isNull);
  });

  testWidgets('shows the current-location link only when the callback is provided', (tester) async {
    final controller = TextEditingController();

    await tester.pumpWidget(wrap(AddressField(
      controller: controller,
      label: 'Origem',
      fetchSuggestions: (q) async => [],
      onSelected: (_) {},
    )));
    expect(find.text('Usar localização atual'), findsNothing);

    var tapped = false;
    await tester.pumpWidget(wrap(AddressField(
      controller: controller,
      label: 'Origem',
      fetchSuggestions: (q) async => [],
      onSelected: (_) {},
      onUseCurrentLocation: () => tapped = true,
    )));
    expect(find.text('Usar localização atual'), findsOneWidget);

    await tester.tap(find.text('Usar localização atual'));
    expect(tapped, isTrue);
  });
}
