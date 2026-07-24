import 'package:flutter/material.dart';

import '../../domain/models/tts_voice.dart';

/// Conteúdo do bottom sheet de escolha da voz do TTS (Fase 12.2) — mesma
/// estrutura de `VoiceModePicker`, mas com conteúdo assíncrono (a lista de
/// vozes vem do motor de TTS do aparelho, `FlutterTts().getVoices`).
class TtsVoicePicker extends StatelessWidget {
  final Future<List<TtsVoice>> vozesFuture;
  final TtsVoice? vozAtual;
  final void Function(TtsVoice? voz) onSelected;

  const TtsVoicePicker({super.key, required this.vozesFuture, required this.vozAtual, required this.onSelected});

  bool _ehVozAtual(TtsVoice voz) => vozAtual != null && vozAtual!.name == voz.name && vozAtual!.locale == voz.locale;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
              child: Text(
                'Voz da navegação',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.smartphone_rounded),
              title: const Text('Voz padrão do sistema'),
              trailing: vozAtual == null ? const Icon(Icons.check_rounded) : null,
              onTap: () => onSelected(null),
            ),
            FutureBuilder<List<TtsVoice>>(
              future: vozesFuture,
              builder: (context, snapshot) {
                if (!snapshot.hasData) {
                  return const Padding(
                    padding: EdgeInsets.all(24),
                    child: Center(child: CircularProgressIndicator()),
                  );
                }
                final vozes = snapshot.data!;
                if (vozes.isEmpty) {
                  return const Padding(
                    padding: EdgeInsets.fromLTRB(20, 8, 20, 20),
                    child: Text('Nenhuma outra voz em português encontrada neste aparelho.'),
                  );
                }
                return Column(
                  children: [
                    for (final voz in vozes)
                      ListTile(
                        leading: const Icon(Icons.record_voice_over_rounded),
                        title: Text(voz.name),
                        subtitle: Text(voz.locale),
                        trailing: _ehVozAtual(voz) ? const Icon(Icons.check_rounded) : null,
                        onTap: () => onSelected(voz),
                      ),
                  ],
                );
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
