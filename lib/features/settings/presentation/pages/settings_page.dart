import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/providers/theme_provider.dart';
import '../../../../core/constants/colors.dart';
import '../../../../features/auth/presentation/providers/auth_provider.dart';
import '../../../../shared/widgets/guest_mode_banner.dart';
import '../../../../l10n/app_localizations.dart';

class SettingsPage extends ConsumerWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeState = ref.watch(themeNotifierProvider);
    final authState = ref.watch(authProvider);
    
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context).settings),
        actions: [
          if (authState.isAuthenticated)
            IconButton(
              icon: const Icon(Icons.logout),
              onPressed: () {
                ref.read(authProvider.notifier).logout();
                context.go('/home');
              },
              tooltip: AppLocalizations.of(context).logout,
            ),
        ],
      ),
      body: _buildSettings(context, ref, themeState, authState.isAuthenticated),
    );
  }

  Widget _buildSettings(BuildContext context, WidgetRef ref, ThemeState themeState, bool isAuthenticated) {
    return ListView(
      children: [
        // Guest mode banner
        if (!isAuthenticated) GuestModeBanner(
          customMessage: AppLocalizations.of(context).themeChangeNotSaved,
          loginBenefits: [
            AppLocalizations.of(context).saveThemePreferences,
            AppLocalizations.of(context).accessUserManagement,
            AppLocalizations.of(context).syncSettingsAcrossDevices,
          ],
        ),
        
        _SectionHeader(title: AppLocalizations.of(context).appearance),
        SwitchListTile(
          title: Text(AppLocalizations.of(context).darkMode),
          subtitle: Text(isAuthenticated 
              ? AppLocalizations.of(context).useDarkTheme 
              : AppLocalizations.of(context).useDarkThemeNotSaved),
          value: themeState.themeMode == ThemeMode.dark,
          onChanged: (value) {
            ref.read(themeNotifierProvider.notifier).setThemeMode(
              value ? ThemeMode.dark : ThemeMode.light,
            );
          },
        ),
        const Divider(),
        _SectionHeader(title: AppLocalizations.of(context).themeColor),
        ...ColorSeed.values.map(
          (seed) => RadioListTile<ColorSeed>(
            title: Text(seed.label),
            secondary: Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: seed.color,
                shape: BoxShape.circle,
                border: Border.all(
                  color: Theme.of(context).colorScheme.outline,
                  width: 1,
                ),
              ),
            ),
            value: seed,
            groupValue: themeState.colorSeed,
            onChanged: (value) {
              if (value != null) {
                ref.read(themeNotifierProvider.notifier).setColorSeed(value);
              }
            },
          ),
        ),
        const Divider(),
        _SectionHeader(title: AppLocalizations.of(context).about),
        ListTile(
          leading: const Icon(Icons.info_outline),
          title: Text(AppLocalizations.of(context).flutterStarterApp),
          subtitle: Text(AppLocalizations.of(context).version),
          onTap: () {
            showAboutDialog(
              context: context,
              applicationName: AppLocalizations.of(context).flutterStarter,
              applicationVersion: '1.0.0',
              applicationLegalese: AppLocalizations.of(context).material3StarterTemplate,
              children: [
                const SizedBox(height: 16),
                Text(
                  AppLocalizations.of(context).starterFeatures,
                ),
              ],
            );
          },
        ),
      ],
    );
  }

}

class _SectionHeader extends StatelessWidget {
  final String title;
  
  const _SectionHeader({required this.title, super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
          color: Theme.of(context).colorScheme.primary,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}