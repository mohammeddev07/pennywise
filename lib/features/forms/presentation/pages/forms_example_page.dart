import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../shared/widgets/ui_components/ui_components.dart';
import '../../../../l10n/app_localizations.dart';

class FormsExamplePage extends ConsumerStatefulWidget {
  const FormsExamplePage({super.key});

  @override
  ConsumerState<FormsExamplePage> createState() => _FormsExamplePageState();
}

class _FormsExamplePageState extends ConsumerState<FormsExamplePage> {
  final _userFormKey = GlobalKey<FormState>();
  final _paymentFormKey = GlobalKey<FormState>();
  final _feedbackFormKey = GlobalKey<FormState>();
  
  // User Registration Form Controllers
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  
  // Payment Form Controllers
  final _cardNumberController = TextEditingController();
  final _cardHolderController = TextEditingController();
  final _expiryController = TextEditingController();
  final _cvvController = TextEditingController();
  
  // Feedback Form Controllers
  final _subjectController = TextEditingController();
  final _messageController = TextEditingController();
  
  // Form States
  bool _agreeToTerms = false;
  String _selectedGender = 'Other';
  double _satisfactionRating = 3;
  final List<String> _selectedInterests = [];
  
  final List<String> _genderOptions = ['Male', 'Female', 'Other'];
  final List<String> _interestOptions = [
    'Technology', 'Sports', 'Music', 'Travel', 
    'Food', 'Art', 'Reading', 'Gaming'
  ];
  
  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    _cardNumberController.dispose();
    _cardHolderController.dispose();
    _expiryController.dispose();
    _cvvController.dispose();
    _subjectController.dispose();
    _messageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context).formsExample),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildFormSection(
              AppLocalizations.of(context).userRegistrationForm,
              AppLocalizations.of(context).completeFormDescription,
              _buildUserRegistrationForm(),
            ),
            const SizedBox(height: 32),
            _buildFormSection(
              AppLocalizations.of(context).paymentForm,
              AppLocalizations.of(context).creditCardFormDescription,
              _buildPaymentForm(),
            ),
            const SizedBox(height: 32),
            _buildFormSection(
              AppLocalizations.of(context).feedbackForm,
              AppLocalizations.of(context).ratingFormDescription,
              _buildFeedbackForm(),
            ),
          ],
        ),
      ),
    );
  }
  
  Widget _buildFormSection(String title, String subtitle, Widget form) {
    return CustomCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 4),
          Text(
            subtitle,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 24),
          form,
        ],
      ),
    );
  }
  
  Widget _buildUserRegistrationForm() {
    return Form(
      key: _userFormKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CustomTextField(
            controller: _nameController,
            label: AppLocalizations.of(context).fullName,
            prefixIcon: const Icon(Icons.person_outline),
            validator: (value) {
              if (value == null || value.isEmpty) {
                return AppLocalizations.of(context).pleaseEnterName;
              }
              if (value.length < 3) {
                return AppLocalizations.of(context).nameMustBe3Chars;
              }
              return null;
            },
          ),
          const SizedBox(height: 16),
          EmailTextField(
            controller: _emailController,
          ),
          const SizedBox(height: 16),
          CustomTextField(
            controller: _phoneController,
            label: AppLocalizations.of(context).phoneNumber,
            prefixIcon: const Icon(Icons.phone_outlined),
            keyboardType: TextInputType.phone,
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
              _PhoneNumberFormatter(),
            ],
            validator: (value) {
              if (value == null || value.isEmpty) {
                return AppLocalizations.of(context).pleaseEnterPhoneNumber;
              }
              if (value.replaceAll(RegExp(r'[^0-9]'), '').length < 10) {
                return AppLocalizations.of(context).pleaseEnterValidPhoneNumber;
              }
              return null;
            },
          ),
          const SizedBox(height: 16),
          Text(
            AppLocalizations.of(context).gender,
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 8),
          Row(
            children: _genderOptions.map((gender) {
              return Expanded(
                child: RadioListTile<String>(
                  title: Text(gender),
                  value: gender,
                  groupValue: _selectedGender,
                  onChanged: (value) {
                    setState(() {
                      _selectedGender = value!;
                    });
                  },
                  contentPadding: EdgeInsets.zero,
                  visualDensity: VisualDensity.compact,
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 16),
          PasswordTextField(
            controller: _passwordController,
            validator: (value) {
              if (value == null || value.isEmpty) {
                return AppLocalizations.of(context).pleaseEnterPassword;
              }
              if (value.length < 8) {
                return AppLocalizations.of(context).passwordMustBe8Chars;
              }
              if (!RegExp(r'^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)').hasMatch(value)) {
                return AppLocalizations.of(context).passwordComplexityRequired;
              }
              return null;
            },
          ),
          const SizedBox(height: 16),
          PasswordTextField(
            controller: _confirmPasswordController,
            label: AppLocalizations.of(context).confirmPassword,
            validator: (value) {
              if (value == null || value.isEmpty) {
                return AppLocalizations.of(context).pleaseConfirmPassword;
              }
              if (value != _passwordController.text) {
                return AppLocalizations.of(context).passwordsDoNotMatch;
              }
              return null;
            },
          ),
          const SizedBox(height: 16),
          Text(
            AppLocalizations.of(context).interests,
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: _interestOptions.map((interest) {
              final isSelected = _selectedInterests.contains(interest);
              return CustomChip(
                label: interest,
                variant: ChipVariant.filter,
                selected: isSelected,
                onPressed: () {
                  setState(() {
                    if (isSelected) {
                      _selectedInterests.remove(interest);
                    } else {
                      _selectedInterests.add(interest);
                    }
                  });
                },
              );
            }).toList(),
          ),
          const SizedBox(height: 16),
          CheckboxListTile(
            title: Text(AppLocalizations.of(context).agreeToTerms),
            value: _agreeToTerms,
            onChanged: (value) {
              setState(() {
                _agreeToTerms = value!;
              });
            },
            contentPadding: EdgeInsets.zero,
            controlAffinity: ListTileControlAffinity.leading,
          ),
          const SizedBox(height: 24),
          CustomButton(
            label: AppLocalizations.of(context).register,
            onPressed: () => _validateUserForm(),
            fullWidth: true,
          ),
        ],
      ),
    );
  }
  
  Widget _buildPaymentForm() {
    return Form(
      key: _paymentFormKey,
      child: Column(
        children: [
          CustomTextField(
            controller: _cardNumberController,
            label: AppLocalizations.of(context).cardNumber,
            prefixIcon: const Icon(Icons.credit_card),
            keyboardType: TextInputType.number,
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
              _CardNumberFormatter(),
            ],
            validator: (value) {
              if (value == null || value.isEmpty) {
                return AppLocalizations.of(context).pleaseEnterCardNumber;
              }
              final digitsOnly = value.replaceAll(' ', '');
              if (digitsOnly.length != 16) {
                return AppLocalizations.of(context).cardNumberMustBe16Digits;
              }
              return null;
            },
          ),
          const SizedBox(height: 16),
          CustomTextField(
            controller: _cardHolderController,
            label: AppLocalizations.of(context).cardHolderName,
            prefixIcon: const Icon(Icons.person_outline),
            textInputAction: TextInputAction.next,
            validator: (value) {
              if (value == null || value.isEmpty) {
                return AppLocalizations.of(context).pleaseEnterCardHolderName;
              }
              return null;
            },
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: CustomTextField(
                  controller: _expiryController,
                  label: AppLocalizations.of(context).expiryDate,
                  hint: AppLocalizations.of(context).mmYy,
                  prefixIcon: const Icon(Icons.calendar_today),
                  keyboardType: TextInputType.number,
                  inputFormatters: [
                    FilteringTextInputFormatter.digitsOnly,
                    _ExpiryDateFormatter(),
                  ],
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return AppLocalizations.of(context).required;
                    }
                    if (!RegExp(r'^(0[1-9]|1[0-2])\/\d{2}$').hasMatch(value)) {
                      return AppLocalizations.of(context).invalid;
                    }
                    return null;
                  },
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: CustomTextField(
                  controller: _cvvController,
                  label: AppLocalizations.of(context).cvv,
                  prefixIcon: const Icon(Icons.lock_outline),
                  keyboardType: TextInputType.number,
                  obscureText: true,
                  maxLength: 3,
                  showCounter: false,
                  inputFormatters: [
                    FilteringTextInputFormatter.digitsOnly,
                  ],
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return AppLocalizations.of(context).required;
                    }
                    if (value.length != 3) {
                      return AppLocalizations.of(context).invalid;
                    }
                    return null;
                  },
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          CustomButton(
            label: AppLocalizations.of(context).processPayment,
            onPressed: () => _validatePaymentForm(),
            fullWidth: true,
            icon: Icons.lock,
          ),
        ],
      ),
    );
  }
  
  Widget _buildFeedbackForm() {
    return Form(
      key: _feedbackFormKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CustomTextField(
            controller: _subjectController,
            label: AppLocalizations.of(context).subject,
            prefixIcon: const Icon(Icons.subject),
            validator: (value) {
              if (value == null || value.isEmpty) {
                return AppLocalizations.of(context).pleaseEnterSubject;
              }
              return null;
            },
          ),
          const SizedBox(height: 16),
          Text(
            AppLocalizations.of(context).satisfactionRating,
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              const Icon(Icons.sentiment_very_dissatisfied, color: Colors.red),
              Expanded(
                child: Slider(
                  value: _satisfactionRating,
                  min: 1,
                  max: 5,
                  divisions: 4,
                  label: _satisfactionRating.round().toString(),
                  onChanged: (value) {
                    setState(() {
                      _satisfactionRating = value;
                    });
                  },
                ),
              ),
              const Icon(Icons.sentiment_very_satisfied, color: Colors.green),
            ],
          ),
          const SizedBox(height: 16),
          CustomTextField(
            controller: _messageController,
            label: AppLocalizations.of(context).yourFeedback,
            hint: AppLocalizations.of(context).tellUsWhatYouThink,
            maxLines: 5,
            maxLength: 500,
            validator: (value) {
              if (value == null || value.isEmpty) {
                return AppLocalizations.of(context).pleaseEnterFeedback;
              }
              if (value.length < 10) {
                return AppLocalizations.of(context).feedbackMustBe10Chars;
              }
              return null;
            },
          ),
          const SizedBox(height: 24),
          CustomButton(
            label: AppLocalizations.of(context).submitFeedback,
            onPressed: () => _validateFeedbackForm(),
            fullWidth: true,
            variant: ButtonVariant.secondary,
          ),
        ],
      ),
    );
  }
  
  void _validateUserForm() {
    if (!_userFormKey.currentState!.validate()) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Please fix the errors in the form')),
      );
      return;
    }
    
    if (!_agreeToTerms) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Please agree to the terms and conditions')),
      );
      return;
    }
    
    if (_selectedInterests.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Please select at least one interest')),
      );
      return;
    }
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Registration successful!'),
        backgroundColor: Colors.green,
      ),
    );
  }
  
  void _validatePaymentForm() {
    if (!_paymentFormKey.currentState!.validate()) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Please fix the errors in the form')),
      );
      return;
    }
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Payment processed successfully!'),
        backgroundColor: Colors.green,
      ),
    );
  }
  
  void _validateFeedbackForm() {
    if (!_feedbackFormKey.currentState!.validate()) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Please fix the errors in the form')),
      );
      return;
    }
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Thank you for your feedback! Rating: ${_satisfactionRating.round()}/5'),
        backgroundColor: Colors.green,
      ),
    );
  }
}

// Custom Text Input Formatters
class _PhoneNumberFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    final text = newValue.text;
    final buffer = StringBuffer();
    int selectionIndex = newValue.selection.end;
    int newSelectionIndex = 0;
    
    for (int i = 0; i < text.length; i++) {
      if (i == 3 || i == 6) {
        buffer.write('-');
        if (i <= selectionIndex) newSelectionIndex++;
      }
      buffer.write(text[i]);
      if (i < selectionIndex) newSelectionIndex++;
    }
    
    return TextEditingValue(
      text: buffer.toString(),
      selection: TextSelection.collapsed(offset: newSelectionIndex),
    );
  }
}

class _CardNumberFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    final text = newValue.text.replaceAll(' ', '');
    final buffer = StringBuffer();
    int selectionIndex = newValue.selection.end;
    int newSelectionIndex = 0;
    
    for (int i = 0; i < text.length; i++) {
      if (i > 0 && i % 4 == 0) {
        buffer.write(' ');
        if (i <= selectionIndex) newSelectionIndex++;
      }
      buffer.write(text[i]);
      if (i < selectionIndex) newSelectionIndex++;
    }
    
    return TextEditingValue(
      text: buffer.toString(),
      selection: TextSelection.collapsed(offset: newSelectionIndex),
    );
  }
}

class _ExpiryDateFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    final text = newValue.text.replaceAll('/', '');
    if (text.length > 4) return oldValue;
    
    final buffer = StringBuffer();
    for (int i = 0; i < text.length; i++) {
      if (i == 2) buffer.write('/');
      buffer.write(text[i]);
    }
    
    return TextEditingValue(
      text: buffer.toString(),
      selection: TextSelection.collapsed(offset: buffer.length),
    );
  }
}