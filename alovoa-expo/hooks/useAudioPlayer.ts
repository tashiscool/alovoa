import { useState, useRef, useCallback, useEffect } from "react";
import { Platform } from "react-native";
import { Audio, AVPlaybackStatus } from "expo-av";

interface PlaybackState {
  isLoaded: boolean;
  isPlaying: boolean;
  isBuffering: boolean;
  positionMs: number;
  durationMs: number;
  playbackRate: number;
}

interface Marker {
  id: string;
  positionMs: number;
  label?: string;
}

interface UseAudioPlayerReturn extends PlaybackState {
  load: (uri: string) => Promise<void>;
  play: () => Promise<void>;
  pause: () => Promise<void>;
  stop: () => Promise<void>;
  seekTo: (positionMs: number) => Promise<void>;
  seekRelative: (deltaMs: number) => Promise<void>;
  setRate: (rate: number) => Promise<void>;
  unload: () => Promise<void>;
  // Marker support
  markers: Marker[];
  addMarker: (label?: string) => void;
  removeMarker: (id: string) => void;
  seekToMarker: (id: string) => Promise<void>;
}

/**
 * Cross-platform audio player hook.
 * Uses expo-av on native, HTMLAudioElement on web.
 *
 * Pattern: Store → Play with markers and seeking
 */
export function useAudioPlayer(): UseAudioPlayerReturn {
  const [state, setState] = useState<PlaybackState>({
    isLoaded: false,
    isPlaying: false,
    isBuffering: false,
    positionMs: 0,
    durationMs: 0,
    playbackRate: 1,
  });

  const [markers, setMarkers] = useState<Marker[]>([]);

  // Native (expo-av)
  const soundRef = useRef<Audio.Sound | null>(null);

  // Web (HTMLAudioElement)
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const updateIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      soundRef.current?.unloadAsync().catch(() => {});
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current.src = "";
      }
      if (updateIntervalRef.current) {
        clearInterval(updateIntervalRef.current);
      }
    };
  }, []);

  const onPlaybackStatusUpdate = useCallback((status: AVPlaybackStatus) => {
    if (!status.isLoaded) {
      setState((s) => ({ ...s, isLoaded: false, isBuffering: false }));
      return;
    }

    setState((s) => ({
      ...s,
      isLoaded: true,
      isPlaying: status.isPlaying,
      isBuffering: status.isBuffering,
      positionMs: status.positionMillis,
      durationMs: status.durationMillis ?? 0,
      playbackRate: status.rate,
    }));

    // Handle playback finished
    if (status.didJustFinish) {
      setState((s) => ({ ...s, isPlaying: false, positionMs: 0 }));
    }
  }, []);

  const load = useCallback(async (uri: string) => {
    // Unload previous
    await unload();

    if (Platform.OS === "web") {
      await loadWeb(uri);
    } else {
      await loadNative(uri);
    }
  }, []);

  const loadNative = async (uri: string) => {
    try {
      await Audio.setAudioModeAsync({
        playsInSilentModeIOS: true,
        staysActiveInBackground: true,
      });

      const { sound } = await Audio.Sound.createAsync(
        { uri },
        { shouldPlay: false },
        onPlaybackStatusUpdate
      );

      soundRef.current = sound;

      const status = await sound.getStatusAsync();
      if (status.isLoaded) {
        setState((s) => ({
          ...s,
          isLoaded: true,
          durationMs: status.durationMillis ?? 0,
        }));
      }
    } catch (error) {
      console.error("Failed to load audio:", error);
      throw error;
    }
  };

  const loadWeb = async (uri: string) => {
    return new Promise<void>((resolve, reject) => {
      const audio = new window.Audio(uri);
      audio.preload = "metadata";

      audio.onloadedmetadata = () => {
        setState((s) => ({
          ...s,
          isLoaded: true,
          durationMs: audio.duration * 1000,
        }));
        resolve();
      };

      audio.onplay = () => {
        setState((s) => ({ ...s, isPlaying: true }));
      };

      audio.onpause = () => {
        setState((s) => ({ ...s, isPlaying: false }));
      };

      audio.onended = () => {
        setState((s) => ({ ...s, isPlaying: false, positionMs: 0 }));
      };

      audio.onerror = () => {
        reject(new Error("Failed to load audio"));
      };

      audioRef.current = audio;

      // Position tracking
      updateIntervalRef.current = setInterval(() => {
        if (audioRef.current && !audioRef.current.paused) {
          setState((s) => ({
            ...s,
            positionMs: audioRef.current!.currentTime * 1000,
          }));
        }
      }, 100);
    });
  };

  const play = useCallback(async () => {
    if (Platform.OS === "web") {
      await audioRef.current?.play();
    } else {
      await soundRef.current?.playAsync();
    }
  }, []);

  const pause = useCallback(async () => {
    if (Platform.OS === "web") {
      audioRef.current?.pause();
    } else {
      await soundRef.current?.pauseAsync();
    }
  }, []);

  const stop = useCallback(async () => {
    if (Platform.OS === "web") {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current.currentTime = 0;
      }
    } else {
      await soundRef.current?.stopAsync();
    }
    setState((s) => ({ ...s, isPlaying: false, positionMs: 0 }));
  }, []);

  const seekTo = useCallback(async (positionMs: number) => {
    const clampedMs = Math.max(0, Math.min(positionMs, state.durationMs));

    if (Platform.OS === "web") {
      if (audioRef.current) {
        audioRef.current.currentTime = clampedMs / 1000;
        setState((s) => ({ ...s, positionMs: clampedMs }));
      }
    } else {
      await soundRef.current?.setPositionAsync(clampedMs);
    }
  }, [state.durationMs]);

  const seekRelative = useCallback(async (deltaMs: number) => {
    await seekTo(state.positionMs + deltaMs);
  }, [state.positionMs, seekTo]);

  const setRate = useCallback(async (rate: number) => {
    const clampedRate = Math.max(0.5, Math.min(2, rate));

    if (Platform.OS === "web") {
      if (audioRef.current) {
        audioRef.current.playbackRate = clampedRate;
        setState((s) => ({ ...s, playbackRate: clampedRate }));
      }
    } else {
      await soundRef.current?.setRateAsync(clampedRate, true);
    }
  }, []);

  const unload = useCallback(async () => {
    if (updateIntervalRef.current) {
      clearInterval(updateIntervalRef.current);
      updateIntervalRef.current = null;
    }

    if (Platform.OS === "web") {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current.src = "";
        audioRef.current = null;
      }
    } else {
      await soundRef.current?.unloadAsync();
      soundRef.current = null;
    }

    setState({
      isLoaded: false,
      isPlaying: false,
      isBuffering: false,
      positionMs: 0,
      durationMs: 0,
      playbackRate: 1,
    });
    setMarkers([]);
  }, []);

  // Marker functions
  const addMarker = useCallback((label?: string) => {
    const marker: Marker = {
      id: `marker-${Date.now()}`,
      positionMs: state.positionMs,
      label,
    };
    setMarkers((m) => [...m, marker].sort((a, b) => a.positionMs - b.positionMs));
  }, [state.positionMs]);

  const removeMarker = useCallback((id: string) => {
    setMarkers((m) => m.filter((marker) => marker.id !== id));
  }, []);

  const seekToMarker = useCallback(async (id: string) => {
    const marker = markers.find((m) => m.id === id);
    if (marker) {
      await seekTo(marker.positionMs);
    }
  }, [markers, seekTo]);

  return {
    ...state,
    load,
    play,
    pause,
    stop,
    seekTo,
    seekRelative,
    setRate,
    unload,
    markers,
    addMarker,
    removeMarker,
    seekToMarker,
  };
}

/**
 * Helper: Format duration for display
 */
export function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, "0")}:${seconds
      .toString()
      .padStart(2, "0")}`;
  }
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/**
 * Helper: Calculate progress percentage
 */
export function getProgress(positionMs: number, durationMs: number): number {
  if (durationMs === 0) return 0;
  return (positionMs / durationMs) * 100;
}
