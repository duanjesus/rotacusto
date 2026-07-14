class AddressSuggestion {
  final String displayName;
  final double lat;
  final double lon;

  AddressSuggestion({required this.displayName, required this.lat, required this.lon});

  factory AddressSuggestion.fromJson(Map<String, dynamic> json) {
    return AddressSuggestion(
      displayName: json['displayName'] as String,
      lat: (json['lat'] as num).toDouble(),
      lon: (json['lon'] as num).toDouble(),
    );
  }
}
