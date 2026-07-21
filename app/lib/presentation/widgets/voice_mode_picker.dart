import 'package:flutter/material.dart';

import '../../domain/navigation/voice_mode.dart';

/// Conteúdo do bottom sheet de escolha do modo de voz da navegação (Fase 12)
/// — mesma estrutura de `RoadAlertPicker`: widget sem `ApiClient`/persistência
/// embutida, só callback [onSelected], testável sem rede/shared_preferences.
class VoiceModePicker extends StatelessWidget {
  final NavigationVoiceMode modoAtual;
  final void Function(NavigationVoiceMode modo) onSelected;

  const VoiceModePicker({super.key, required this.modoAtual, required this.onSelected});

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
                'Voz durante a navegação',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
              ),
            ),
            for (final modo in NavigationVoiceMode.values)
              ListTile(
                leading: Icon(modo.icon),
                title: Text(modo.label),
                subtitle: Text(modo.descricao),
                trailing: modo == modoAtual ? const Icon(Icons.check_rounded) : null,
                onTap: () => onSelected(modo),
              ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
