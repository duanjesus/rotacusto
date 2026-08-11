import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Sessão de login — compartilhada pelo app inteiro, mesmo padrão do
/// theme_controller.dart. Login é opcional (o app inteiro funciona sem
/// conta); isso só guarda o token/e-mail atual quando existe um.
class AuthSession {
  final String token;
  final String email;

  const AuthSession({required this.token, required this.email});
}

final ValueNotifier<AuthSession?> authSessionNotifier = ValueNotifier(null);

const _kTokenKey = 'auth_token';
const _kEmailKey = 'auth_email';

// Fase 17 — token/e-mail passam a ficar em storage criptografado (Keychain no
// iOS, Windows Credential Manager, Android Keystore) em vez de shared_preferences
// puro (arquivo em texto claro em disco). Mesma API read/write/delete, troca
// mecânica — nenhum outro código deste arquivo muda.
const _secureStorage = FlutterSecureStorage();

/// Carrega a sessão salva (se houver) — chamado uma vez no início do app,
/// pra sobreviver a fechar/reabrir sem precisar logar de novo toda hora.
Future<void> restoreAuthSession() async {
  final token = await _secureStorage.read(key: _kTokenKey);
  final email = await _secureStorage.read(key: _kEmailKey);
  if (token != null && email != null) {
    authSessionNotifier.value = AuthSession(token: token, email: email);
  }
}

Future<void> saveAuthSession(AuthSession session) async {
  await _secureStorage.write(key: _kTokenKey, value: session.token);
  await _secureStorage.write(key: _kEmailKey, value: session.email);
  authSessionNotifier.value = session;
}

Future<void> clearAuthSession() async {
  await _secureStorage.delete(key: _kTokenKey);
  await _secureStorage.delete(key: _kEmailKey);
  authSessionNotifier.value = null;
}
