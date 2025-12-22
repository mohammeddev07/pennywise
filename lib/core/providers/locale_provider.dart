import 'dart:ui';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'locale_provider.g.dart';

@riverpod
class LocaleNotifier extends _$LocaleNotifier {
  static const String _localeKey = 'selected_locale';
  
  @override
  Locale build() {
    _loadLocale();
    return const Locale('en'); // Default to English
  }

  void _loadLocale() async {
    final prefs = await SharedPreferences.getInstance();
    final localeCode = prefs.getString(_localeKey);
    
    if (localeCode != null) {
      // Parse locale code (e.g., 'en_US' or 'en')
      final parts = localeCode.split('_');
      final languageCode = parts[0];
      final countryCode = parts.length > 1 ? parts[1] : null;
      
      state = Locale(languageCode, countryCode);
    }
  }

  Future<void> setLocale(Locale locale) async {
    final prefs = await SharedPreferences.getInstance();
    final localeCode = locale.countryCode != null 
        ? '${locale.languageCode}_${locale.countryCode}'
        : locale.languageCode;
    
    await prefs.setString(_localeKey, localeCode);
    state = locale;
  }

  Future<void> setLocaleFromLanguageCode(String languageCode) async {
    await setLocale(Locale(languageCode));
  }
}

// Supported locales
class SupportedLocales {
  static const List<Locale> locales = [
    Locale('en'), // English
    Locale('es'), // Spanish
    Locale('fr'), // French
  ];

  static const Map<String, String> localeNames = {
    'en': 'English',
    'es': 'Español',
    'fr': 'Français',
  };

  static String getLocaleName(String languageCode) {
    return localeNames[languageCode] ?? languageCode;
  }

  static bool isSupported(Locale locale) {
    return locales.any((supportedLocale) => 
        supportedLocale.languageCode == locale.languageCode);
  }
}