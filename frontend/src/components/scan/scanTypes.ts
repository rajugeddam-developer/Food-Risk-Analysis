export interface CapturedImage {
  file: File;
  previewUrl: string;
  name: string;
  sizeBytes: number;
  sizeFormatted: string;
  mimeType: string;
  dimensions?: { width: number; height: number };
  capturedVia: 'camera' | 'upload';
  timestamp: number;
}
