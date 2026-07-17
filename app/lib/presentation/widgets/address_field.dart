import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/models/address_suggestion.dart';

/// Campo de texto com dropdown de sugestões de endereço, buscando no
/// back-end (Photon/OSM) conforme o usuário digita, com debounce.
class AddressField extends StatefulWidget {
  final TextEditingController controller;
  final String label;
  final Future<List<AddressSuggestion>> Function(String query) fetchSuggestions;
  final void Function(AddressSuggestion?) onSelected;
  /// Só faz sentido pro campo de Origem — null esconde o link (Destino não
  /// tem esse botão).
  final VoidCallback? onUseCurrentLocation;
  /// Atalhos mostrados no lugar do dropdown de sugestões ao vivo, só quando o
  /// campo está vazio (Fase 9) — favoritos primeiro, depois recentes. Vazios
  /// por padrão, então Origem/Paradas continuam exatamente como antes.
  final List<AddressSuggestion> favoritos;
  final List<AddressSuggestion> recentes;

  const AddressField({
    super.key,
    required this.controller,
    required this.label,
    required this.fetchSuggestions,
    required this.onSelected,
    this.onUseCurrentLocation,
    this.favoritos = const [],
    this.recentes = const [],
  });

  @override
  State<AddressField> createState() => _AddressFieldState();
}

class _AddressFieldState extends State<AddressField> {
  static const _debounceDuration = Duration(milliseconds: 400);
  static const _minQueryLength = 3;

  Timer? _debounce;
  List<AddressSuggestion> _suggestions = [];
  bool _loading = false;

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  void _onChanged(String value) {
    widget.onSelected(null); // edição manual invalida a seleção anterior
    _debounce?.cancel();

    if (value.trim().length < _minQueryLength) {
      setState(() => _suggestions = []);
      return;
    }

    _debounce = Timer(_debounceDuration, () => _fetchSuggestions(value));
  }

  Future<void> _fetchSuggestions(String query) async {
    setState(() => _loading = true);
    try {
      final results = await widget.fetchSuggestions(query);
      if (!mounted) return;
      setState(() {
        _suggestions = results;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _suggestions = [];
        _loading = false;
      });
    }
  }

  void _select(AddressSuggestion suggestion) {
    widget.controller.text = suggestion.displayName;
    widget.onSelected(suggestion);
    setState(() => _suggestions = []);
    FocusScope.of(context).unfocus();
  }

  @override
  Widget build(BuildContext context) {
    final campoVazio = widget.controller.text.trim().isEmpty;
    // Um destino favoritado também aparece nos recentes se foi usado
    // recentemente — evita listar a mesma linha duas vezes.
    final recentesSemFavoritos = widget.recentes
        .where((r) => !widget.favoritos.any((f) => f.displayName == r.displayName))
        .toList();
    final mostrarAtalhos = campoVazio && (widget.favoritos.isNotEmpty || recentesSemFavoritos.isNotEmpty);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: widget.controller,
          decoration: InputDecoration(
            labelText: widget.label,
            suffixIcon: _loading
                ? const Padding(
                    padding: EdgeInsets.all(12),
                    child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                  )
                : null,
          ),
          onChanged: _onChanged,
        ),
        if (widget.onUseCurrentLocation != null)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: InkWell(
              onTap: widget.onUseCurrentLocation,
              borderRadius: BorderRadius.circular(6),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.my_location_rounded, size: 14, color: Theme.of(context).colorScheme.primary),
                    const SizedBox(width: 4),
                    Text(
                      'Usar localização atual',
                      style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.primary),
                    ),
                  ],
                ),
              ),
            ),
          ),
        if (mostrarAtalhos)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Material(
              elevation: 3,
              borderRadius: BorderRadius.circular(14),
              clipBehavior: Clip.antiAlias,
              color: Theme.of(context).colorScheme.surfaceContainerHigh,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxHeight: 220),
                child: ListView(
                  padding: EdgeInsets.zero,
                  shrinkWrap: true,
                  children: [
                    for (final favorito in widget.favoritos)
                      ListTile(
                        dense: true,
                        leading: const Icon(Icons.star_rounded, size: 18),
                        title: Text(favorito.displayName, style: const TextStyle(fontSize: 13)),
                        onTap: () => _select(favorito),
                      ),
                    for (final recente in recentesSemFavoritos)
                      ListTile(
                        dense: true,
                        leading: const Icon(Icons.history_rounded, size: 18),
                        title: Text(recente.displayName, style: const TextStyle(fontSize: 13)),
                        onTap: () => _select(recente),
                      ),
                  ],
                ),
              ),
            ),
          ),
        if (_suggestions.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Material(
              elevation: 3,
              borderRadius: BorderRadius.circular(14),
              clipBehavior: Clip.antiAlias,
              color: Theme.of(context).colorScheme.surfaceContainerHigh,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxHeight: 220),
                child: ListView.builder(
                  padding: EdgeInsets.zero,
                  shrinkWrap: true,
                  itemCount: _suggestions.length,
                  itemBuilder: (context, index) {
                    final suggestion = _suggestions[index];
                    return ListTile(
                      dense: true,
                      leading: const Icon(Icons.location_on_outlined, size: 18),
                      title: Text(suggestion.displayName, style: const TextStyle(fontSize: 13)),
                      onTap: () => _select(suggestion),
                    );
                  },
                ),
              ),
            ),
          ),
      ],
    );
  }
}
