import 'package:flutter/material.dart';

/// Estado do tema (claro/escuro) compartilhado entre o app inteiro.
/// Começa seguindo o sistema — o botão de sol/lua na AppBar assume o
/// controle manual assim que o usuário toca nele pela primeira vez.
final ValueNotifier<ThemeMode> themeModeNotifier = ValueNotifier(ThemeMode.system);

void toggleThemeMode(BuildContext context) {
  final estaEscuroAgora = themeModeNotifier.value == ThemeMode.dark ||
      (themeModeNotifier.value == ThemeMode.system && MediaQuery.platformBrightnessOf(context) == Brightness.dark);
  themeModeNotifier.value = estaEscuroAgora ? ThemeMode.light : ThemeMode.dark;
}
