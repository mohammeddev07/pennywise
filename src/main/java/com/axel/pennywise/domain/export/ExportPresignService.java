package com.axel.pennywise.domain.export;

import org.springframework.stereotype.Service;

@Service
public class ExportPresignService {

  // Phase 1 stub: replace with S3/GCS presigner later.
  public String presign(String storageKey) {
    // You can keep this predictable for now
    return "https://example.com/presigned-url?key=" + storageKey;
  }
}
