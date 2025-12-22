import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class GuestModeBanner extends StatelessWidget {
  final String? customMessage;
  final List<String>? loginBenefits;
  final bool showActions;
  
  const GuestModeBanner({
    super.key,
    this.customMessage,
    this.loginBenefits,
    this.showActions = true,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.primaryContainer,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: theme.colorScheme.primary.withValues(alpha: 0.3),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                Icons.person_outline,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(width: 8),
              Text(
                'Guest Mode',
                style: theme.textTheme.titleMedium?.copyWith(
                  color: theme.colorScheme.primary,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            customMessage ?? 'You\'re browsing as a guest with limited features.',
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onPrimaryContainer,
            ),
          ),
          if (loginBenefits != null && loginBenefits!.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(
              'Login to unlock:',
              style: theme.textTheme.labelLarge?.copyWith(
                color: theme.colorScheme.primary,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 4),
            ...loginBenefits!.map((benefit) => Padding(
              padding: const EdgeInsets.only(left: 16, top: 2),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '• ',
                    style: TextStyle(
                      color: theme.colorScheme.primary,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Expanded(
                    child: Text(
                      benefit,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onPrimaryContainer,
                      ),
                    ),
                  ),
                ],
              ),
            )),
          ],
          if (showActions) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                FilledButton.icon(
                  onPressed: () => context.go('/login'),
                  icon: const Icon(Icons.login, size: 18),
                  label: const Text('Login'),
                  style: FilledButton.styleFrom(
                    backgroundColor: theme.colorScheme.primary,
                    foregroundColor: theme.colorScheme.onPrimary,
                  ),
                ),
                const SizedBox(width: 8),
                TextButton(
                  onPressed: () => context.go('/register'),
                  child: const Text('Create Account'),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class GuestModeChip extends StatelessWidget {
  final VoidCallback? onTap;
  
  const GuestModeChip({super.key, this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: theme.colorScheme.primaryContainer,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: theme.colorScheme.primary.withValues(alpha: 0.3),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.person_outline,
              size: 14,
              color: theme.colorScheme.primary,
            ),
            const SizedBox(width: 4),
            Text(
              'Guest',
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.primary,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}