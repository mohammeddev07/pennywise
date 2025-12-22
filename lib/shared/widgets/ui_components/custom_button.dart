import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

enum ButtonVariant { primary, secondary, tertiary, danger }
enum ButtonSize { small, medium, large }

class CustomButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final ButtonVariant variant;
  final ButtonSize size;
  final IconData? icon;
  final bool isLoading;
  final bool fullWidth;
  
  const CustomButton({
    super.key,
    required this.label,
    this.onPressed,
    this.variant = ButtonVariant.primary,
    this.size = ButtonSize.medium,
    this.icon,
    this.isLoading = false,
    this.fullWidth = false,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    
    final buttonStyle = _getButtonStyle(theme);
    final textStyle = _getTextStyle(theme);
    
    Widget buttonChild = Row(
      mainAxisSize: fullWidth ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (isLoading)
          SizedBox(
            width: _getIconSize(),
            height: _getIconSize(),
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation<Color>(
                _getLoadingColor(theme),
              ),
            ),
          )
        else if (icon != null) ...[
          Icon(icon, size: _getIconSize()),
          const SizedBox(width: 8),
        ],
        if (!isLoading)
          Text(label, style: textStyle),
      ],
    );
    
    Widget button = switch (variant) {
      ButtonVariant.primary => FilledButton(
          onPressed: isLoading ? null : onPressed,
          style: buttonStyle,
          child: buttonChild,
        ),
      ButtonVariant.secondary => FilledButton.tonal(
          onPressed: isLoading ? null : onPressed,
          style: buttonStyle,
          child: buttonChild,
        ),
      ButtonVariant.tertiary => OutlinedButton(
          onPressed: isLoading ? null : onPressed,
          style: buttonStyle,
          child: buttonChild,
        ),
      ButtonVariant.danger => FilledButton(
          onPressed: isLoading ? null : onPressed,
          style: buttonStyle?.copyWith(
            backgroundColor: WidgetStateProperty.resolveWith((states) {
              if (states.contains(WidgetState.disabled)) {
                return theme.colorScheme.error.withValues(alpha: 0.38);
              }
              return theme.colorScheme.error;
            }),
            foregroundColor: WidgetStateProperty.all(
              theme.colorScheme.onError,
            ),
          ),
          child: buttonChild,
        ),
    };
    
    if (fullWidth) {
      button = SizedBox(
        width: double.infinity,
        child: button,
      );
    }
    
    return button.animate()
      .scale(
        begin: const Offset(0.95, 0.95),
        end: const Offset(1.0, 1.0),
        duration: 200.ms,
        curve: Curves.easeOut,
      );
  }
  
  EdgeInsetsGeometry _getPadding() {
    return switch (size) {
      ButtonSize.small => const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      ButtonSize.medium => const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      ButtonSize.large => const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
    };
  }
  
  double _getIconSize() {
    return switch (size) {
      ButtonSize.small => 16,
      ButtonSize.medium => 20,
      ButtonSize.large => 24,
    };
  }
  
  TextStyle? _getTextStyle(ThemeData theme) {
    return switch (size) {
      ButtonSize.small => theme.textTheme.bodySmall,
      ButtonSize.medium => theme.textTheme.bodyMedium,
      ButtonSize.large => theme.textTheme.bodyLarge,
    };
  }
  
  ButtonStyle? _getButtonStyle(ThemeData theme) {
    return ElevatedButton.styleFrom(
      padding: _getPadding(),
      minimumSize: Size.zero,
      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
    );
  }
  
  Color _getLoadingColor(ThemeData theme) {
    return switch (variant) {
      ButtonVariant.primary => theme.colorScheme.onPrimary,
      ButtonVariant.secondary => theme.colorScheme.onSecondaryContainer,
      ButtonVariant.tertiary => theme.colorScheme.primary,
      ButtonVariant.danger => theme.colorScheme.onError,
    };
  }
}