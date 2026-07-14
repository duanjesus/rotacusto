enum TipoEnergia {
  combustao,
  eletrico;

  String get apiValue => name.toUpperCase();

  static TipoEnergia fromApiValue(String? value) {
    return TipoEnergia.values.firstWhere(
      (t) => t.apiValue == value,
      orElse: () => TipoEnergia.combustao,
    );
  }
}
