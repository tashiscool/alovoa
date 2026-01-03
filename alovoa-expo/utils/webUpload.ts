import { Platform } from "react-native";

export interface UploadProgress {
  totalBytesSent: number;
  totalBytesExpectedToSend: number;
}

export type ProgressCallback = (progress: UploadProgress) => void;

/**
 * Web-compatible file upload using fetch API with progress tracking
 * Falls back to XMLHttpRequest for progress support
 */
export async function uploadFileWeb(
  uploadUrl: string,
  fileUri: string,
  options: {
    httpMethod?: string;
    contentType?: string;
    headers?: Record<string, string>;
    onProgress?: ProgressCallback;
  } = {}
): Promise<{ status: number; body: string }> {
  const {
    httpMethod = "PUT",
    contentType = "application/octet-stream",
    headers = {},
    onProgress,
  } = options;

  // On web, fileUri is typically a blob URL
  const response = await fetch(fileUri);
  const blob = await response.blob();

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();

    xhr.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress({
          totalBytesSent: event.loaded,
          totalBytesExpectedToSend: event.total,
        });
      }
    });

    xhr.addEventListener("load", () => {
      resolve({
        status: xhr.status,
        body: xhr.responseText,
      });
    });

    xhr.addEventListener("error", () => {
      reject(new Error("Upload failed"));
    });

    xhr.addEventListener("abort", () => {
      reject(new Error("Upload aborted"));
    });

    xhr.open(httpMethod, uploadUrl);

    // Set headers
    xhr.setRequestHeader("Content-Type", contentType);
    Object.entries(headers).forEach(([key, value]) => {
      xhr.setRequestHeader(key, value);
    });

    xhr.send(blob);
  });
}

/**
 * Convert a blob URL to a File object (for FormData uploads)
 */
export async function blobUrlToFile(blobUrl: string, filename: string): Promise<File> {
  const response = await fetch(blobUrl);
  const blob = await response.blob();
  return new File([blob], filename, { type: blob.type });
}

/**
 * Upload a file using FormData (for multipart uploads)
 */
export async function uploadFileMultipart(
  uploadUrl: string,
  fileUri: string,
  fieldName: string,
  filename: string,
  additionalFields?: Record<string, string>,
  onProgress?: ProgressCallback
): Promise<{ status: number; body: string }> {
  const formData = new FormData();

  // Convert blob URL to File
  const file = await blobUrlToFile(fileUri, filename);
  formData.append(fieldName, file);

  // Add additional fields
  if (additionalFields) {
    Object.entries(additionalFields).forEach(([key, value]) => {
      formData.append(key, value);
    });
  }

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();

    xhr.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress({
          totalBytesSent: event.loaded,
          totalBytesExpectedToSend: event.total,
        });
      }
    });

    xhr.addEventListener("load", () => {
      resolve({
        status: xhr.status,
        body: xhr.responseText,
      });
    });

    xhr.addEventListener("error", () => {
      reject(new Error("Upload failed"));
    });

    xhr.open("POST", uploadUrl);
    xhr.send(formData);
  });
}

/**
 * Create a cross-platform upload task
 * On web, uses XMLHttpRequest; on native, uses expo-file-system
 */
export function createUploadTask(
  uploadUrl: string,
  fileUri: string,
  options: {
    httpMethod?: string;
    contentType?: string;
    headers?: Record<string, string>;
  },
  onProgress?: ProgressCallback
) {
  if (Platform.OS === "web") {
    let aborted = false;
    let xhr: XMLHttpRequest | null = null;

    return {
      uploadAsync: async () => {
        if (aborted) return;

        const response = await fetch(fileUri);
        const blob = await response.blob();

        return new Promise<{ status: number; body: string }>((resolve, reject) => {
          xhr = new XMLHttpRequest();

          xhr.upload.addEventListener("progress", (event) => {
            if (event.lengthComputable && onProgress) {
              onProgress({
                totalBytesSent: event.loaded,
                totalBytesExpectedToSend: event.total,
              });
            }
          });

          xhr.addEventListener("load", () => {
            resolve({
              status: xhr!.status,
              body: xhr!.responseText,
            });
          });

          xhr.addEventListener("error", () => {
            reject(new Error("Upload failed"));
          });

          xhr.open(options.httpMethod || "PUT", uploadUrl);

          if (options.contentType) {
            xhr.setRequestHeader("Content-Type", options.contentType);
          }

          if (options.headers) {
            Object.entries(options.headers).forEach(([key, value]) => {
              xhr!.setRequestHeader(key, value);
            });
          }

          xhr.send(blob);
        });
      },
      cancel: () => {
        aborted = true;
        if (xhr) {
          xhr.abort();
        }
      },
    };
  }

  // For native, return a placeholder - the actual implementation will use expo-file-system
  return null;
}

/**
 * Read a file as base64 (web compatible)
 */
export function readAsBase64Web(fileUri: string): Promise<string> {
  return new Promise(async (resolve, reject) => {
    try {
      const response = await fetch(fileUri);
      const blob = await response.blob();
      const reader = new FileReader();

      reader.onloadend = () => {
        const base64 = reader.result as string;
        // Remove the data URL prefix
        const base64Data = base64.split(",")[1];
        resolve(base64Data);
      };

      reader.onerror = reject;
      reader.readAsDataURL(blob);
    } catch (error) {
      reject(error);
    }
  });
}

/**
 * Get file info (size, etc.) from a blob URL
 */
export async function getFileInfoWeb(fileUri: string): Promise<{ size: number; type: string }> {
  const response = await fetch(fileUri);
  const blob = await response.blob();
  return {
    size: blob.size,
    type: blob.type,
  };
}
