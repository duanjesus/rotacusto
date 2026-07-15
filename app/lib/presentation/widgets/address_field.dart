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

  const AddressField({
    super.key,
    required this.controller,
    required this.label,
    required this.fetchSuggestions,
    required this.onSelected,
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
