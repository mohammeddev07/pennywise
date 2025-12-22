import 'package:flutter/material.dart';
import '../../../../shared/widgets/file_upload/file_upload_widget.dart';
import '../../../../l10n/app_localizations.dart';

class FileUploadShowcasePage extends StatefulWidget {
  const FileUploadShowcasePage({super.key});

  @override
  State<FileUploadShowcasePage> createState() => _FileUploadShowcasePageState();
}

class _FileUploadShowcasePageState extends State<FileUploadShowcasePage> {
  final List<FileUploadResult> _generalFiles = [];
  final List<FileUploadResult> _imageFiles = [];
  final List<FileUploadResult> _documentFiles = [];
  final List<FileUploadResult> _multipleFiles = [];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context).fileUpload),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildSection(
              context,
              AppLocalizations.of(context).generalFileUpload,
              AppLocalizations.of(context).generalFileUploadDescription,
              FileUploadWidget(
                type: FileUploadType.any,
                maxSizeInBytes: 5 * 1024 * 1024, // 5MB
                hint: AppLocalizations.of(context).dropAnyFileHere,
                onFilesSelected: (files) {
                  setState(() {
                    _generalFiles.clear();
                    _generalFiles.addAll(files);
                  });
                  _showSuccessMessage(AppLocalizations.of(context).fileUploadedSuccessfully);
                },
                onError: (error) => _showErrorMessage(error),
              ),
            ),
            const SizedBox(height: 32),
            
            _buildSection(
              context,
              AppLocalizations.of(context).imageUpload,
              AppLocalizations.of(context).imageUploadDescription,
              ImageUploadWidget(
                onImagesSelected: (files) {
                  setState(() {
                    _imageFiles.clear();
                    _imageFiles.addAll(files);
                  });
                  _showSuccessMessage(AppLocalizations.of(context).imageUploadedSuccessfully);
                },
                onError: (error) => _showErrorMessage(error),
              ),
            ),
            const SizedBox(height: 32),
            
            _buildSection(
              context,
              AppLocalizations.of(context).documentUpload,
              AppLocalizations.of(context).documentUploadDescription,
              DocumentUploadWidget(
                onDocumentsSelected: (files) {
                  setState(() {
                    _documentFiles.clear();
                    _documentFiles.addAll(files);
                  });
                  _showSuccessMessage(AppLocalizations.of(context).documentUploadedSuccessfully);
                },
                onError: (error) => _showErrorMessage(error),
              ),
            ),
            const SizedBox(height: 32),
            
            _buildSection(
              context,
              AppLocalizations.of(context).multipleFileUpload,
              AppLocalizations.of(context).multipleFileUploadDescription,
              FileUploadWidget(
                type: FileUploadType.any,
                multiple: true,
                maxSizeInBytes: 2 * 1024 * 1024, // 2MB per file
                hint: AppLocalizations.of(context).dropMultipleFilesHere,
                onFilesSelected: (files) {
                  setState(() {
                    _multipleFiles.clear();
                    _multipleFiles.addAll(files);
                  });
                  _showSuccessMessage(AppLocalizations.of(context).multipleFilesUploadedSuccessfully(files.length));
                },
                onError: (error) => _showErrorMessage(error),
              ),
            ),
            const SizedBox(height: 32),
            
            _buildSection(
              context,
              AppLocalizations.of(context).customExtensions,
              AppLocalizations.of(context).customExtensionsDescription,
              FileUploadWidget(
                type: FileUploadType.any,
                allowedExtensions: ['json', 'xml', 'csv'],
                hint: AppLocalizations.of(context).dropJSONXMLCSVFiles,
                onFilesSelected: (files) {
                  _showSuccessMessage(AppLocalizations.of(context).customFileUploadedSuccessfully);
                },
                onError: (error) => _showErrorMessage(error),
              ),
            ),
            const SizedBox(height: 32),
            
            _buildSection(
              context,
              AppLocalizations.of(context).noPreviewUpload,
              AppLocalizations.of(context).noPreviewUploadDescription,
              FileUploadWidget(
                type: FileUploadType.any,
                showPreview: false,
                hint: AppLocalizations.of(context).uploadFilesWithoutPreview,
                onFilesSelected: (files) {
                  _showSuccessMessage(AppLocalizations.of(context).fileUploadedNoPreview);
                },
                onError: (error) => _showErrorMessage(error),
              ),
            ),
            const SizedBox(height: 32),
            
            // Upload Statistics
            _buildUploadStats(),
          ],
        ),
      ),
    );
  }
  
  Widget _buildSection(
    BuildContext context,
    String title,
    String description,
    Widget uploadWidget,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          description,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 16),
        uploadWidget,
      ],
    );
  }
  
  Widget _buildUploadStats() {
    final totalFiles = _generalFiles.length + 
                     _imageFiles.length + 
                     _documentFiles.length + 
                     _multipleFiles.length;
    
    final totalSize = [
      ..._generalFiles,
      ..._imageFiles,
      ..._documentFiles,
      ..._multipleFiles,
    ].fold<int>(0, (sum, file) => sum + file.size);
    
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              AppLocalizations.of(context).uploadStatistics,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _buildStatCard(
                    context,
                    AppLocalizations.of(context).totalFiles,
                    totalFiles.toString(),
                    Icons.insert_drive_file,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: _buildStatCard(
                    context,
                    AppLocalizations.of(context).totalSize,
                    _formatBytes(totalSize),
                    Icons.storage,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _buildStatCard(
                    context,
                    AppLocalizations.of(context).images,
                    _imageFiles.length.toString(),
                    Icons.image,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: _buildStatCard(
                    context,
                    AppLocalizations.of(context).documents,
                    _documentFiles.length.toString(),
                    Icons.description,
                  ),
                ),
              ],
            ),
            if (totalFiles > 0) ...[
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _clearAllFiles,
                  icon: const Icon(Icons.clear_all),
                  label: Text(AppLocalizations.of(context).clearAllFiles),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
  
  Widget _buildStatCard(
    BuildContext context,
    String label,
    String value,
    IconData icon,
  ) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: Theme.of(context).colorScheme.outline.withValues(alpha: 0.2),
        ),
      ),
      child: Column(
        children: [
          Icon(
            icon,
            size: 24,
            color: Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(height: 8),
          Text(
            value,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
          Text(
            label,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
  
  void _clearAllFiles() {
    setState(() {
      _generalFiles.clear();
      _imageFiles.clear();
      _documentFiles.clear();
      _multipleFiles.clear();
    });
    _showSuccessMessage(AppLocalizations.of(context).allFilesCleared);
  }
  
  void _showSuccessMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.green,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
  
  void _showErrorMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Theme.of(context).colorScheme.error,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
  
  String _formatBytes(int bytes) {
    if (bytes < 1024) return '${bytes}B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)}KB';
    if (bytes < 1024 * 1024 * 1024) return '${(bytes / (1024 * 1024)).toStringAsFixed(1)}MB';
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)}GB';
  }
}