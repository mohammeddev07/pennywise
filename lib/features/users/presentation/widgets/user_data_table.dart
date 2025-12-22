import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../../core/models/user.dart';
import '../../../../l10n/app_localizations.dart';

class UserDataTable extends StatelessWidget {
  final List<User> users;
  final Function(User) onUserTap;
  final Function(User)? onDeleteUser;
  final bool showActions;
  
  const UserDataTable({
    super.key,
    required this.users,
    required this.onUserTap,
    this.onDeleteUser,
    this.showActions = true,
  });

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat.yMMMd();
    
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 600) {
          // Mobile view - Cards
          return ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: users.length,
            itemBuilder: (context, index) {
              final user = users[index];
              return Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  leading: CircleAvatar(
                    backgroundImage: user.avatarUrl != null 
                        ? NetworkImage(user.avatarUrl!)
                        : null,
                    child: user.avatarUrl == null 
                        ? Text(user.name.substring(0, 1).toUpperCase())
                        : null,
                  ),
                  title: Text(user.name),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(user.email),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Chip(
                            label: Text(user.role),
                            visualDensity: VisualDensity.compact,
                          ),
                          const SizedBox(width: 8),
                          if (user.isActive)
                            const Icon(Icons.check_circle, 
                                     color: Colors.green, size: 16)
                          else
                            const Icon(Icons.cancel, 
                                     color: Colors.red, size: 16),
                        ],
                      ),
                    ],
                  ),
                  trailing: showActions ? IconButton(
                    icon: const Icon(Icons.more_vert),
                    onPressed: () => _showUserOptions(context, user),
                  ) : GestureDetector(
                    onTap: () => onUserTap(user),
                    child: const Icon(Icons.chevron_right),
                  ),
                  onTap: () => onUserTap(user),
                ),
              );
            },
          );
        } else {
          // Desktop view - Data table
          return SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: SingleChildScrollView(
              child: DataTable(
                columns: [
                  DataColumn(label: Text(AppLocalizations.of(context).name)),
                  DataColumn(label: Text(AppLocalizations.of(context).email)),
                  DataColumn(label: Text(AppLocalizations.of(context).role)),
                  DataColumn(label: Text(AppLocalizations.of(context).department)),
                  DataColumn(label: Text(AppLocalizations.of(context).status)),
                  DataColumn(label: Text(AppLocalizations.of(context).created)),
                  if (showActions) DataColumn(label: Text(AppLocalizations.of(context).actions)),
                ],
                rows: users.map((user) {
                  return DataRow(
                    cells: [
                      DataCell(
                        Row(
                          children: [
                            CircleAvatar(
                              radius: 16,
                              backgroundImage: user.avatarUrl != null 
                                  ? NetworkImage(user.avatarUrl!)
                                  : null,
                              child: user.avatarUrl == null 
                                  ? Text(user.name.substring(0, 1).toUpperCase())
                                  : null,
                            ),
                            const SizedBox(width: 8),
                            Text(user.name),
                          ],
                        ),
                        onTap: () => onUserTap(user),
                      ),
                      DataCell(Text(user.email)),
                      DataCell(
                        Chip(
                          label: Text(user.role),
                          visualDensity: VisualDensity.compact,
                        ),
                      ),
                      DataCell(Text(user.department ?? '-')),
                      DataCell(
                        Row(
                          children: [
                            Icon(
                              user.isActive ? Icons.check_circle : Icons.cancel,
                              color: user.isActive ? Colors.green : Colors.red,
                              size: 16,
                            ),
                            const SizedBox(width: 4),
                            Text(user.isActive ? AppLocalizations.of(context).active : AppLocalizations.of(context).inactive),
                          ],
                        ),
                      ),
                      DataCell(Text(dateFormat.format(user.createdAt))),
                      if (showActions) DataCell(
                        Row(
                          children: [
                            IconButton(
                              icon: const Icon(Icons.edit_outlined),
                              onPressed: () => onUserTap(user),
                              tooltip: AppLocalizations.of(context).edit,
                            ),
                            if (onDeleteUser != null)
                              IconButton(
                                icon: const Icon(Icons.delete_outline),
                                onPressed: () => onDeleteUser!(user),
                                tooltip: AppLocalizations.of(context).delete,
                              ),
                          ],
                        ),
                      ),
                    ],
                  );
                }).toList(),
              ),
            ),
          );
        }
      },
    );
  }
  
  void _showUserOptions(BuildContext context, User user) {
    showModalBottomSheet(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.visibility_outlined),
              title: Text(AppLocalizations.of(context).viewDetails),
              onTap: () {
                Navigator.pop(context);
                onUserTap(user);
              },
            ),
            if (showActions) ...[
              ListTile(
                leading: const Icon(Icons.edit_outlined),
                title: Text(AppLocalizations.of(context).editUser),
                onTap: () {
                  Navigator.pop(context);
                  onUserTap(user);
                },
              ),
              if (onDeleteUser != null)
                ListTile(
                  leading: const Icon(Icons.delete_outline, color: Colors.red),
                  title: Text(AppLocalizations.of(context).deleteUser, 
                    style: const TextStyle(color: Colors.red),
                  ),
                  onTap: () {
                    Navigator.pop(context);
                    onDeleteUser!(user);
                  },
                ),
            ] else
              ListTile(
                leading: const Icon(Icons.login),
                title: Text(AppLocalizations.of(context).loginToEditUsers),
                onTap: () {
                  Navigator.pop(context);
                },
              ),
          ],
        ),
      ),
    );
  }
}