import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

enum ChipVariant { filled, outlined, suggestion, filter }
enum ChipSize { small, medium, large }

class CustomChip extends StatelessWidget {
  final String label;
  final Widget? avatar;
  final Widget? deleteIcon;
  final VoidCallback? onDeleted;
  final VoidCallback? onPressed;
  final bool selected;
  final ChipVariant variant;
  final ChipSize size;
  final Color? color;
  final bool animate;
  
  const CustomChip({
    super.key,
    required this.label,
    this.avatar,
    this.deleteIcon,
    this.onDeleted,
    this.onPressed,
    this.selected = false,
    this.variant = ChipVariant.filled,
    this.size = ChipSize.medium,
    this.color,
    this.animate = true,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    
    Widget chip = switch (variant) {
      ChipVariant.filled => ActionChip(
          label: Text(label),
          avatar: avatar,
          onPressed: onPressed,
          backgroundColor: selected 
              ? color ?? theme.colorScheme.primary
              : color ?? theme.colorScheme.surfaceContainerHighest,
          labelStyle: TextStyle(
            fontSize: _getFontSize(),
            color: selected 
                ? theme.colorScheme.onPrimary
                : null,
          ),
          padding: _getPadding(),
          visualDensity: _getVisualDensity(),
        ),
      ChipVariant.outlined => ActionChip(
          label: Text(label),
          avatar: avatar,
          onPressed: onPressed,
          backgroundColor: Colors.transparent,
          side: BorderSide(
            color: selected
                ? color ?? theme.colorScheme.primary
                : theme.colorScheme.outline,
          ),
          labelStyle: TextStyle(
            fontSize: _getFontSize(),
            color: selected
                ? color ?? theme.colorScheme.primary
                : null,
          ),
          padding: _getPadding(),
          visualDensity: _getVisualDensity(),
        ),
      ChipVariant.suggestion => RawChip(
          label: Text(label),
          avatar: avatar,
          onPressed: onPressed,
          backgroundColor: theme.colorScheme.secondaryContainer,
          labelStyle: TextStyle(
            fontSize: _getFontSize(),
            color: theme.colorScheme.onSecondaryContainer,
          ),
          padding: _getPadding(),
          visualDensity: _getVisualDensity(),
        ),
      ChipVariant.filter => FilterChip(
          label: Text(label),
          avatar: avatar,
          selected: selected,
          onSelected: onPressed != null ? (value) => onPressed!() : null,
          deleteIcon: deleteIcon,
          onDeleted: onDeleted,
          backgroundColor: theme.colorScheme.surface,
          selectedColor: color ?? theme.colorScheme.primaryContainer,
          checkmarkColor: theme.colorScheme.onPrimaryContainer,
          labelStyle: TextStyle(
            fontSize: _getFontSize(),
          ),
          padding: _getPadding(),
          visualDensity: _getVisualDensity(),
        ),
    };
    
    if (animate) {
      return chip.animate()
        .scale(
          begin: const Offset(0.8, 0.8),
          end: const Offset(1.0, 1.0),
          duration: 200.ms,
          curve: Curves.easeOut,
        );
    }
    
    return chip;
  }
  
  EdgeInsetsGeometry _getPadding() {
    return switch (size) {
      ChipSize.small => const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      ChipSize.medium => const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      ChipSize.large => const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
    };
  }
  
  double _getFontSize() {
    return switch (size) {
      ChipSize.small => 12,
      ChipSize.medium => 14,
      ChipSize.large => 16,
    };
  }
  
  VisualDensity _getVisualDensity() {
    return switch (size) {
      ChipSize.small => VisualDensity.compact,
      ChipSize.medium => VisualDensity.comfortable,
      ChipSize.large => VisualDensity.standard,
    };
  }
}

class ChipGroup extends StatelessWidget {
  final List<CustomChip> chips;
  final double spacing;
  final double runSpacing;
  final WrapAlignment alignment;
  final WrapAlignment runAlignment;
  
  const ChipGroup({
    super.key,
    required this.chips,
    this.spacing = 8.0,
    this.runSpacing = 8.0,
    this.alignment = WrapAlignment.start,
    this.runAlignment = WrapAlignment.start,
  });

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: spacing,
      runSpacing: runSpacing,
      alignment: alignment,
      runAlignment: runAlignment,
      children: chips,
    );
  }
}