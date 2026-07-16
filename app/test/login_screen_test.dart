import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rotacusto_app/presentation/screens/login_screen.dart';
import 'package:rotacusto_app/theme/auth_controller.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    authSessionNotifier.value = null;
  });

  // Empilha LoginScreen sobre uma tela placeholder — voltar a ver "início"
  // depois de interagir é como confirmamos que a tela deu Navigator.pop()
  // (login/registro bem-sucedido).
  Widget wrap(LoginScreen loginScreen) {
    return MaterialApp(
      home: Builder(
        builder: (context) => Scaffold(
          body: Center(
            child: ElevatedButton(
              onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => loginScreen)),
              child: const Text('início'),
            ),
          ),
        ),
      ),
    );
  }

  testWidgets('mostra o formulário de login por padrão', (tester) async {
    await tester.pumpWidget(wrap(LoginScreen(
      register: (_, _) async => const AuthSession(token: 't', email: 'e'),
      login: (_, _) async => const AuthSession(token: 't', email: 'e'),
    )));
    await tester.tap(find.text('início'));
    await tester.pumpAndSettle();

    expect(find.widgetWithText(AppBar, 'Entrar'), findsOneWidget);
    expect(find.text('Não tenho conta — criar uma'), findsOneWidget);
  });

  testWidgets('alterna pra registro ao tocar no link', (tester) async {
    await tester.pumpWidget(wrap(LoginScreen(
      register: (_, _) async => const AuthSession(token: 't', email: 'e'),
      login: (_, _) async => const AuthSession(token: 't', email: 'e'),
    )));
    await tester.tap(find.text('início'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Não tenho conta — criar uma'));
    await tester.pump();

    expect(find.widgetWithText(AppBar, 'Criar conta'), findsOneWidget);
    expect(find.text('Já tenho conta — entrar'), findsOneWidget);
  });

  testWidgets('não envia com e-mail ou senha vazios', (tester) async {
    var chamado = false;
    await tester.pumpWidget(wrap(LoginScreen(
      register: (_, _) async => const AuthSession(token: 't', email: 'e'),
      login: (_, _) async {
        chamado = true;
        return const AuthSession(token: 't', email: 'e');
      },
    )));
    await tester.tap(find.text('início'));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
    await tester.pump();

    expect(chamado, isFalse);
    expect(find.text('Preencha e-mail e senha.'), findsOneWidget);
  });

  testWidgets('login bem-sucedido salva a sessão e fecha a tela', (tester) async {
    String? emailRecebido;
    String? senhaRecebida;

    await tester.pumpWidget(wrap(LoginScreen(
      register: (_, _) async => const AuthSession(token: 't', email: 'e'),
      login: (email, senha) async {
        emailRecebido = email;
        senhaRecebida = senha;
        return const AuthSession(token: 'abc123', email: 'teste@rotacusto.com');
      },
    )));
    await tester.tap(find.text('início'));
    await tester.pumpAndSettle();

    await tester.enterText(find.widgetWithText(TextField, 'E-mail'), 'teste@rotacusto.com');
    await tester.enterText(find.widgetWithText(TextField, 'Senha'), 'senha123');
    await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
    await tester.pumpAndSettle();

    expect(emailRecebido, 'teste@rotacusto.com');
    expect(senhaRecebida, 'senha123');
    expect(authSessionNotifier.value?.token, 'abc123');
    expect(find.text('início'), findsOneWidget); // voltou pra tela anterior
  });

  testWidgets('mostra a mensagem de erro do back-end quando o login falha', (tester) async {
    await tester.pumpWidget(wrap(LoginScreen(
      register: (_, _) async => const AuthSession(token: 't', email: 'e'),
      login: (_, _) async {
        throw DioException(
          requestOptions: RequestOptions(path: '/api/auth/login'),
          response: Response(
            requestOptions: RequestOptions(path: '/api/auth/login'),
            statusCode: 400,
            data: {'message': 'E-mail ou senha inválidos.'},
          ),
        );
      },
    )));
    await tester.tap(find.text('início'));
    await tester.pumpAndSettle();

    await tester.enterText(find.widgetWithText(TextField, 'E-mail'), 'teste@rotacusto.com');
    await tester.enterText(find.widgetWithText(TextField, 'Senha'), 'errada');
    await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
    await tester.pumpAndSettle();

    expect(find.text('E-mail ou senha inválidos.'), findsOneWidget);
    expect(authSessionNotifier.value, isNull);
  });
}
