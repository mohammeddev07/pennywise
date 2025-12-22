import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../providers/users_provider.dart';
import '../widgets/edit_user_form.dart';

class UserDetailPage extends ConsumerWidget {
  final String userId;
  
  const UserDetailPage({
    super.key,
    required this.userId,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userAsync = ref.watch(selectedUserProvider(userId));
    
    return Scaffold(
      appBar: AppBar(
        title: const Text('User Details'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(selectedUserProvider(userId)),
          ),
        ],
      ),
      body: userAsync.when(
        data: (user) {
          if (user == null) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.person_off, size: 64),
                  const SizedBox(height: 16),
                  const Text('User not found'),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: () => context.go('/users'),
                    child: const Text('Back to Users'),
                  ),
                ],
              ),
            );
          }
          
          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 800),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _UserHeader(user: user),
                    const SizedBox(height: 24),
                    _UserDetailsCard(user: user),
                    const SizedBox(height: 16),
                    _UserActivityCard(user: user),
                    const SizedBox(height: 24),
                    _UserActions(user: user),
                  ],
                ),
              ),
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 64, color: Colors.red),
              const SizedBox(height: 16),
              Text('Error: $error'),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => ref.invalidate(selectedUserProvider(userId)),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _UserHeader extends StatelessWidget {
  final dynamic user;
  
  const _UserHeader({required this.user});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Row(
          children: [
            CircleAvatar(
              radius: 48,
              backgroundImage: user.avatarUrl != null 
                  ? NetworkImage(user.avatarUrl!)
                  : null,
              child: user.avatarUrl == null 
                  ? Text(
                      user.name.substring(0, 1).toUpperCase(),
                      style: Theme.of(context).textTheme.headlineMedium,
                    )
                  : null,
            ),
            const SizedBox(width: 24),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    user.name,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    user.email,
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Chip(
                        label: Text(user.role),
                        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
                      ),
                      const SizedBox(width: 8),
                      Chip(
                        avatar: Icon(
                          user.isActive ? Icons.check_circle : Icons.cancel,
                          color: user.isActive ? Colors.green : Colors.red,
                          size: 16,
                        ),
                        label: Text(user.isActive ? 'Active' : 'Inactive'),
                        backgroundColor: user.isActive 
                            ? Colors.green.withValues(alpha: 0.1)
                            : Colors.red.withValues(alpha: 0.1),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _UserDetailsCard extends StatelessWidget {
  final dynamic user;
  
  const _UserDetailsCard({required this.user});

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat.yMMMd();
    
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Contact Information',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            _DetailRow(
              icon: Icons.email_outlined,
              label: 'Email',
              value: user.email,
            ),
            if (user.phone != null) ...[
              const SizedBox(height: 12),
              _DetailRow(
                icon: Icons.phone_outlined,
                label: 'Phone',
                value: user.phone!,
              ),
            ],
            if (user.department != null) ...[
              const SizedBox(height: 12),
              _DetailRow(
                icon: Icons.business_outlined,
                label: 'Department',
                value: user.department!,
              ),
            ],
            const Divider(height: 32),
            Text(
              'Account Information',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            _DetailRow(
              icon: Icons.badge_outlined,
              label: 'User ID',
              value: user.id,
            ),
            const SizedBox(height: 12),
            _DetailRow(
              icon: Icons.calendar_today_outlined,
              label: 'Created',
              value: dateFormat.format(user.createdAt),
            ),
          ],
        ),
      ),
    );
  }
}

class _UserActivityCard extends StatelessWidget {
  final dynamic user;
  
  const _UserActivityCard({required this.user});

  @override
  Widget build(BuildContext context) {
    final timeFormat = DateFormat.jm();
    final dateFormat = DateFormat.yMMMd();
    
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Activity',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            if (user.lastLogin != null)
              _DetailRow(
                icon: Icons.login,
                label: 'Last Login',
                value: '${dateFormat.format(user.lastLogin!)} at ${timeFormat.format(user.lastLogin!)}',
              )
            else
              Row(
                children: [
                  Icon(
                    Icons.info_outline,
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                  const SizedBox(width: 16),
                  Text(
                    'User has not logged in yet',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}

class _UserActions extends ConsumerWidget {
  final dynamic user;
  
  const _UserActions({required this.user});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        OutlinedButton.icon(
          onPressed: () async {
            final confirmed = await showDialog<bool>(
              context: context,
              builder: (context) => AlertDialog(
                title: const Text('Delete User'),
                content: Text('Are you sure you want to delete ${user.name}?'),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(false),
                    child: const Text('Cancel'),
                  ),
                  FilledButton(
                    onPressed: () => Navigator.of(context).pop(true),
                    style: FilledButton.styleFrom(
                      backgroundColor: Theme.of(context).colorScheme.error,
                    ),
                    child: const Text('Delete'),
                  ),
                ],
              ),
            ) ?? false;
            
            if (confirmed) {
              await ref.read(usersProvider.notifier).deleteUser(user.id);
              if (context.mounted) {
                context.go('/users');
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text('User ${user.name} deleted'),
                  ),
                );
              }
            }
          },
          icon: const Icon(Icons.delete_outline),
          label: const Text('Delete'),
          style: OutlinedButton.styleFrom(
            foregroundColor: Theme.of(context).colorScheme.error,
          ),
        ),
        const SizedBox(width: 16),
        FilledButton.icon(
          onPressed: () => _showEditDialog(context, ref, user),
          icon: const Icon(Icons.edit),
          label: const Text('Edit User'),
        ),
      ],
    );
  }
  
  void _showEditDialog(BuildContext context, WidgetRef ref, dynamic user) {
    showDialog(
      context: context,
      builder: (context) => EditUserForm(user: user),
    );
  }
}

class _DetailRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  
  const _DetailRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(
          icon,
          size: 20,
          color: Theme.of(context).colorScheme.onSurfaceVariant,
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                value,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ],
          ),
        ),
      ],
    );
  }
}