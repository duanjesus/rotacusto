import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:rotacusto_app/data/server_wake_up.dart';

const _avisoApos = Duration(milliseconds: 20);
const _bemDepoisDoAviso = Duration(milliseconds: 120);

void main() {
  test('nunca mostra o aviso quando o servidor responde rápido', () async {
    final wakeUp = ServerWakeUp(() async {}, avisoApos: _avisoApos);
    final avisos = <bool>[];
    wakeUp.acordando.addListener(() => avisos.add(wakeUp.acordando.value));

    await wakeUp.ensureAwake();
    await Future<void>.delayed(_bemDepoisDoAviso);

    expect(avisos, isEmpty);
  });

  test('mostra o aviso enquanto o ping demora e esconde quando ele volta', () async {
    final ping = Completer<void>();
    final wakeUp = ServerWakeUp(() => ping.future, avisoApos: _avisoApos);

    final espera = wakeUp.ensureAwake();
    expect(wakeUp.acordando.value, isFalse);

    await Future<void>.delayed(_bemDepoisDoAviso);
    expect(wakeUp.acordando.value, isTrue);

    ping.complete();
    await espera;
    expect(wakeUp.acordando.value, isFalse);
  });

  test('chamadas simultâneas compartilham um único ping', () async {
    final ping = Completer<void>();
    var pings = 0;
    final wakeUp = ServerWakeUp(() {
      pings++;
      return ping.future;
    });

    final primeira = wakeUp.ensureAwake();
    final segunda = wakeUp.ensureAwake();
    ping.complete();
    await Future.wait([primeira, segunda]);

    expect(pings, 1);
  });

  test('não pinga de novo dentro da validade, pinga depois dela', () async {
    var agora = DateTime(2026, 1, 1, 12);
    var pings = 0;
    final wakeUp = ServerWakeUp(
      () async => pings++,
      validade: const Duration(minutes: 10),
      agora: () => agora,
    );

    await wakeUp.ensureAwake();
    agora = agora.add(const Duration(minutes: 9));
    await wakeUp.ensureAwake();
    expect(pings, 1);

    agora = agora.add(const Duration(minutes: 2));
    await wakeUp.ensureAwake();
    expect(pings, 2);
  });

  test('ping que falha não lança, esconde o aviso e permite tentar de novo', () async {
    var pings = 0;
    final wakeUp = ServerWakeUp(() async {
      pings++;
      throw Exception('sem conexão');
    });

    await wakeUp.ensureAwake();
    expect(wakeUp.acordando.value, isFalse);

    await wakeUp.ensureAwake();
    expect(pings, 2);
  });
}
