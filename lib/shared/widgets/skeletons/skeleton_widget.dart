import 'package:flutter/material.dart';

class SkeletonWidget extends StatefulWidget {
  final double? width;
  final double? height;
  final double borderRadius;
  final EdgeInsetsGeometry? margin;
  final BoxShape shape;
  
  const SkeletonWidget({
    super.key,
    this.width,
    this.height,
    this.borderRadius = 4,
    this.margin,
    this.shape = BoxShape.rectangle,
  });

  @override
  State<SkeletonWidget> createState() => _SkeletonWidgetState();
}

class _SkeletonWidgetState extends State<SkeletonWidget>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    )..repeat();
    
    _animation = Tween<double>(
      begin: -1.0,
      end: 2.0,
    ).animate(CurvedAnimation(
      parent: _controller,
      curve: Curves.easeInOut,
    ));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    
    return Container(
      width: widget.width,
      height: widget.height,
      margin: widget.margin,
      decoration: BoxDecoration(
        color: isDark 
            ? theme.colorScheme.surfaceContainerHighest
            : theme.colorScheme.surfaceContainerLowest,
        shape: widget.shape,
        borderRadius: widget.shape == BoxShape.rectangle
            ? BorderRadius.circular(widget.borderRadius)
            : null,
      ),
      child: AnimatedBuilder(
        animation: _animation,
        builder: (context, child) {
          return Container(
            decoration: BoxDecoration(
              shape: widget.shape,
              borderRadius: widget.shape == BoxShape.rectangle
                  ? BorderRadius.circular(widget.borderRadius)
                  : null,
              gradient: LinearGradient(
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
                colors: [
                  Colors.transparent,
                  theme.colorScheme.surface.withValues(alpha: 0.3),
                  Colors.transparent,
                ],
                stops: [
                  (_animation.value - 0.3).clamp(0.0, 1.0),
                  _animation.value.clamp(0.0, 1.0),
                  (_animation.value + 0.3).clamp(0.0, 1.0),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

// Skeleton Text Widget
class SkeletonText extends StatelessWidget {
  final double width;
  final double height;
  final int lines;
  final double spacing;
  final bool lastLineWidth;
  
  const SkeletonText({
    super.key,
    this.width = double.infinity,
    this.height = 16,
    this.lines = 1,
    this.spacing = 8,
    this.lastLineWidth = true,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: List.generate(lines, (index) {
        final isLastLine = index == lines - 1;
        return Padding(
          padding: EdgeInsets.only(bottom: isLastLine ? 0 : spacing),
          child: SkeletonWidget(
            width: isLastLine && lastLineWidth ? width * 0.6 : width,
            height: height,
          ),
        );
      }),
    );
  }
}

// Skeleton Avatar Widget
class SkeletonAvatar extends StatelessWidget {
  final double size;
  final BoxShape shape;
  
  const SkeletonAvatar({
    super.key,
    this.size = 40,
    this.shape = BoxShape.circle,
  });

  @override
  Widget build(BuildContext context) {
    return SkeletonWidget(
      width: size,
      height: size,
      shape: shape,
      borderRadius: shape == BoxShape.rectangle ? size * 0.3 : 0,
    );
  }
}

// Skeleton Card Widget
class SkeletonCard extends StatelessWidget {
  final double? width;
  final double height;
  final EdgeInsetsGeometry? margin;
  final bool showAvatar;
  final bool showActions;
  
  const SkeletonCard({
    super.key,
    this.width,
    this.height = 200,
    this.margin,
    this.showAvatar = false,
    this.showActions = false,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: margin,
      child: SizedBox(
        width: width,
        height: height,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (showAvatar) ...[
                Row(
                  children: [
                    const SkeletonAvatar(size: 40),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const SkeletonWidget(
                            width: 150,
                            height: 16,
                          ),
                          const SizedBox(height: 4),
                          SkeletonWidget(
                            width: 100,
                            height: 14,
                            borderRadius: 2,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
              ],
              const Expanded(
                child: SkeletonText(
                  lines: 3,
                  height: 14,
                ),
              ),
              if (showActions) ...[
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    SkeletonWidget(
                      width: 60,
                      height: 32,
                      borderRadius: 16,
                    ),
                    const SizedBox(width: 8),
                    SkeletonWidget(
                      width: 60,
                      height: 32,
                      borderRadius: 16,
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

// Skeleton List Tile
class SkeletonListTile extends StatelessWidget {
  final bool showLeading;
  final bool showTrailing;
  final bool showSubtitle;
  final EdgeInsetsGeometry? contentPadding;
  
  const SkeletonListTile({
    super.key,
    this.showLeading = true,
    this.showTrailing = false,
    this.showSubtitle = true,
    this.contentPadding,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: contentPadding ?? const EdgeInsets.symmetric(
        horizontal: 16,
        vertical: 8,
      ),
      child: Row(
        children: [
          if (showLeading) ...[
            const SkeletonAvatar(size: 48),
            const SizedBox(width: 16),
          ],
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SkeletonWidget(
                  width: 200,
                  height: 16,
                ),
                if (showSubtitle) ...[
                  const SizedBox(height: 4),
                  const SkeletonWidget(
                    width: 150,
                    height: 14,
                  ),
                ],
              ],
            ),
          ),
          if (showTrailing) ...[
            const SizedBox(width: 16),
            const SkeletonWidget(
              width: 24,
              height: 24,
            ),
          ],
        ],
      ),
    );
  }
}

// Skeleton Grid
class SkeletonGrid extends StatelessWidget {
  final int itemCount;
  final int crossAxisCount;
  final double childAspectRatio;
  final double crossAxisSpacing;
  final double mainAxisSpacing;
  final EdgeInsetsGeometry? padding;
  
  const SkeletonGrid({
    super.key,
    this.itemCount = 6,
    this.crossAxisCount = 2,
    this.childAspectRatio = 1,
    this.crossAxisSpacing = 16,
    this.mainAxisSpacing = 16,
    this.padding,
  });

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding: padding,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: crossAxisCount,
        childAspectRatio: childAspectRatio,
        crossAxisSpacing: crossAxisSpacing,
        mainAxisSpacing: mainAxisSpacing,
      ),
      itemCount: itemCount,
      itemBuilder: (context, index) {
        return const SkeletonCard(
          height: double.infinity,
        );
      },
    );
  }
}