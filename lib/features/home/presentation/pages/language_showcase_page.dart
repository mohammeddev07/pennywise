import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../l10n/app_localizations.dart';
import '../../../../core/providers/locale_provider.dart';

class LanguageShowcasePage extends ConsumerWidget {
  const LanguageShowcasePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final currentLocale = ref.watch(localeNotifierProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.language),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.language,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Select your preferred language / Selecciona tu idioma preferido / Sélectionnez votre langue préférée',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 32),
            
            // Language Selection
            ...SupportedLocales.locales.map((locale) {
              final isSelected = currentLocale.languageCode == locale.languageCode;
              
              return Card(
                margin: const EdgeInsets.only(bottom: 8),
                color: isSelected 
                    ? Theme.of(context).colorScheme.primaryContainer
                    : null,
                child: ListTile(
                  leading: CircleAvatar(
                    backgroundColor: isSelected 
                        ? Theme.of(context).colorScheme.primary
                        : Theme.of(context).colorScheme.surfaceContainerHighest,
                    child: Text(
                      _getLanguageFlag(locale.languageCode),
                      style: const TextStyle(fontSize: 20),
                    ),
                  ),
                  title: Text(
                    SupportedLocales.getLocaleName(locale.languageCode),
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: isSelected ? FontWeight.bold : null,
                      color: isSelected 
                          ? Theme.of(context).colorScheme.onPrimaryContainer
                          : null,
                    ),
                  ),
                  subtitle: Text(
                    _getLanguageNativeName(locale.languageCode),
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: isSelected 
                          ? Theme.of(context).colorScheme.onPrimaryContainer
                          : Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  trailing: isSelected
                      ? Icon(
                          Icons.check_circle,
                          color: Theme.of(context).colorScheme.primary,
                        )
                      : const Icon(Icons.radio_button_unchecked),
                  onTap: () {
                    ref.read(localeNotifierProvider.notifier).setLocale(locale);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text(
                          'Language changed to ${SupportedLocales.getLocaleName(locale.languageCode)}',
                        ),
                        behavior: SnackBarBehavior.floating,
                      ),
                    );
                  },
                ),
              );
            }),
            
            const SizedBox(height: 32),
            
            // Sample Text in Different Languages
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Sample Text Preview',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 16),
                    _buildSampleText(context, l10n),
                  ],
                ),
              ),
            ),
            
            const SizedBox(height: 32),
            
            // Language Statistics
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Localization Info',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 16),
                    _buildInfoRow(
                      context,
                      'Current Language',
                      SupportedLocales.getLocaleName(currentLocale.languageCode),
                    ),
                    _buildInfoRow(
                      context,
                      'Language Code',
                      currentLocale.languageCode,
                    ),
                    _buildInfoRow(
                      context,
                      'Supported Languages',
                      SupportedLocales.locales.length.toString(),
                    ),
                    _buildInfoRow(
                      context,
                      'System Locale',
                      Localizations.localeOf(context).toString(),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSampleText(BuildContext context, AppLocalizations l10n) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildTextSample(context, l10n.welcomeTitle, 'Title'),
        const SizedBox(height: 12),
        _buildTextSample(context, l10n.welcomeSubtitle, 'Subtitle'),
        const SizedBox(height: 12),
        _buildTextSample(context, l10n.dashboard, 'Navigation'),
        const SizedBox(height: 12),
        _buildTextSample(context, l10n.login, 'Action'),
      ],
    );
  }

  Widget _buildTextSample(BuildContext context, String text, String label) {
    return Row(
      children: [
        SizedBox(
          width: 80,
          child: Text(
            '$label:',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              fontWeight: FontWeight.w500,
              color: Theme.of(context).colorScheme.primary,
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            text,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ),
      ],
    );
  }

  Widget _buildInfoRow(BuildContext context, String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(
            width: 140,
            child: Text(
              '$label:',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }

  String _getLanguageFlag(String languageCode) {
    switch (languageCode) {
      case 'en':
        return '🇺🇸';
      case 'es':
        return '🇪🇸';
      case 'fr':
        return '🇫🇷';
      default:
        return '🌍';
    }
  }

  String _getLanguageNativeName(String languageCode) {
    switch (languageCode) {
      case 'en':
        return 'English';
      case 'es':
        return 'Español';
      case 'fr':
        return 'Français';
      default:
        return languageCode;
    }
  }
}