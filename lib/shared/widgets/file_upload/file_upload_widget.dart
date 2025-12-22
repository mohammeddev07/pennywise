import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';

enum FileUploadType {
  image,
  document,
  any,
}

class FileUploadResult {
  final String name;
  final String? path;
  final Uint8List? bytes;
  final int size;
  final String? extension;

  FileUploadResult({
    required this.name,
    this.path,
    this.bytes,
    required this.size,
    this.extension,
  });

  String get sizeFormatted {
    if (size < 1024) return '${size}B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)}KB';
    if (size < 1024 * 1024 * 1024) return '${(size / (1024 * 1024)).toStringAsFixed(1)}MB';
    return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(1)}GB';
  }
}

class FileUploadWidget extends StatefulWidget {
  final FileUploadType type;
  final List<String>? allowedExtensions;
  final int? maxSizeInBytes;
  final bool multiple;
  final Function(List<FileUploadResult>)? onFilesSelected;
  final Function(String)? onError;
  final String? hint;
  final bool showPreview;

  const FileUploadWidget({
    super.key,
    this.type = FileUploadType.any,
    this.allowedExtensions,
    this.maxSizeInBytes,
    this.multiple = false,
    this.onFilesSelected,
    this.onError,
    this.hint,
    this.showPreview = true,
  });

  @override
  State<FileUploadWidget> createState() => _FileUploadWidgetState();
}

class _FileUploadWidgetState extends State<FileUploadWidget> {
  List<FileUploadResult> _selectedFiles = [];
  bool _isDragOver = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        MouseRegion(
          onEnter: (_) => setState(() => _isDragOver = true),
          onExit: (_) => setState(() => _isDragOver = false),
          child: GestureDetector(
            onTap: _pickFiles,
            child: Container(
              width: double.infinity,
              height: 120,
              decoration: BoxDecoration(
                border: Border.all(
                  color: _isDragOver 
                      ? theme.colorScheme.primary
                      : theme.colorScheme.outline,
                  width: _isDragOver ? 2 : 1,
                  style: BorderStyle.solid,
                ),
                borderRadius: BorderRadius.circular(8),
                color: _isDragOver 
                    ? theme.colorScheme.primary.withValues(alpha: 0.05)
                    : theme.colorScheme.surfaceContainerLowest,
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    _getIconForType(),
                    size: 32,
                    color: _isDragOver 
                        ? theme.colorScheme.primary
                        : theme.colorScheme.onSurfaceVariant,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    widget.hint ?? _getDefaultHint(),
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: _isDragOver 
                          ? theme.colorScheme.primary
                          : theme.colorScheme.onSurfaceVariant,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'or click to browse',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        if (_selectedFiles.isNotEmpty && widget.showPreview) ...[
          const SizedBox(height: 16),
          _buildFilePreview(),
        ],
      ],
    );
  }

  Widget _buildFilePreview() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Selected Files:',
          style: Theme.of(context).textTheme.titleSmall,
        ),
        const SizedBox(height: 8),
        ..._selectedFiles.map((file) => _buildFileItem(file)),
      ],
    );
  }

  Widget _buildFileItem(FileUploadResult file) {
    final theme = Theme.of(context);
    
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Icon(
              _getFileIcon(file.extension),
              color: theme.colorScheme.primary,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    file.name,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w500,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    '${file.sizeFormatted} • ${file.extension?.toUpperCase() ?? 'Unknown'}',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
            IconButton(
              icon: const Icon(Icons.close),
              onPressed: () => _removeFile(file),
              iconSize: 20,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickFiles() async {
    try {
      FileType fileType = _getFileType();
      List<String>? allowedExtensions = widget.allowedExtensions;

      if (widget.type == FileUploadType.image && allowedExtensions == null) {
        allowedExtensions = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'];
      } else if (widget.type == FileUploadType.document && allowedExtensions == null) {
        allowedExtensions = ['pdf', 'doc', 'docx', 'txt', 'rtf'];
      }

      final result = await FilePicker.platform.pickFiles(
        type: fileType,
        allowMultiple: widget.multiple,
        allowedExtensions: allowedExtensions,
        withData: true,
      );

      if (result != null) {
        final newFiles = <FileUploadResult>[];
        
        for (final file in result.files) {
          // Validate file size
          if (widget.maxSizeInBytes != null && file.size > widget.maxSizeInBytes!) {
            widget.onError?.call(
              'File "${file.name}" is too large. Maximum size is ${_formatBytes(widget.maxSizeInBytes!)}.',
            );
            continue;
          }

          newFiles.add(FileUploadResult(
            name: file.name,
            path: file.path,
            bytes: file.bytes,
            size: file.size,
            extension: file.extension,
          ));
        }

        if (newFiles.isNotEmpty) {
          setState(() {
            if (widget.multiple) {
              _selectedFiles.addAll(newFiles);
            } else {
              _selectedFiles = newFiles;
            }
          });

          widget.onFilesSelected?.call(_selectedFiles);
        }
      }
    } catch (e) {
      widget.onError?.call('Error selecting files: ${e.toString()}');
    }
  }

  void _removeFile(FileUploadResult file) {
    setState(() {
      _selectedFiles.remove(file);
    });
    widget.onFilesSelected?.call(_selectedFiles);
  }

  FileType _getFileType() {
    switch (widget.type) {
      case FileUploadType.image:
        return FileType.image;
      case FileUploadType.document:
        return FileType.custom;
      case FileUploadType.any:
        return FileType.any;
    }
  }

  IconData _getIconForType() {
    switch (widget.type) {
      case FileUploadType.image:
        return Icons.image_outlined;
      case FileUploadType.document:
        return Icons.description_outlined;
      case FileUploadType.any:
        return Icons.upload_file_outlined;
    }
  }

  IconData _getFileIcon(String? extension) {
    if (extension == null) return Icons.insert_drive_file;
    
    switch (extension.toLowerCase()) {
      case 'jpg':
      case 'jpeg':
      case 'png':
      case 'gif':
      case 'bmp':
      case 'webp':
        return Icons.image;
      case 'pdf':
        return Icons.picture_as_pdf;
      case 'doc':
      case 'docx':
        return Icons.description;
      case 'txt':
        return Icons.text_snippet;
      case 'zip':
      case 'rar':
      case '7z':
        return Icons.archive;
      case 'mp3':
      case 'wav':
      case 'flac':
        return Icons.audio_file;
      case 'mp4':
      case 'avi':
      case 'mkv':
        return Icons.video_file;
      default:
        return Icons.insert_drive_file;
    }
  }

  String _getDefaultHint() {
    switch (widget.type) {
      case FileUploadType.image:
        return 'Drop image files here';
      case FileUploadType.document:
        return 'Drop document files here';
      case FileUploadType.any:
        return 'Drop files here';
    }
  }

  String _formatBytes(int bytes) {
    if (bytes < 1024) return '${bytes}B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)}KB';
    if (bytes < 1024 * 1024 * 1024) return '${(bytes / (1024 * 1024)).toStringAsFixed(1)}MB';
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)}GB';
  }
}

// Specialized file upload widgets
class ImageUploadWidget extends StatelessWidget {
  final Function(List<FileUploadResult>)? onImagesSelected;
  final Function(String)? onError;
  final bool multiple;
  final int? maxSizeInBytes;

  const ImageUploadWidget({
    super.key,
    this.onImagesSelected,
    this.onError,
    this.multiple = false,
    this.maxSizeInBytes = 5 * 1024 * 1024, // 5MB default
  });

  @override
  Widget build(BuildContext context) {
    return FileUploadWidget(
      type: FileUploadType.image,
      multiple: multiple,
      maxSizeInBytes: maxSizeInBytes,
      onFilesSelected: onImagesSelected,
      onError: onError,
      hint: 'Drop image files here\n(JPG, PNG, GIF, WebP)',
    );
  }
}

class DocumentUploadWidget extends StatelessWidget {
  final Function(List<FileUploadResult>)? onDocumentsSelected;
  final Function(String)? onError;
  final bool multiple;
  final int? maxSizeInBytes;

  const DocumentUploadWidget({
    super.key,
    this.onDocumentsSelected,
    this.onError,
    this.multiple = false,
    this.maxSizeInBytes = 10 * 1024 * 1024, // 10MB default
  });

  @override
  Widget build(BuildContext context) {
    return FileUploadWidget(
      type: FileUploadType.document,
      allowedExtensions: ['pdf', 'doc', 'docx', 'txt', 'rtf'],
      multiple: multiple,
      maxSizeInBytes: maxSizeInBytes,
      onFilesSelected: onDocumentsSelected,
      onError: onError,
      hint: 'Drop document files here\n(PDF, DOC, DOCX, TXT)',
    );
  }
}