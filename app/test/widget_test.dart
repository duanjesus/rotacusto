import 'package:flutter_test/flutter_test.dart';

import 'package:rotacusto_app/main.dart';

void main() {
  testWidgets('RotaCusto home screen renders the trip form', (WidgetTester tester) async {
    await tester.pumpWidget(const RotaCustoApp());
    // pumpAndSettle: o initState dispara uma chamada de rede ao catálogo de
    // veículos; em testes o HttpClient é interceptado e responde 400 na hora,
    // mas precisamos deixar essa resolução assentar antes do fim do teste.
    await tester.pumpAndSettle();

    expect(find.text('RotaCusto'), findsOneWidget);
    expect(find.text('Origem'), findsOneWidget);
    expect(find.text('Destino'), findsOneWidget);
    expect(find.text('Calcular'), findsOneWidget);
  });
}
