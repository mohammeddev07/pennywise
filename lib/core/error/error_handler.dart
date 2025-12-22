import 'dart:async';
import 'dart:developer' as developer;
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

class GlobalErrorHandler {
  static bool _isInitialized = false;

  static void initialize() {
    if (_isInitialized) return;

    // Handle Flutter framework errors
    FlutterError.onError = (FlutterErrorDetails details) {
      _logError(details.exception, details.stack, details.context);
      
      if (kDebugMode) {
        // In debug mode, show the default error widget
        FlutterError.presentError(details);
      }
    };

    // Set a custom error widget builder that includes Material context
    ErrorWidget.builder = (FlutterErrorDetails details) {
      return MaterialApp(
        home: Scaffold(
          body: Container(
            color: const Color(0xFFFFE0E0),
            padding: const EdgeInsets.all(16),
            child: const Center(
              child: Text(
                'An error occurred',
                style: TextStyle(
                  color: Color(0xFF8B0000),
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
                textAlign: TextAlign.center,
              ),
            ),
          ),
        ),
      );
    };

    // Handle errors outside of Flutter framework
    PlatformDispatcher.instance.onError = (error, stack) {
      _logError(error, stack, 'PlatformDispatcher');
      return true;
    };

    // Handle async errors
    runZonedGuarded(() {}, (error, stack) {
      _logError(error, stack, 'RunZonedGuarded');
    });

    _isInitialized = true;
  }

  static void _logError(dynamic error, StackTrace? stack, dynamic context) {
    developer.log(
      'Global Error Handler',
      error: error,
      stackTrace: stack,
      name: 'ErrorHandler',
    );

    // In a real app, you would send this to your error reporting service
    // Examples: Firebase Crashlytics, Sentry, Bugsnag, etc.
    if (kDebugMode) {
      print('=== ERROR REPORT ===');
      print('Context: $context');
      print('Error: $error');
      print('Stack: $stack');
      print('==================');
    }
  }

  static void reportError({
    required dynamic error,
    StackTrace? stackTrace,
    String? context,
    Map<String, dynamic>? metadata,
  }) {
    _logError(error, stackTrace, context);
    
    // Add metadata logging
    if (metadata != null && kDebugMode) {
      print('Metadata: $metadata');
    }
  }

  // Specific handler for image loading errors
  static void reportImageError({
    required String imageUrl,
    required dynamic error,
    String? additionalContext,
  }) {
    final context = 'Image Loading Error${additionalContext != null ? ' - $additionalContext' : ''}';
    final metadata = {
      'imageUrl': imageUrl,
      'errorType': error.runtimeType.toString(),
      'timestamp': DateTime.now().toIso8601String(),
    };
    
    reportError(
      error: error,
      context: context,
      metadata: metadata,
    );
  }
}

// Extension to easily report errors from anywhere in the app
extension ErrorReporting on Object {
  void reportError([StackTrace? stackTrace, String? context]) {
    GlobalErrorHandler.reportError(
      error: this,
      stackTrace: stackTrace ?? StackTrace.current,
      context: context,
    );
  }
}

// Utility class for handling common error scenarios
class ErrorUtils {
  // Handle network errors
  static String getNetworkErrorMessage(dynamic error) {
    if (error.toString().contains('SocketException')) {
      return 'No internet connection. Please check your network settings.';
    } else if (error.toString().contains('TimeoutException')) {
      return 'Request timed out. Please try again.';
    } else if (error.toString().contains('FormatException')) {
      return 'Received invalid data from server.';
    }
    return 'Network error occurred. Please try again.';
  }

  // Handle API errors
  static String getApiErrorMessage(int? statusCode, String? message) {
    switch (statusCode) {
      case 400:
        return 'Bad request. Please check your input.';
      case 401:
        return 'Unauthorized. Please log in again.';
      case 403:
        return 'Access forbidden. You don\'t have permission.';
      case 404:
        return 'Resource not found.';
      case 429:
        return 'Too many requests. Please try again later.';
      case 500:
        return 'Server error. Please try again later.';
      case 503:
        return 'Service unavailable. Please try again later.';
      default:
        return message ?? 'An error occurred. Please try again.';
    }
  }

  // Show error dialog
  static void showErrorDialog(
    BuildContext context, {
    required String title,
    required String message,
    VoidCallback? onRetry,
    String? retryButtonText,
  }) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        icon: Icon(
          Icons.error_outline,
          color: Theme.of(context).colorScheme.error,
          size: 32,
        ),
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Dismiss'),
          ),
          if (onRetry != null)
            FilledButton(
              onPressed: () {
                Navigator.of(context).pop();
                onRetry();
              },
              child: Text(retryButtonText ?? 'Retry'),
            ),
        ],
      ),
    );
  }

  // Show error snackbar
  static void showErrorSnackBar(
    BuildContext context, {
    required String message,
    VoidCallback? onRetry,
    String? retryButtonText,
  }) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Theme.of(context).colorScheme.error,
        behavior: SnackBarBehavior.floating,
        action: onRetry != null
            ? SnackBarAction(
                label: retryButtonText ?? 'Retry',
                textColor: Theme.of(context).colorScheme.onError,
                onPressed: onRetry,
              )
            : null,
      ),
    );
  }
}

// Widget to handle async operations with error handling
class AsyncErrorHandler<T> extends StatelessWidget {
  final Future<T> future;
  final Widget Function(BuildContext context, T data) builder;
  final Widget Function(BuildContext context, Object error)? errorBuilder;
  final Widget Function(BuildContext context)? loadingBuilder;
  final VoidCallback? onRetry;

  const AsyncErrorHandler({
    super.key,
    required this.future,
    required this.builder,
    this.errorBuilder,
    this.loadingBuilder,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<T>(
      future: future,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return loadingBuilder?.call(context) ??
              const Center(child: CircularProgressIndicator());
        }

        if (snapshot.hasError) {
          final error = snapshot.error!;
          error.reportError(snapshot.stackTrace, 'AsyncErrorHandler');

          return errorBuilder?.call(context, error) ??
              _DefaultAsyncErrorWidget(
                error: error,
                onRetry: onRetry,
              );
        }

        if (snapshot.hasData) {
          return builder(context, snapshot.data as T);
        }

        return const SizedBox.shrink();
      },
    );
  }
}

class _DefaultAsyncErrorWidget extends StatelessWidget {
  final Object error;
  final VoidCallback? onRetry;

  const _DefaultAsyncErrorWidget({
    required this.error,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Material(
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.error_outline,
                size: 64,
                color: theme.colorScheme.error,
              ),
            const SizedBox(height: 16),
            Text(
              'Something went wrong',
              style: theme.textTheme.titleLarge?.copyWith(
                color: theme.colorScheme.error,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              ErrorUtils.getNetworkErrorMessage(error),
              style: theme.textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            if (onRetry != null) ...[
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh),
                label: const Text('Try Again'),
              ),
            ],
            ],
          ),
        ),
      ),
    );
  }
}