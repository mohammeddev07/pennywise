import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/users_provider.dart';
import '../widgets/user_data_table.dart';
import '../widgets/create_user_dialog.dart';
import '../../../../shared/widgets/search_bar.dart';
import '../../../../shared/widgets/skeletons/page_skeletons.dart';
import '../../../../shared/widgets/guest_mode_banner.dart';
import '../../../../features/auth/presentation/providers/auth_provider.dart';
import '../../../../l10n/app_localizations.dart';

class UsersPage extends ConsumerStatefulWidget {
  const UsersPage({super.key});

  @override
  ConsumerState<UsersPage> createState() => _UsersPageState();
}

class _UsersPageState extends ConsumerState<UsersPage> {
  final _searchController = TextEditingController();
  
  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final usersState = ref.watch(usersProvider);
    final searchQuery = ref.watch(searchQueryProvider);
    final authState = ref.watch(authProvider);
    final isAuthenticated = authState.isAuthenticated;
    
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context).users),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(usersProvider),
          ),
        ],
      ),
      body: Column(
        children: [
          // Guest mode banner
          if (!isAuthenticated) GuestModeBanner(
            customMessage: AppLocalizations.of(context).userManagementLimited,
            loginBenefits: [
              AppLocalizations.of(context).addEditDeleteUsers,
              AppLocalizations.of(context).accessDetailedProfiles,
              AppLocalizations.of(context).exportUserData,
              AppLocalizations.of(context).manageUserPermissions,
            ],
          ),
          
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Row(
              children: [
                Expanded(
                  child: AppSearchBar(
                    controller: _searchController,
                    hintText: AppLocalizations.of(context).searchUsers,
                    onChanged: (value) {
                      ref.read(searchQueryProvider.notifier).state = value;
                    },
                  ),
                ),
                const SizedBox(width: 16),
                if (isAuthenticated)
                  FilledButton.icon(
                    onPressed: () => _showCreateUserDialog(context),
                    icon: const Icon(Icons.add),
                    label: Text(AppLocalizations.of(context).addUser),
                  )
                else
                  FilledButton.icon(
                    onPressed: () => context.go('/login'),
                    icon: const Icon(Icons.login),
                    label: Text(AppLocalizations.of(context).loginToAdd),
                    style: FilledButton.styleFrom(
                      backgroundColor: Theme.of(context).colorScheme.surfaceVariant,
                      foregroundColor: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
              ],
            ),
          ),
          Expanded(
            child: usersState.when(
              data: (users) {
                if (users.isEmpty) {
                  return Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          searchQuery.isNotEmpty 
                              ? Icons.search_off 
                              : Icons.people_outline,
                          size: 64,
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          searchQuery.isNotEmpty 
                              ? AppLocalizations.of(context).noUsersFound(searchQuery)
                              : AppLocalizations.of(context).noUsersYet,
                          style: Theme.of(context).textTheme.bodyLarge,
                        ),
                      ],
                    ),
                  );
                }
                
                return UserDataTable(
                  users: users,
                  onUserTap: (user) {
                    context.go('/users/${user.id}');
                  },
                  onDeleteUser: isAuthenticated ? (user) async {
                    final confirmed = await _showDeleteConfirmation(context, user.name);
                    if (confirmed) {
                      try {
                        await ref.read(usersProvider.notifier).deleteUser(user.id);
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(AppLocalizations.of(context).userDeleted(user.name)),
                              action: SnackBarAction(
                                label: AppLocalizations.of(context).undo,
                                onPressed: () {
                                  // In a real app, you'd implement undo functionality
                                },
                              ),
                            ),
                          );
                        }
                      } catch (e) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(AppLocalizations.of(context).errorDeletingUser(e.toString())),
                              backgroundColor: Theme.of(context).colorScheme.error,
                            ),
                          );
                        }
                      }
                    }
                  } : null, // Disable delete for guests
                  showActions: isAuthenticated, // Hide action buttons for guests
                );
              },
              loading: () => const UserListSkeleton(),
              error: (error, stack) => Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.error_outline, size: 64, color: Colors.red),
                    const SizedBox(height: 16),
                    Text('${AppLocalizations.of(context).error}: $error'),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: () => ref.invalidate(usersProvider),
                      child: Text(AppLocalizations.of(context).retry),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
  
  void _showCreateUserDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => const CreateUserDialog(),
    );
  }
  
  Future<bool> _showDeleteConfirmation(BuildContext context, String userName) async {
    return await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(AppLocalizations.of(context).deleteUser),
        content: Text(AppLocalizations.of(context).confirmDeleteUser(userName)),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(AppLocalizations.of(context).cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            child: Text(AppLocalizations.of(context).delete),
          ),
        ],
      ),
    ) ?? false;
  }
}