import 'package:flutter/material.dart';

enum ErrorType {
  notFound,
  serverError,
  networkError,
  unauthorized,
  generic,
}

class ErrorPage extends StatelessWidget {
  final ErrorType errorType;
  final String? title;
  final String? message;
  final String? details;
  final VoidCallback? onRetry;
  final VoidCallback? onHome;
  final String? customButtonText;
  final VoidCallback? customAction;

  const ErrorPage({
    super.key,
    required this.errorType,
    this.title,
    this.message,
    this.details,
    this.onRetry,
    this.onHome,
    this.customButtonText,
    this.customAction,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final errorInfo = _getErrorInfo();

    return Scaffold(
      appBar: AppBar(
        title: Text(errorInfo.title),
        backgroundColor: theme.colorScheme.errorContainer,
        foregroundColor: theme.colorScheme.onErrorContainer,
      ),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 600),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  errorInfo.icon,
                  size: 80,
                  color: theme.colorScheme.error,
                ),
                const SizedBox(height: 24),
                Text(
                  title ?? errorInfo.title,
                  style: theme.textTheme.headlineMedium?.copyWith(
                    color: theme.colorScheme.error,
                    fontWeight: FontWeight.bold,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                Text(
                  message ?? errorInfo.message,
                  style: theme.textTheme.bodyLarge,
                  textAlign: TextAlign.center,
                ),
                if (details != null) ...[
                  const SizedBox(height: 16),
                  Card(
                    color: theme.colorScheme.errorContainer.withValues(alpha: 0.1),
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Details',
                            style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            details!,
                            style: theme.textTheme.bodySmall?.copyWith(
                              fontFamily: 'monospace',
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 32),
                _buildActions(context),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildActions(BuildContext context) {
    final actions = <Widget>[];

    if (onRetry != null) {
      actions.add(
        FilledButton.icon(
          onPressed: onRetry,
          icon: const Icon(Icons.refresh),
          label: const Text('Try Again'),
        ),
      );
    }

    if (onHome != null) {
      actions.add(
        OutlinedButton.icon(
          onPressed: onHome,
          icon: const Icon(Icons.home),
          label: const Text('Go Home'),
        ),
      );
    }

    if (customAction != null && customButtonText != null) {
      actions.add(
        TextButton(
          onPressed: customAction,
          child: Text(customButtonText!),
        ),
      );
    }

    if (actions.isEmpty) {
      actions.add(
        FilledButton.icon(
          onPressed: () => Navigator.of(context).pop(),
          icon: const Icon(Icons.arrow_back),
          label: const Text('Go Back'),
        ),
      );
    }

    return Wrap(
      spacing: 16,
      runSpacing: 16,
      alignment: WrapAlignment.center,
      children: actions,
    );
  }

  _ErrorInfo _getErrorInfo() {
    return switch (errorType) {
      ErrorType.notFound => _ErrorInfo(
          icon: Icons.search_off,
          title: '404 - Page Not Found',
          message: 'The page you\'re looking for doesn\'t exist or has been moved.',
        ),
      ErrorType.serverError => _ErrorInfo(
          icon: Icons.error_outline,
          title: '500 - Server Error',
          message: 'Something went wrong on our end. Please try again later.',
        ),
      ErrorType.networkError => _ErrorInfo(
          icon: Icons.wifi_off,
          title: 'Network Error',
          message: 'Please check your internet connection and try again.',
        ),
      ErrorType.unauthorized => _ErrorInfo(
          icon: Icons.lock_outline,
          title: '401 - Unauthorized',
          message: 'You don\'t have permission to access this resource.',
        ),
      ErrorType.generic => _ErrorInfo(
          icon: Icons.warning_amber,
          title: 'Something went wrong',
          message: 'An unexpected error occurred. Please try again.',
        ),
    };
  }
}

class _ErrorInfo {
  final IconData icon;
  final String title;
  final String message;

  _ErrorInfo({
    required this.icon,
    required this.title,
    required this.message,
  });
}

// Specialized error pages
class NotFoundPage extends StatelessWidget {
  final VoidCallback? onHome;

  const NotFoundPage({super.key, this.onHome});

  @override
  Widget build(BuildContext context) {
    return ErrorPage(
      errorType: ErrorType.notFound,
      onHome: onHome ?? () => Navigator.of(context).pushNamedAndRemoveUntil('/', (route) => false),
    );
  }
}

class NetworkErrorPage extends StatelessWidget {
  final VoidCallback? onRetry;
  final VoidCallback? onHome;

  const NetworkErrorPage({
    super.key,
    this.onRetry,
    this.onHome,
  });

  @override
  Widget build(BuildContext context) {
    return ErrorPage(
      errorType: ErrorType.networkError,
      onRetry: onRetry,
      onHome: onHome ?? () => Navigator.of(context).pushNamedAndRemoveUntil('/', (route) => false),
    );
  }
}

class ServerErrorPage extends StatelessWidget {
  final String? details;
  final VoidCallback? onRetry;
  final VoidCallback? onHome;

  const ServerErrorPage({
    super.key,
    this.details,
    this.onRetry,
    this.onHome,
  });

  @override
  Widget build(BuildContext context) {
    return ErrorPage(
      errorType: ErrorType.serverError,
      details: details,
      onRetry: onRetry,
      onHome: onHome ?? () => Navigator.of(context).pushNamedAndRemoveUntil('/', (route) => false),
    );
  }
}

class UnauthorizedPage extends StatelessWidget {
  final VoidCallback? onLogin;

  const UnauthorizedPage({super.key, this.onLogin});

  @override
  Widget build(BuildContext context) {
    return ErrorPage(
      errorType: ErrorType.unauthorized,
      customButtonText: 'Login',
      customAction: onLogin ?? () => Navigator.of(context).pushNamedAndRemoveUntil('/login', (route) => false),
    );
  }
}