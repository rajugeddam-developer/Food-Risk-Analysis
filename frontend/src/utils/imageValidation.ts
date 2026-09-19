export const MAX_IMAGE_SIZE_MB = 10;
export const MAX_IMAGE_SIZE_BYTES = MAX_IMAGE_SIZE_MB * 1024 * 1024;

export const ALLOWED_MIME_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp'
];

export const ALLOWED_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp'];

export interface ValidationResult {
  isValid: boolean;
  errorMessage?: string;
  dimensions?: { width: number; height: number };
}

/**
 * Format bytes into human-readable string (e.g. "1.8 MB" or "450 KB")
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Extract image natural dimensions (width x height) using a temporary Image instance.
 */
export function getImageDimensions(file: File): Promise<{ width: number; height: number } | undefined> {
  return new Promise((resolve) => {
    const objectUrl = URL.createObjectURL(file);
    const img = new Image();

    img.onload = () => {
      const dimensions = { width: img.naturalWidth, height: img.naturalHeight };
      URL.revokeObjectURL(objectUrl);
      resolve(dimensions);
    };

    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      resolve(undefined);
    };

    img.src = objectUrl;
  });
}

/**
 * Validate image file: checks existence, MIME type, file extension, file size, and basic decodability.
 */
export async function validateImageFile(file: File | null | undefined): Promise<ValidationResult> {
  if (!file) {
    return { isValid: false, errorMessage: 'No file selected.' };
  }

  // Check file size > 0
  if (file.size === 0) {
    return { isValid: false, errorMessage: 'The selected file is empty (0 bytes).' };
  }

  // Check file size <= MAX_IMAGE_SIZE_MB
  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    return {
      isValid: false,
      errorMessage: `Image is too large (${formatFileSize(file.size)}). Maximum allowed size is ${MAX_IMAGE_SIZE_MB} MB.`
    };
  }

  // Check MIME type
  const normalizedMime = file.type.toLowerCase();
  const isValidMime = ALLOWED_MIME_TYPES.includes(normalizedMime);

  // Check file extension as secondary protection against MIME spoofing
  const lowerName = file.name.toLowerCase();
  const hasValidExtension = ALLOWED_EXTENSIONS.some((ext) => lowerName.endsWith(ext));

  if (!isValidMime && !hasValidExtension) {
    return {
      isValid: false,
      errorMessage: `Unsupported file format. Please capture or upload a JPEG, PNG, or WEBP image.`
    };
  }

  // Attempt to decode dimensions
  const dimensions = await getImageDimensions(file);
  if (!dimensions || dimensions.width === 0 || dimensions.height === 0) {
    return {
      isValid: false,
      errorMessage: 'Unable to decode image. The file may be corrupt or not a valid image format.'
    };
  }

  return {
    isValid: true,
    dimensions
  };
}
