import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/models/user.dart';
import '../providers/users_provider.dart';
import '../../../../l10n/app_localizations.dart';

class CreateUserDialog extends ConsumerStatefulWidget {
  const CreateUserDialog({super.key});

  @override
  ConsumerState<CreateUserDialog> createState() => _CreateUserDialogState();
}

class _CreateUserDialogState extends ConsumerState<CreateUserDialog> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _phoneController = TextEditingController();
  String _selectedRole = 'Developer';
  String _selectedDepartment = 'Engineering';
  bool _isActive = true;
  bool _isLoading = false;

  final List<String> _roles = [
    'Admin', 'Manager', 'Developer', 'Designer', 
    'Analyst', 'Support', 'Sales', 'Marketing'
  ];
  
  final List<String> _departments = [
    'Engineering', 'Sales', 'Marketing', 'HR', 
    'Finance', 'Operations', 'Support', 'Product'
  ];

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(AppLocalizations.of(context).createNewUser),
      content: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: _nameController,
                decoration: InputDecoration(
                  labelText: AppLocalizations.of(context).fullName,
                  prefixIcon: Icon(Icons.person_outline),
                ),
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return AppLocalizations.of(context).pleaseEnterName;
                  }
                  return null;
                },
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _emailController,
                decoration: InputDecoration(
                  labelText: AppLocalizations.of(context).email,
                  prefixIcon: Icon(Icons.email_outlined),
                ),
                keyboardType: TextInputType.emailAddress,
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return AppLocalizations.of(context).pleaseEnterEmail;
                  }
                  if (!value.contains('@')) {
                    return AppLocalizations.of(context).pleaseEnterValidEmail;
                  }
                  return null;
                },
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _phoneController,
                decoration: InputDecoration(
                  labelText: AppLocalizations.of(context).phoneOptional,
                  prefixIcon: Icon(Icons.phone_outlined),
                ),
                keyboardType: TextInputType.phone,
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: _selectedRole,
                decoration: InputDecoration(
                  labelText: AppLocalizations.of(context).role,
                  prefixIcon: Icon(Icons.badge_outlined),
                ),
                items: _roles.map((role) {
                  return DropdownMenuItem(
                    value: role,
                    child: Text(role),
                  );
                }).toList(),
                onChanged: (value) {
                  setState(() {
                    _selectedRole = value!;
                  });
                },
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: _selectedDepartment,
                decoration: InputDecoration(
                  labelText: AppLocalizations.of(context).department,
                  prefixIcon: Icon(Icons.business_outlined),
                ),
                items: _departments.map((dept) {
                  return DropdownMenuItem(
                    value: dept,
                    child: Text(dept),
                  );
                }).toList(),
                onChanged: (value) {
                  setState(() {
                    _selectedDepartment = value!;
                  });
                },
              ),
              const SizedBox(height: 16),
              SwitchListTile(
                title: Text(AppLocalizations.of(context).active),
                subtitle: Text(AppLocalizations.of(context).userCanAccess),
                value: _isActive,
                onChanged: (value) {
                  setState(() {
                    _isActive = value;
                  });
                },
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isLoading ? null : () => Navigator.of(context).pop(),
          child: Text(AppLocalizations.of(context).cancel),
        ),
        FilledButton(
          onPressed: _isLoading ? null : _createUser,
          child: _isLoading
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : Text(AppLocalizations.of(context).create),
        ),
      ],
    );
  }

  Future<void> _createUser() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isLoading = true;
    });

    try {
      final newUser = User(
        id: 'user_${DateTime.now().millisecondsSinceEpoch}',
        email: _emailController.text,
        name: _nameController.text,
        role: _selectedRole,
        department: _selectedDepartment,
        phone: _phoneController.text.isNotEmpty ? _phoneController.text : null,
        createdAt: DateTime.now(),
        isActive: _isActive,
        lastLogin: null,
      );

      await ref.read(usersProvider.notifier).createUser(newUser);

      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context).userCreatedSuccess(newUser.name)),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context).errorCreatingUser(e.toString())),
            backgroundColor: Theme.of(context).colorScheme.error,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }
}