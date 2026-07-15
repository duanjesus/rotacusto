import 'package:flutter/material.dart';

import 'presentation/screens/home_screen.dart';
import 'theme/app_theme.dart';
import 'theme/theme_controller.dart';

void main() {
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
