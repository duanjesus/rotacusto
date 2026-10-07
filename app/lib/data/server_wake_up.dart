import 'dart:async';

import 'package:flutter/foundation.dart';

/// Em produção o servidor dorme depois de um tempo sem tráfego (plano
/// gratuito da hospedagem) e a primeira chamada seguinte pode levar mais de
/// um minuto — bem mais que o timeout normal das chamadas do app. Esta
/// classe "acorda" o servidor com um ping de timeout longo antes de qualquer
/// chamada que importa, e avisa a tela quando a espera passa de [avisoApos]
/// pra ela mostrar que não travou. Em dev local o ping volta na hora e o
/// aviso nunca aparece.
class ServerWakeUp {
  final Future<void> Function() _ping;
  final Duration avisoApos;

  /// Por quanto tempo um ping bem-sucedido vale — menor que o tempo que o
  /// servidor leva pra dormir de novo (15 min), com folga.
  final Duration validade;
  final DateTime Function() _agora;

  /// `true` só enquanto um ping está demorando mais que [avisoApos].
  final ValueNotifier<bool> acordando = ValueNotifier(false);

  DateTime? _ultimoOk;
  Future<void>? _emAndamento;

  ServerWakeUp(
    this._ping, {
    this.avisoApos = const Duration(seconds: 2),
    this.validade = const Duration(minutes: 10),
    DateTime Function()? agora,
  }) : _agora = agora ?? DateTime.now;

  /// Completa quando o servidor respondeu (ou o ping falhou — nunca lança:
  /// quem chamou segue em frente e a chamada de verdade mostra o erro certo).
  Future<void> ensureAwake() {
    final ultimoOk = _ultimoOk;
    if (ultimoOk != null && _agora().difference(ultimoOk) < validade) {
      return Future.value();
    }
    return _emAndamento ??= _acordar();
  }

  Future<void> _acordar() async {
    final aviso = Timer(avisoApos, () => acordando.value = true);
    try {
      await _ping();
      _ultimoOk = _agora();
    } catch (_) {
      // Ver doc de ensureAwake.
    } finally {
      aviso.cancel();
      acordando.value = false;
      _emAndamento = null;
    }
  }
}
