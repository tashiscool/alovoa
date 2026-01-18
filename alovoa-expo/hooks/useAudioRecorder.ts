import { useState, useRef, useCallback, useEffect } from "react";
import { Platform } from "react-native";
import { Audio, AVPlaybackStatus } from "expo-av";
import * as FileSystem from "expo-file-system";

export interface RecordingResult {
  uri: string;
  durationMs: number;
  blob?: Blob; // Only available on web
}

interface AudioRecorderState {
  isRecording: boolean;
  isPaused: boolean;
  durationMs: number;
  meterLevel: number; // 0-1 for visualizations
}

interface UseAudioRecorderReturn extends AudioRecorderState {
  start: () => Promise<void>;
  stop: () => Promise<RecordingResult | null>;
  pause: () => Promise<void>;
  resume: () => Promise<void>;
  cancel: () => Promise<void>;
}

/**
 * Cross-platform audio recorder hook.
 * Uses expo-av on native, MediaRecorder on web.
 *
 * Pattern: Record → returns URI/Blob for transcription
 */
export function useAudioRecorder(): UseAudioRecorderReturn {
  const [state, setState] = useState<AudioRecorderState>({
    isRecording: false,
    isPaused: false,
    durationMs: 0,
    meterLevel: 0,
  });

  // Native (expo-av)
  const recordingRef = useRef<Audio.Recording | null>(null);
  const meterIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Web (MediaRecorder)
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const startTimeRef = useRef<number>(0);
  const durationIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (meterIntervalRef.current) clearInterval(meterIntervalRef.current);
      if (durationIntervalRef.current) clearInterval(durationIntervalRef.current);
      recordingRef.current?.stopAndUnloadAsync().catch(() => {});
      mediaRecorderRef.current?.stop();
      streamRef.current?.getTracks().forEach((track) => track.stop());
    };
  }, []);

  const start = useCallback(async () => {
    if (Platform.OS === "web") {
      await startWeb();
    } else {
      await startNative();
    }
  }, []);

  const startNative = async () => {
    try {
      // Request permissions
      const { granted } = await Audio.requestPermissionsAsync();
      if (!granted) {
        throw new Error("Audio recording permission denied");
      }

      // Configure audio mode
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true,
      });

      // Create and start recording
      const recording = new Audio.Recording();
      await recording.prepareToRecordAsync(
        Audio.RecordingOptionsPresets.HIGH_QUALITY
      );
      await recording.startAsync();

      recordingRef.current = recording;
      setState((s) => ({ ...s, isRecording: true, isPaused: false, durationMs: 0 }));

      // Start metering
      meterIntervalRef.current = setInterval(async () => {
        if (recordingRef.current) {
          const status = await recordingRef.current.getStatusAsync();
          if (status.isRecording) {
            setState((s) => ({
              ...s,
              durationMs: status.durationMillis,
              meterLevel: Math.min(1, (status.metering ?? -60 + 60) / 60),
            }));
          }
        }
      }, 100);
    } catch (error) {
      console.error("Failed to start native recording:", error);
      throw error;
    }
  };

  const startWeb = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;

      const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
        ? "audio/webm;codecs=opus"
        : "audio/webm";

      const mediaRecorder = new MediaRecorder(stream, { mimeType });
      chunksRef.current = [];

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          chunksRef.current.push(e.data);
        }
      };

      mediaRecorder.start(1000); // Collect data every second
      mediaRecorderRef.current = mediaRecorder;
      startTimeRef.current = Date.now();

      setState((s) => ({ ...s, isRecording: true, isPaused: false, durationMs: 0 }));

      // Track duration
      durationIntervalRef.current = setInterval(() => {
        setState((s) => ({
          ...s,
          durationMs: Date.now() - startTimeRef.current,
        }));
      }, 100);

      // Web Audio API for metering
      const audioContext = new AudioContext();
      const source = audioContext.createMediaStreamSource(stream);
      const analyser = audioContext.createAnalyser();
      analyser.fftSize = 256;
      source.connect(analyser);

      const dataArray = new Uint8Array(analyser.frequencyBinCount);
      const updateMeter = () => {
        if (mediaRecorderRef.current?.state === "recording") {
          analyser.getByteFrequencyData(dataArray);
          const avg = dataArray.reduce((a, b) => a + b, 0) / dataArray.length;
          setState((s) => ({ ...s, meterLevel: avg / 255 }));
          requestAnimationFrame(updateMeter);
        }
      };
      updateMeter();
    } catch (error) {
      console.error("Failed to start web recording:", error);
      throw error;
    }
  };

  const stop = useCallback(async (): Promise<RecordingResult | null> => {
    if (Platform.OS === "web") {
      return stopWeb();
    } else {
      return stopNative();
    }
  }, []);

  const stopNative = async (): Promise<RecordingResult | null> => {
    if (!recordingRef.current) return null;

    if (meterIntervalRef.current) {
      clearInterval(meterIntervalRef.current);
      meterIntervalRef.current = null;
    }

    try {
      await recordingRef.current.stopAndUnloadAsync();
      const uri = recordingRef.current.getURI();
      const status = await recordingRef.current.getStatusAsync();

      recordingRef.current = null;
      setState({ isRecording: false, isPaused: false, durationMs: 0, meterLevel: 0 });

      // Reset audio mode
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: false,
      });

      if (!uri) return null;

      return {
        uri,
        durationMs: status.durationMillis,
      };
    } catch (error) {
      console.error("Failed to stop native recording:", error);
      return null;
    }
  };

  const stopWeb = async (): Promise<RecordingResult | null> => {
    return new Promise((resolve) => {
      if (!mediaRecorderRef.current) {
        resolve(null);
        return;
      }

      if (durationIntervalRef.current) {
        clearInterval(durationIntervalRef.current);
        durationIntervalRef.current = null;
      }

      const durationMs = Date.now() - startTimeRef.current;

      mediaRecorderRef.current.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: "audio/webm" });
        const uri = URL.createObjectURL(blob);

        // Stop all tracks
        streamRef.current?.getTracks().forEach((track) => track.stop());
        streamRef.current = null;
        mediaRecorderRef.current = null;

        setState({ isRecording: false, isPaused: false, durationMs: 0, meterLevel: 0 });

        resolve({ uri, durationMs, blob });
      };

      mediaRecorderRef.current.stop();
    });
  };

  const pause = useCallback(async () => {
    if (Platform.OS === "web") {
      mediaRecorderRef.current?.pause();
    } else {
      await recordingRef.current?.pauseAsync();
    }
    setState((s) => ({ ...s, isPaused: true }));
  }, []);

  const resume = useCallback(async () => {
    if (Platform.OS === "web") {
      mediaRecorderRef.current?.resume();
    } else {
      await recordingRef.current?.startAsync();
    }
    setState((s) => ({ ...s, isPaused: false }));
  }, []);

  const cancel = useCallback(async () => {
    if (meterIntervalRef.current) clearInterval(meterIntervalRef.current);
    if (durationIntervalRef.current) clearInterval(durationIntervalRef.current);

    if (Platform.OS === "web") {
      mediaRecorderRef.current?.stop();
      streamRef.current?.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
      mediaRecorderRef.current = null;
    } else {
      await recordingRef.current?.stopAndUnloadAsync();
      recordingRef.current = null;
    }

    setState({ isRecording: false, isPaused: false, durationMs: 0, meterLevel: 0 });
  }, []);

  return {
    ...state,
    start,
    stop,
    pause,
    resume,
    cancel,
  };
}
