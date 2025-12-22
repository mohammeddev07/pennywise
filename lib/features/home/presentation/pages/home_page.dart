import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:go_router/go_router.dart';
import '../../../../l10n/app_localizations.dart';
import '../../../../features/auth/presentation/providers/auth_provider.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final authState = ref.watch(authProvider);
    
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.home),
        actions: [
          if (authState.isAuthenticated)
            IconButton(
              icon: const Icon(Icons.logout),
              onPressed: () => ref.read(authProvider.notifier).logout(),
              tooltip: l10n.logout,
            )
          else
            IconButton(
              icon: const Icon(Icons.login),
              onPressed: () => context.go('/login'),
              tooltip: l10n.login,
            ),
        ],
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.rocket_launch,
                size: 80,
                color: Theme.of(context).colorScheme.primary,
              ).animate().scale(duration: 600.ms, curve: Curves.easeOutBack),
              const SizedBox(height: 24),
              Text(
                l10n.welcomeTitle,
                style: Theme.of(context).textTheme.headlineMedium,
                textAlign: TextAlign.center,
              ).animate().fadeIn(delay: 200.ms),
              const SizedBox(height: 16),
              Text(
                l10n.welcomeSubtitle,
                style: Theme.of(context).textTheme.bodyLarge,
                textAlign: TextAlign.center,
              ).animate().fadeIn(delay: 400.ms),
              const SizedBox(height: 48),
              Wrap(
                alignment: WrapAlignment.center,
                spacing: 16,
                runSpacing: 16,
                children: [
                  FilledButton.tonal(
                    onPressed: () => context.go('/showcase/ui'),
                    child: Text(l10n.uiComponents),
                  ),
                  FilledButton.tonal(
                    onPressed: () => context.go('/showcase/skeletons'),
                    child: Text(l10n.loadingSkeletons),
                  ),
                  FilledButton.tonal(
                    onPressed: () => context.go('/showcase/errors'),
                    child: Text(l10n.errorHandling),
                  ),
                  FilledButton.tonal(
                    onPressed: () => context.go('/showcase/upload'),
                    child: Text(l10n.fileUpload),
                  ),
                  FilledButton.tonal(
                    onPressed: () => context.go('/showcase/language'),
                    child: Text(l10n.language),
                  ),
                  FilledButton.tonal(
                    onPressed: () => context.go('/forms'),
                    child: Text(l10n.forms),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(24.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        l10n.features,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      const SizedBox(height: 16),
                      _FeatureItem(
                        icon: Icons.palette,
                        title: l10n.material3Design,
                        description: l10n.material3Description,
                      ),
                      _FeatureItem(
                        icon: Icons.color_lens,
                        title: l10n.dynamicTheming,
                        description: l10n.dynamicThemingDescription,
                      ),
                      _FeatureItem(
                        icon: Icons.devices,
                        title: l10n.responsiveLayout,
                        description: l10n.responsiveLayoutDescription,
                      ),
                      _FeatureItem(
                        icon: Icons.security,
                        title: l10n.authenticationReady,
                        description: l10n.authenticationDescription,
                      ),
                      if (!authState.isAuthenticated)
                        Padding(
                          padding: const EdgeInsets.only(top: 16.0),
                          child: Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              color: Theme.of(context).colorScheme.primaryContainer.withValues(alpha: 0.3),
                              borderRadius: BorderRadius.circular(8),
                              border: Border.all(
                                color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.3),
                              ),
                            ),
                            child: Row(
                              children: [
                                Icon(
                                  Icons.info_outline,
                                  color: Theme.of(context).colorScheme.primary,
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        l10n.guestModeInHome,
                                        style: Theme.of(context).textTheme.titleSmall?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: Theme.of(context).colorScheme.primary,
                                        ),
                                      ),
                                      Text(
                                        l10n.loginToAccessPersonalization,
                                        style: Theme.of(context).textTheme.bodySmall,
                                      ),
                                    ],
                                  ),
                                ),
                                TextButton(
                                  onPressed: () => context.go('/login'),
                                  child: Text(l10n.login),
                                ),
                              ],
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ).animate().slideY(
                begin: 0.1,
                end: 0,
                delay: 600.ms,
                duration: 400.ms,
                curve: Curves.easeOutCubic,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _FeatureItem extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;

  const _FeatureItem({
    required this.icon,
    required this.title,
    required this.description,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            icon,
            size: 24,
            color: Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}