enum TipoCombustivel {
  gasolina,
  etanol,
  diesel,
  eletrico;

  String get apiValue => name.toUpperCase();

  static TipoCombustivel fromApiValue(String? value) {
    return TipoCombustivel.values.firstWhere(
      (t) => t.apiValue == value,
      orElse: () => TipoCombustivel.gasolina,
    );
  }

  String get label {
    switch (this) {
      case TipoCombustivel.gasolina:
        return 'Gasolina';
      case TipoCombustivel.etanol:
        return 'Etanol';
      case TipoCombustivel.diesel:
        return 'Diesel';
      case TipoCombustivel.eletrico:
        return 'Elétrico';
    }
  }
}
