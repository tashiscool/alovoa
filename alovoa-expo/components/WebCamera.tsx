import React, { forwardRef, useImperativeHandle, useRef, useState, useCallback, useEffect } from "react";
import { View, StyleSheet, Platform } from "react-native";

export interface WebCameraRef {
  recordAsync: (options?: { maxDuration?: number }) => Promise<{ uri: string } | undefined>;
  stopRecording: () => void;
  takePictureAsync: () => Promise<{ uri: string } | undefined>;
}

interface WebCameraProps {
  style?: any;
  facing?: "front" | "back";
  mode?: "video" | "picture";
  children?: React.ReactNode;
  onCameraReady?: () => void;
}

// Web Camera component using browser's MediaRecorder API
const WebCamera = forwardRef<WebCameraRef, WebCameraProps>(
  ({ style, facing = "front", mode = "video", children, onCameraReady }, ref) => {
    const videoRef = useRef<HTMLVideoElement>(null);
    const mediaRecorderRef = useRef<MediaRecorder | null>(null);
    const streamRef = useRef<MediaStream | null>(null);
    const chunksRef = useRef<Blob[]>([]);
    const [isRecording, setIsRecording] = useState(false);
    const recordingResolveRef = useRef<((value: { uri: string } | undefined) => void) | null>(null);

    // Initialize camera on mount
    useEffect(() => {
      if (Platform.OS !== "web") return;

      initializeCamera();

      return () => {
        // Cleanup
        if (streamRef.current) {
          streamRef.current.getTracks().forEach(track => track.stop());
        }
      };
    }, [facing]);

    async function initializeCamera() {
      try {
        const constraints: MediaStreamConstraints = {
          video: {
            facingMode: facing === "front" ? "user" : "environment",
            width: { ideal: 1280 },
            height: { ideal: 720 },
          },
          audio: mode === "video",
        };

        const stream = await navigator.mediaDevices.getUserMedia(constraints);
        streamRef.current = stream;

        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play();
        }

        onCameraReady?.();
      } catch (error) {
        console.error("Error initializing camera:", error);
      }
    }

    const startRecording = useCallback((maxDuration?: number): Promise<{ uri: string } | undefined> => {
      return new Promise((resolve) => {
        if (!streamRef.current) {
          resolve(undefined);
          return;
        }

        chunksRef.current = [];
        recordingResolveRef.current = resolve;

        // Use webm format which is well supported in browsers
        const mimeType = MediaRecorder.isTypeSupported("video/webm;codecs=vp9,opus")
          ? "video/webm;codecs=vp9,opus"
          : MediaRecorder.isTypeSupported("video/webm;codecs=vp8,opus")
          ? "video/webm;codecs=vp8,opus"
          : "video/webm";

        const mediaRecorder = new MediaRecorder(streamRef.current, {
          mimeType,
          videoBitsPerSecond: 2500000, // 2.5 Mbps
        });

        mediaRecorder.ondataavailable = (event) => {
          if (event.data.size > 0) {
            chunksRef.current.push(event.data);
          }
        };

        mediaRecorder.onstop = () => {
          const blob = new Blob(chunksRef.current, { type: mimeType });
          const uri = URL.createObjectURL(blob);
          setIsRecording(false);

          if (recordingResolveRef.current) {
            recordingResolveRef.current({ uri });
            recordingResolveRef.current = null;
          }
        };

        mediaRecorderRef.current = mediaRecorder;
        mediaRecorder.start(1000); // Collect data every second
        setIsRecording(true);

        // Auto-stop after maxDuration
        if (maxDuration) {
          setTimeout(() => {
            if (mediaRecorderRef.current?.state === "recording") {
              mediaRecorderRef.current.stop();
            }
          }, maxDuration * 1000);
        }
      });
    }, []);

    const stopRecording = useCallback(() => {
      if (mediaRecorderRef.current?.state === "recording") {
        mediaRecorderRef.current.stop();
      }
    }, []);

    const takePicture = useCallback((): Promise<{ uri: string } | undefined> => {
      return new Promise((resolve) => {
        if (!videoRef.current) {
          resolve(undefined);
          return;
        }

        const canvas = document.createElement("canvas");
        canvas.width = videoRef.current.videoWidth;
        canvas.height = videoRef.current.videoHeight;

        const ctx = canvas.getContext("2d");
        if (!ctx) {
          resolve(undefined);
          return;
        }

        // Mirror the image if using front camera
        if (facing === "front") {
          ctx.translate(canvas.width, 0);
          ctx.scale(-1, 1);
        }

        ctx.drawImage(videoRef.current, 0, 0);

        canvas.toBlob((blob) => {
          if (blob) {
            const uri = URL.createObjectURL(blob);
            resolve({ uri });
          } else {
            resolve(undefined);
          }
        }, "image/jpeg", 0.9);
      });
    }, [facing]);

    useImperativeHandle(ref, () => ({
      recordAsync: (options) => startRecording(options?.maxDuration),
      stopRecording,
      takePictureAsync: takePicture,
    }));

    if (Platform.OS !== "web") {
      // This component only works on web
      return null;
    }

    return (
      <View style={[styles.container, style]}>
        <video
          ref={videoRef}
          style={{
            ...StyleSheet.absoluteFillObject,
            objectFit: "cover",
            transform: facing === "front" ? "scaleX(-1)" : undefined,
          } as any}
          autoPlay
          playsInline
          muted
        />
        {children}
      </View>
    );
  }
);

// Web-compatible permission request functions
export async function requestWebCameraPermissions(): Promise<{ status: "granted" | "denied" }> {
  if (Platform.OS !== "web") {
    return { status: "denied" };
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    stream.getTracks().forEach(track => track.stop());
    return { status: "granted" };
  } catch (error) {
    console.error("Permission denied:", error);
    return { status: "denied" };
  }
}

export async function checkWebCameraPermissions(): Promise<{ status: "granted" | "denied" | "undetermined" }> {
  if (Platform.OS !== "web") {
    return { status: "denied" };
  }

  try {
    const result = await navigator.permissions.query({ name: "camera" as PermissionName });
    if (result.state === "granted") {
      return { status: "granted" };
    } else if (result.state === "denied") {
      return { status: "denied" };
    }
    return { status: "undetermined" };
  } catch (error) {
    // Fallback for browsers that don't support permissions query
    return { status: "undetermined" };
  }
}

const styles = StyleSheet.create({
  container: {
    overflow: "hidden",
    backgroundColor: "#000",
  },
});

export default WebCamera;
