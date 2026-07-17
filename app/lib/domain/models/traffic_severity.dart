import 'package:flutter/material.dart';

/// Severidade de um relato de trânsito lento (Fase 6.7) — gerado
/// automaticamente pelo app ao detectar velocidade bem abaixo da esperada
/// pra via, não reportado manualmente como [RoadAlertType].
enum TrafficSeverity {
  leve,
  medio,
  intenso;

  String get apiValue {
    switch (this) {
      case TrafficSeverity.leve:
        return 'LEVE';
      case TrafficSeverity.medio:
        return 'MEDIO';
      case TrafficSeverity.intenso:
        return 'INTENSO';
    }
  }

  static TrafficSeverity fromApiValue(String value) {
    return TrafficSeverity.values.firstWhere(
      (s) => s.apiValue == value,
      orElse: () => TrafficSeverity.leve,
    );
  }

  String get label {
    switch (this) {
      case TrafficSeverity.leve:
        return 'Trânsito lento';
      case TrafficSeverity.medio:
        return 'Trânsito médio';
      case TrafficSeverity.intenso:
        return 'Trânsito intenso';
    }
  }

  /// Amarelo puro é ilegível sobre o tile claro do mapa — amber.shade700 dá
  /// contraste sem perder a leitura "amarelo = pouco trânsito".
  Color get color {
    switch (this) {
      case TrafficSeverity.leve:
        return Colors.amber.shade700;
      case TrafficSeverity.medio:
        return Colors.orange;
      case TrafficSeverity.intenso:
        return Colors.red;
    }
  }
}
