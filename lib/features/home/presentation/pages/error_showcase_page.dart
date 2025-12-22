import 'package:flutter/material.dart';
import '../../../../shared/widgets/error/error_page.dart';
import '../../../../shared/widgets/error/error_boundary.dart';
import '../../../../core/error/error_handler.dart';
import '../../../../l10n/app_localizations.dart';

class ErrorShowcasePage extends StatelessWidget {
  const ErrorShowcasePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context).errorHandling),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildSection(
            context,
            AppLocalizations.of(context).errorPages,
            Column(
              children: [
                _buildErrorPageDemo(
                  context,
                  AppLocalizations.of(context).pageNotFound,
                  AppLocalizations.of(context).pageNotFoundDescription,
                  () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => const NotFoundPage(),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                _buildErrorPageDemo(
                  context,
                  AppLocalizations.of(context).networkError,
                  AppLocalizations.of(context).networkErrorDescription,
                  () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => NetworkErrorPage(
                        onRetry: () {
                          Navigator.pop(context);
                          ErrorUtils.showErrorSnackBar(
                            context,
                            message: AppLocalizations.of(context).retry,
                          );
                        },
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                _buildErrorPageDemo(
                  context,
                  AppLocalizations.of(context).serverError,
                  AppLocalizations.of(context).serverErrorDescription,
                  () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => ServerErrorPage(
                        details: 'Internal Server Error: Database connection failed',
                        onRetry: () {
                          Navigator.pop(context);
                          ErrorUtils.showErrorSnackBar(
                            context,
                            message: AppLocalizations.of(context).retry,
                          );
                        },
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                _buildErrorPageDemo(
                  context,
                  AppLocalizations.of(context).unauthorizedError,
                  AppLocalizations.of(context).unauthorizedErrorDescription,
                  () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => UnauthorizedPage(
                        onLogin: () {
                          Navigator.pop(context);
                          ErrorUtils.showErrorSnackBar(
                            context,
                            message: AppLocalizations.of(context).redirectingToLogin,
                          );
                        },
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 32),
          
          _buildSection(
            context,
            AppLocalizations.of(context).errorDialogsSnackbars,
            Column(
              children: [
                _buildErrorDemo(
                  context,
                  AppLocalizations.of(context).showErrorDialog,
                  AppLocalizations.of(context).showErrorDialogDescription,
                  () => ErrorUtils.showErrorDialog(
                    context,
                    title: AppLocalizations.of(context).operationFailed,
                    message: AppLocalizations.of(context).operationFailedMessage,
                    onRetry: () {
                      ErrorUtils.showErrorSnackBar(
                        context,
                        message: AppLocalizations.of(context).retryingOperation,
                      );
                    },
                  ),
                ),
                const SizedBox(height: 12),
                _buildErrorDemo(
                  context,
                  AppLocalizations.of(context).showErrorSnackbar,
                  AppLocalizations.of(context).showErrorSnackbarDescription,
                  () => ErrorUtils.showErrorSnackBar(
                    context,
                    message: AppLocalizations.of(context).failedToSaveChanges,
                    onRetry: () {
                      ErrorUtils.showErrorSnackBar(
                        context,
                        message: AppLocalizations.of(context).changesSavedSuccessfully,
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 32),
          
          _buildSection(
            context,
            AppLocalizations.of(context).errorBoundaries,
            Column(
              children: [
                _buildErrorDemo(
                  context,
                  AppLocalizations.of(context).featureErrorBoundary,
                  AppLocalizations.of(context).featureErrorBoundaryDescription,
                  () => showDialog(
                    context: context,
                    builder: (context) => AlertDialog(
                      title: Text(AppLocalizations.of(context).featureErrorDemo),
                      content: SizedBox(
                        height: 200,
                        width: 300,
                        child: FeatureErrorBoundary(
                          featureName: AppLocalizations.of(context).demoFeature,
                          child: const _ErrorThrowingWidget(),
                        ),
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(context),
                          child: Text(AppLocalizations.of(context).close),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                _buildErrorDemo(
                  context,
                  AppLocalizations.of(context).asyncErrorHandling,
                  AppLocalizations.of(context).asyncErrorHandlingDescription,
                  () => showDialog(
                    context: context,
                    builder: (context) => AlertDialog(
                      title: Text(AppLocalizations.of(context).asyncErrorDemo),
                      content: SizedBox(
                        height: 200,
                        width: 300,
                        child: AsyncErrorHandler<String>(
                          future: _simulateFailingOperation(),
                          builder: (context, data) => Text(data),
                          onRetry: () {
                            Navigator.pop(context);
                            ErrorUtils.showErrorSnackBar(
                              context,
                              message: AppLocalizations.of(context).retryingAsyncOperation,
                            );
                          },
                        ),
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(context),
                          child: Text(AppLocalizations.of(context).close),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 32),
          
          _buildSection(
            context,
            AppLocalizations.of(context).errorReporting,
            Column(
              children: [
                _buildErrorDemo(
                  context,
                  AppLocalizations.of(context).reportCustomError,
                  AppLocalizations.of(context).reportCustomErrorDescription,
                  () {
                    final error = Exception(AppLocalizations.of(context).customErrorForDemo);
                    error.reportError(StackTrace.current, AppLocalizations.of(context).errorShowcase);
                    
                    ErrorUtils.showErrorSnackBar(
                      context,
                      message: AppLocalizations.of(context).errorReportedCheckConsole,
                    );
                  },
                ),
                const SizedBox(height: 12),
                _buildErrorDemo(
                  context,
                  AppLocalizations.of(context).networkErrorMessage,
                  AppLocalizations.of(context).networkErrorMessageDescription,
                  () {
                    final message = ErrorUtils.getNetworkErrorMessage(
                      AppLocalizations.of(context).socketExceptionFailedHostLookup,
                    );
                    
                    ErrorUtils.showErrorSnackBar(
                      context,
                      message: message,
                    );
                  },
                ),
                const SizedBox(height: 12),
                _buildErrorDemo(
                  context,
                  AppLocalizations.of(context).apiErrorMessage,
                  AppLocalizations.of(context).apiErrorMessageDescription,
                  () {
                    final message = ErrorUtils.getApiErrorMessage(404, null);
                    
                    ErrorUtils.showErrorSnackBar(
                      context,
                      message: message,
                    );
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _buildSection(BuildContext context, String title, Widget content) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 16),
        content,
      ],
    );
  }
  
  Widget _buildErrorPageDemo(
    BuildContext context,
    String title,
    String description,
    VoidCallback onTap,
  ) {
    return Card(
      child: ListTile(
        leading: Icon(
          Icons.error_outline,
          color: Theme.of(context).colorScheme.error,
        ),
        title: Text(title),
        subtitle: Text(description),
        trailing: const Icon(Icons.arrow_forward_ios),
        onTap: onTap,
      ),
    );
  }
  
  Widget _buildErrorDemo(
    BuildContext context,
    String title,
    String description,
    VoidCallback onTap,
  ) {
    return Card(
      child: ListTile(
        leading: Icon(
          Icons.play_arrow,
          color: Theme.of(context).colorScheme.primary,
        ),
        title: Text(title),
        subtitle: Text(description),
        onTap: onTap,
      ),
    );
  }

  Future<String> _simulateFailingOperation() async {
    await Future.delayed(const Duration(seconds: 1));
    throw Exception('Simulated async operation failure');
  }
}

class _ErrorThrowingWidget extends StatelessWidget {
  const _ErrorThrowingWidget();

  @override
  Widget build(BuildContext context) {
    // Simulate an error after a short delay
    WidgetsBinding.instance.addPostFrameCallback((_) {
      throw Exception(AppLocalizations.of(context).simulatedWidgetErrorForDemo);
    });

    return Center(
      child: Text(AppLocalizations.of(context).thisWidgetWillThrowError),
    );
  }
}