import 'package:flutter/material.dart';

import '../../domain/models/road_alert_type.dart';

/// Conteúdo do bottom sheet "Reportar um problema" da tela de navegação
/// (Fase 6.6) — widget separado e sem `ApiClient` nenhum embutido, só um
/// callback [onSelected], pro mesmo motivo de `VehicleSearchField`/
/// `AddressField`: dá pra testar sem precisar de rede de verdade.
class RoadAlertPicker extends StatelessWidget {
  final void Function(RoadAlertType tipo) onSelected;

  const RoadAlertPicker({super.key, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    // SingleChildScrollView: com os 6 tipos + título, o conteúdo passa da
    // altura padrão do bottom sheet em telas de celular menores (achado
    // testando de verdade no emulador — sem isso, o último item ficava
    // cortado). Precisa de isScrollControlled: true em showModalBottomSheet
    // pra isso funcionar (senão a altura fica travada em ~metade da tela).
    return SafeArea(
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
              child: Text(
                'Reportar um problema na via',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
              ),
            ),
            for (final tipo in RoadAlertType.values)
              ListTile(
                leading: Icon(tipo.icon),
                title: Text(tipo.label),
                onTap: () => onSelected(tipo),
              ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
