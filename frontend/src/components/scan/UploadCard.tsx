import React, { useRef, useState } from 'react';
import { GlassCard } from '../common/GlassCard';
import { Button } from '../common/Button';
import { validateImageFile, formatFileSize } from '../../utils/imageValidation';
import type { CapturedImage } from './scanTypes';

// Re-export for backward compatibility
export type SelectedImage = CapturedImage;

interface UploadCardProps {
  id: string;
  title: string;
  subtitle: string;
  icon?: string;
  selectedImage: CapturedImage | null;
  onImageSelected: (image: CapturedImage) => void;
  onImageRemoved: () => void;
  required?: boolean;
}

export const UploadCard: React.FC<UploadCardProps> = ({
  id,
  title,
  subtitle,
  icon = '📷',
  selectedImage,
  onImageSelected,
  onImageRemoved,
  required = true
}) => {
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [cardError, setCardError] = useState<string | null>(null);
  const [isValidating, setIsValidating] = useState<boolean>(false);

  const handleFileChange = async (
    e: React.ChangeEvent<HTMLInputElement>,
    capturedVia: 'camera' | 'upload'
  ) => {
    const file = e.target.files?.[0];
    // Always reset inputs so selecting the same file triggers onChange again
    e.target.value = '';

    if (!file) return;

    setIsValidating(true);
    setCardError(null);

    try {
      const validation = await validateImageFile(file);

      if (!validation.isValid) {
        setCardError(validation.errorMessage || 'Invalid image file.');
        setIsValidating(false);
        return;
      }

      // Memory cleanup: Revoke old preview object URL if replacing an existing image
      if (selectedImage?.previewUrl && selectedImage.previewUrl.startsWith('blob:')) {
        URL.revokeObjectURL(selectedImage.previewUrl);
      }

      const previewUrl = URL.createObjectURL(file);
      const newCapturedImage: CapturedImage = {
        file,
        previewUrl,
        name: file.name,
        sizeBytes: file.size,
        sizeFormatted: formatFileSize(file.size),
        mimeType: file.type || 'image/jpeg',
        dimensions: validation.dimensions,
        capturedVia,
        timestamp: Date.now()
      };

      onImageSelected(newCapturedImage);
    } catch {
      setCardError('An unexpected error occurred while processing the image.');
    } finally {
      setIsValidating(false);
    }
  };

  const handleRemove = () => {
    setCardError(null);
    if (selectedImage?.previewUrl && selectedImage.previewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(selectedImage.previewUrl);
    }
    onImageRemoved();
  };

  return (
    <GlassCard variant="elevated" padding="medium" className="upload-card">
      <div className="upload-card-header">
        <div className="upload-card-title-group">
          <span className="upload-card-icon" aria-hidden="true">{icon}</span>
          <div>
            <div className="upload-card-badge">
              <span>{required ? 'REQUIRED' : 'OPTIONAL'}</span>
            </div>
            <h3 className="upload-card-title">{title}</h3>
          </div>
        </div>
      </div>

      <p className="upload-card-desc">{subtitle}</p>

      {/* Card Validation Error Alert */}
      {cardError && (
        <div className="upload-card-error" role="alert">
          <span className="error-icon" aria-hidden="true">⚠️</span>
          <span className="error-text">{cardError}</span>
          <button
            type="button"
            className="error-dismiss"
            onClick={() => setCardError(null)}
            aria-label="Dismiss error"
          >
            ✕
          </button>
        </div>
      )}

      {/* Hidden File Inputs */}
      {/* 1. Camera capture targeting mobile rear camera */}
      <input
        type="file"
        ref={cameraInputRef}
        id={`${id}-camera`}
        accept="image/jpeg,image/png,image/webp"
        capture="environment"
        className="sr-only"
        onChange={(e) => handleFileChange(e, 'camera')}
      />

      {/* 2. File picker for gallery or desktop selection */}
      <input
        type="file"
        ref={fileInputRef}
        id={`${id}-file`}
        accept="image/jpeg,image/png,image/webp"
        className="sr-only"
        onChange={(e) => handleFileChange(e, 'upload')}
      />

      {/* Validating indicator */}
      {isValidating && (
        <div className="upload-validating-indicator" aria-live="polite">
          <span className="spinner-dots" aria-hidden="true">⏳</span>
          <span>Verifying image format & quality...</span>
        </div>
      )}

      {/* Preview or Action Selection */}
      {selectedImage ? (
        <div className="upload-preview-container">
          <div className="upload-preview-frame">
            <img
              src={selectedImage.previewUrl}
              alt={`${title} captured preview`}
              className="upload-preview-image"
            />
            <div className="upload-preview-badge">
              <span>{selectedImage.capturedVia === 'camera' ? '📷 Photo' : '📁 Upload'}</span>
            </div>
          </div>

          {/* File Metadata Chip */}
          <div className="upload-file-meta">
            <span className="upload-file-name" title={selectedImage.name}>
              {selectedImage.name}
            </span>
            <div className="upload-file-stats">
              <span className="upload-file-size">{selectedImage.sizeFormatted}</span>
              {selectedImage.dimensions && (
                <span className="upload-file-dim">
                  {selectedImage.dimensions.width}×{selectedImage.dimensions.height}
                </span>
              )}
            </div>
          </div>

          {/* Touch-Friendly Action Buttons: Retake, Replace, Remove */}
          <div className="upload-preview-actions">
            <Button
              variant="outline"
              size="small"
              onClick={() => cameraInputRef.current?.click()}
              icon={<span aria-hidden="true">📷</span>}
            >
              Retake
            </Button>
            <Button
              variant="outline"
              size="small"
              onClick={() => fileInputRef.current?.click()}
              icon={<span aria-hidden="true">📁</span>}
            >
              Replace
            </Button>
            <Button
              variant="danger"
              size="small"
              onClick={handleRemove}
              icon={<span aria-hidden="true">🗑️</span>}
            >
              Remove
            </Button>
          </div>
        </div>
      ) : (
        <div className="upload-action-box">
          <div className="upload-buttons-row">
            <Button
              variant="primary"
              size="medium"
              fullWidth
              onClick={() => cameraInputRef.current?.click()}
              icon={<span aria-hidden="true">📸</span>}
            >
              TAKE PHOTO
            </Button>
            <Button
              variant="outline"
              size="medium"
              fullWidth
              onClick={() => fileInputRef.current?.click()}
              icon={<span aria-hidden="true">📁</span>}
            >
              UPLOAD IMAGE
            </Button>
          </div>
          <span className="upload-hint-text">
            Supports JPG, PNG, WEBP (Max 10 MB)
          </span>
        </div>
      )}
    </GlassCard>
  );
};

export default UploadCard;
