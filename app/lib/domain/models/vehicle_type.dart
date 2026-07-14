enum VehicleType {
  carro,
  moto,
  caminhao,
  van,
  onibus;

  String get apiValue => name.toUpperCase();

  static VehicleType fromApiValue(String value) {
    return VehicleType.values.firstWhere(
      (t) => t.apiValue == value,
      orElse: () => VehicleType.carro,
    );
  }

  String get label {
    switch (this) {
      case VehicleType.carro:
        return 'Carro';
      case VehicleType.moto:
        return 'Moto';
      case VehicleType.caminhao:
        return 'Caminhão';
      case VehicleType.van:
        return 'Van';
      case VehicleType.onibus:
        return 'Ônibus';
    }
  }
}
