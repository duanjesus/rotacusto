import 'package:flutter/material.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

import 'presentation/screens/home_screen.dart';
import 'theme/app_theme.dart';
import 'theme/theme_controller.dart';

void main() {
  // Necessário mesmo em plataformas sem suporte a foreground service
  // (Windows/web) — a chamada é um no-op segura nelas, e sem ela a
  // comunicação TaskHandler↔UI da navegação em segundo plano (Fase 6.3)
  // não funciona no Android.
  FlutterForegroundTask.initCommunicationPort();
  runApp(const RotaCustoApp());
}

class RotaCustoApp extends StatelessWidget {
  const RotaCustoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<ThemeMode>(
      valueListenable: themeModeNotifier,
      builder: (context, themeMode, _) {
        return MaterialApp(
          title: 'RotaCusto',
          theme: AppTheme.light(),
          darkTheme: AppTheme.dark(),
          themeMode: themeMode,
          home: const HomeScreen(),
        );
      },
    );
  }
}
