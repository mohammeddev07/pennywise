import 'package:flutter/material.dart';
import 'dart:developer' as developer;

class ErrorBoundary extends StatefulWidget {
  final Widget child;
  final Widget? fallback;
  final Function(FlutterErrorDetails)? onError;
  final bool isRootErrorBoundary;

  const ErrorBoundary({
    super.key,
    required this.child,
    this.fallback,
    this.onError,
    this.isRootErrorBoundary = false,
  });

  @override
  State<ErrorBoundary> createState() => _ErrorBoundaryState();
}

class _ErrorBoundaryState extends State<ErrorBoundary> {
  bool _hasError = false;
  FlutterErrorDetails? _errorDetails;

  @override
  void initState() {
    super.initState();
    
    if (widget.isRootErrorBoundary) {
      // Set up global error handling for the root boundary
      FlutterError.onError = (FlutterErrorDetails details) {
        _handleError(details);
        widget.onError?.call(details);
      };
    }
  }

  void _handleError(FlutterErrorDetails details) {
    developer.log(
      'Error caught by ErrorBoundary',
      error: details.exception,
      stackTrace: details.stack,
    );

    if (mounted) {
      // Use post-frame callback to avoid setState during build
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          setState(() {
            _hasError = true;
            _errorDetails = details;
          });
        }
      });
    }
  }

  void _reset() {
    setState(() {
      _hasError = false;
      _errorDetails = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_hasError) {
      return widget.fallback ?? _DefaultErrorWidget(
        errorDetails: _errorDetails,
        onReset: _reset,
      );
    }

    return ErrorCatcher(
      onError: _handleError,
      child: widget.child,
    );
  }
}

class ErrorCatcher extends StatelessWidget {
  final Widget child;
  final Function(FlutterErrorDetails) onError;

  const ErrorCatcher({
    super.key,
    required this.child,
    required this.onError,
  });

  @override
  Widget build(BuildContext context) {
    return child;
  }
}

class _DefaultErrorWidget extends StatelessWidget {
  final FlutterErrorDetails? errorDetails;
  final VoidCallback? onReset;

  const _DefaultErrorWidget({
    this.errorDetails,
    this.onReset,
  });

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: _ErrorContent(
          errorDetails: errorDetails,
          onReset: onReset,
        ),
      ),
    );
  }
}

class _ErrorContent extends StatelessWidget {
  final FlutterErrorDetails? errorDetails;
  final VoidCallback? onReset;

  const _ErrorContent({
    this.errorDetails,
    this.onReset,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(24),
      color: theme.colorScheme.errorContainer,
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.error_outline,
              size: 64,
              color: theme.colorScheme.error,
            ),
            const SizedBox(height: 16),
            Text(
              'Something went wrong',
              style: theme.textTheme.headlineSmall?.copyWith(
                color: theme.colorScheme.onErrorContainer,
                fontWeight: FontWeight.bold,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              'An unexpected error occurred in this part of the app.',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onErrorContainer,
              ),
              textAlign: TextAlign.center,
            ),
            if (errorDetails != null) ...[
              const SizedBox(height: 16),
              ExpansionTile(
                title: Text(
                  'Error Details',
                  style: TextStyle(
                    color: theme.colorScheme.onErrorContainer,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                iconColor: theme.colorScheme.onErrorContainer,
                collapsedIconColor: theme.colorScheme.onErrorContainer,
                children: [
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(16),
                    margin: const EdgeInsets.symmetric(horizontal: 16),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surface,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Exception:',
                          style: theme.textTheme.labelMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          errorDetails!.exception.toString(),
                          style: theme.textTheme.bodySmall?.copyWith(
                            fontFamily: 'monospace',
                          ),
                        ),
                        if (errorDetails!.stack != null) ...[
                          const SizedBox(height: 12),
                          Text(
                            'Stack Trace:',
                            style: theme.textTheme.labelMedium?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            errorDetails!.stack.toString(),
                            style: theme.textTheme.bodySmall?.copyWith(
                              fontFamily: 'monospace',
                            ),
                            maxLines: 10,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ],
            const SizedBox(height: 24),
            if (onReset != null)
              FilledButton.icon(
                onPressed: onReset,
                icon: const Icon(Icons.refresh),
                label: const Text('Try Again'),
                style: FilledButton.styleFrom(
                  backgroundColor: theme.colorScheme.error,
                  foregroundColor: theme.colorScheme.onError,
                ),
              ),
          ],
        ),
      ),
    );
  }
}

// Widget to wrap individual features with error boundaries
class FeatureErrorBoundary extends StatelessWidget {
  final Widget child;
  final String featureName;

  const FeatureErrorBoundary({
    super.key,
    required this.child,
    required this.featureName,
  });

  @override
  Widget build(BuildContext context) {
    return ErrorBoundary(
      onError: (details) {
        developer.log(
          'Error in $featureName feature',
          error: details.exception,
          stackTrace: details.stack,
        );
      },
      fallback: _FeatureErrorFallback(featureName: featureName),
      child: child,
    );
  }
}

class _FeatureErrorFallback extends StatelessWidget {
  final String featureName;

  const _FeatureErrorFallback({required this.featureName});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Material(
      child: Card(
        margin: const EdgeInsets.all(16),
        color: theme.colorScheme.errorContainer,
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.warning_amber,
                size: 48,
                color: theme.colorScheme.error,
              ),
              const SizedBox(height: 16),
              Text(
                '$featureName Unavailable',
                style: theme.textTheme.titleMedium?.copyWith(
                  color: theme.colorScheme.onErrorContainer,
                  fontWeight: FontWeight.bold,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              Text(
                'This feature is temporarily unavailable due to an error.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onErrorContainer,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: () {
                  // Trigger a rebuild by navigating and coming back
                  Navigator.of(context).pushReplacement(
                    MaterialPageRoute(
                      builder: (context) => const SizedBox.shrink(),
                    ),
                  );
                },
                icon: const Icon(Icons.refresh),
                label: const Text('Reload'),
                style: FilledButton.styleFrom(
                  backgroundColor: theme.colorScheme.error,
                  foregroundColor: theme.colorScheme.onError,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}