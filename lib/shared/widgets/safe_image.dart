import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../core/error/error_handler.dart';

/// A safe image widget that handles various image formats and provides graceful fallbacks
class SafeImage extends StatelessWidget {
  final String imageUrl;
  final Widget? placeholder;
  final Widget? errorWidget;
  final BoxFit? fit;
  final double? width;
  final double? height;
  final Color? color;
  final BlendMode? colorBlendMode;
  final AlignmentGeometry alignment;
  final ImageRepeat repeat;
  final Rect? centerSlice;
  final bool matchTextDirection;
  final bool gaplessPlayback;
  final FilterQuality filterQuality;
  final bool isAntiAlias;
  final Map<String, String>? httpHeaders;
  final Duration? fadeOutDuration;
  final Duration? fadeInDuration;
  final Curve fadeOutCurve;
  final Curve fadeInCurve;

  const SafeImage({
    super.key,
    required this.imageUrl,
    this.placeholder,
    this.errorWidget,
    this.fit,
    this.width,
    this.height,
    this.color,
    this.colorBlendMode,
    this.alignment = Alignment.center,
    this.repeat = ImageRepeat.noRepeat,
    this.centerSlice,
    this.matchTextDirection = false,
    this.gaplessPlayback = false,
    this.filterQuality = FilterQuality.low,
    this.isAntiAlias = false,
    this.httpHeaders,
    this.fadeOutDuration,
    this.fadeInDuration,
    this.fadeOutCurve = Curves.easeOut,
    this.fadeInCurve = Curves.easeIn,
  });

  @override
  Widget build(BuildContext context) {
    // Check if the URL might be an SVG (basic heuristic)
    final isSvg = imageUrl.toLowerCase().contains('.svg') || 
                  imageUrl.toLowerCase().contains('svg');
    
    if (isSvg) {
      // For SVG images, provide a fallback since Flutter web doesn't support SVG natively
      return _buildSvgFallback(context);
    }

    return CachedNetworkImage(
      imageUrl: imageUrl,
      placeholder: placeholder != null 
          ? (context, url) => placeholder!
          : (context, url) => _buildDefaultPlaceholder(context),
      errorWidget: (context, url, error) => _handleImageError(context, url, error),
      fit: fit,
      width: width,
      height: height,
      color: color,
      colorBlendMode: colorBlendMode,
      alignment: alignment,
      repeat: repeat,
      matchTextDirection: matchTextDirection,
      fadeOutDuration: fadeOutDuration,
      fadeInDuration: fadeInDuration,
      fadeOutCurve: fadeOutCurve,
      fadeInCurve: fadeInCurve,
      filterQuality: filterQuality,
      httpHeaders: httpHeaders,
    );
  }

  Widget _buildSvgFallback(BuildContext context) {
    final theme = Theme.of(context);
    
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Icon(
        Icons.image,
        size: (width != null && height != null) 
            ? (width! + height!) / 4 
            : 32,
        color: theme.colorScheme.onSurfaceVariant,
      ),
    );
  }

  Widget _buildDefaultPlaceholder(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: Colors.grey[200],
        borderRadius: BorderRadius.circular(8),
      ),
      child: const Center(
        child: CircularProgressIndicator(strokeWidth: 2),
      ),
    );
  }

  Widget _handleImageError(BuildContext context, String url, dynamic error) {
    // Report the error using our enhanced error handling
    GlobalErrorHandler.reportImageError(
      imageUrl: url,
      error: error,
      additionalContext: 'SafeImage widget',
    );

    // Return the custom error widget or a default one
    if (errorWidget != null) {
      return errorWidget!;
    }

    return _buildDefaultErrorWidget(context);
  }

  Widget _buildDefaultErrorWidget(BuildContext context) {
    final theme = Theme.of(context);
    
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: theme.colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Icon(
        Icons.broken_image,
        size: (width != null && height != null) 
            ? (width! + height!) / 4 
            : 32,
        color: theme.colorScheme.error,
      ),
    );
  }
}

/// Extension to provide safe image loading for network images
extension SafeImageExtension on Image {
  static Widget network(
    String src, {
    Key? key,
    double scale = 1.0,
    Widget Function(BuildContext, Widget, int?, bool)? frameBuilder,
    Widget Function(BuildContext, Object, StackTrace?)? errorBuilder,
    String? semanticLabel,
    bool excludeFromSemantics = false,
    double? width,
    double? height,
    Color? color,
    Animation<double>? opacity,
    BlendMode? colorBlendMode,
    BoxFit? fit,
    AlignmentGeometry alignment = Alignment.center,
    ImageRepeat repeat = ImageRepeat.noRepeat,
    Rect? centerSlice,
    bool matchTextDirection = false,
    bool gaplessPlayback = false,
    bool isAntiAlias = false,
    Map<String, String>? headers,
    int? cacheWidth,
    int? cacheHeight,
    FilterQuality filterQuality = FilterQuality.low,
  }) {
    return SafeImage(
      key: key,
      imageUrl: src,
      width: width,
      height: height,
      color: color,
      colorBlendMode: colorBlendMode,
      fit: fit,
      alignment: alignment,
      repeat: repeat,
      centerSlice: centerSlice,
      matchTextDirection: matchTextDirection,
      gaplessPlayback: gaplessPlayback,
      isAntiAlias: isAntiAlias,
      httpHeaders: headers,
      filterQuality: filterQuality,
      errorWidget: errorBuilder != null 
          ? Builder(builder: (context) => errorBuilder(context, 'Image load error', null))
          : null,
    );
  }
}