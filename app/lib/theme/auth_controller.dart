import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

/// Carrega a sessão salva (se houver) — chamado uma vez no início do app,
/// pra sobreviver a fechar/reabrir sem precisar logar de novo toda hora.
Future<void> restoreAuthSession() async {
  final prefs = await SharedPreferences.getInstance();
  final token = prefs.getString(_kTokenKey);
  final email = prefs.getString(_kEmailKey);
  if (token != null && email != null) {
    authSessionNotifier.value = AuthSession(token: token, email: email);
  }
}

Future<void> saveAuthSession(AuthSession session) async {
  final prefs = await SharedPreferences.getInstance();
  await prefs.setString(_kTokenKey, session.token);
  await prefs.setString(_kEmailKey, session.email);
  authSessionNotifier.value = session;
}

Future<void> clearAuthSession() async {
  final prefs = await SharedPreferences.getInstance();
  await prefs.remove(_kTokenKey);
  await prefs.remove(_kEmailKey);
  authSessionNotifier.value = null;
}
