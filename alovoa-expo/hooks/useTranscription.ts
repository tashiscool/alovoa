import { useState, useRef, useCallback, useEffect } from "react";
import { Platform } from "react-native";

export interface TranscriptSegment {
  text: string;
  startMs: number;
  endMs: number;
  confidence: number;
  isFinal: boolean;
}

interface TranscriptionState {
  isListening: boolean;
  transcript: string;
  segments: TranscriptSegment[];
  interimText: string; // Current non-final text
  error: string | null;
}

interface UseTranscriptionReturn extends TranscriptionState {
  startListening: () => Promise<void>;
  stopListening: () => void;
  reset: () => void;
  // For file-based transcription (post-recording)
  transcribeAudio: (uri: string) => Promise<TranscriptSegment[]>;
}

// Web Speech API types
interface SpeechRecognitionEvent {
  resultIndex: number;
  results: SpeechRecognitionResultList;
}

interface SpeechRecognitionResultList {
  length: number;
  item(index: number): SpeechRecognitionResult;
  [index: number]: SpeechRecognitionResult;
}

interface SpeechRecognitionResult {
  isFinal: boolean;
  length: number;
  item(index: number): SpeechRecognitionAlternative;
  [index: number]: SpeechRecognitionAlternative;
}

interface SpeechRecognitionAlternative {
  transcript: string;
  confidence: number;
}

interface SpeechRecognition extends EventTarget {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start(): void;
  stop(): void;
  abort(): void;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  onstart: (() => void) | null;
}

declare global {
  interface Window {
    SpeechRecognition: new () => SpeechRecognition;
    webkitSpeechRecognition: new () => SpeechRecognition;
  }
}

/**
 * Cross-platform transcription hook.
 * Uses Web Speech API on web (FREE, runs in browser).
 * On native, supports file-based transcription via Whisper API.
 *
 * Pattern: Transcribe → returns timestamped segments
 */
export function useTranscription(language = "en-US"): UseTranscriptionReturn {
  const [state, setState] = useState<TranscriptionState>({
    isListening: false,
    transcript: "",
    segments: [],
    interimText: "",
    error: null,
  });

  const recognitionRef = useRef<SpeechRecognition | null>(null);
  const segmentStartRef = useRef<number>(0);
  const sessionStartRef = useRef<number>(0);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      recognitionRef.current?.stop();
    };
  }, []);

  const startListening = useCallback(async () => {
    if (Platform.OS !== "web") {
      setState((s) => ({
        ...s,
        error: "Live transcription only available on web. Use transcribeAudio for recordings.",
      }));
      return;
    }

    const SpeechRecognition =
      window.SpeechRecognition || window.webkitSpeechRecognition;

    if (!SpeechRecognition) {
      setState((s) => ({
        ...s,
        error: "Speech recognition not supported in this browser",
      }));
      return;
    }

    try {
      const recognition = new SpeechRecognition();
      recognition.continuous = true;
      recognition.interimResults = true;
      recognition.lang = language;

      sessionStartRef.current = Date.now();
      segmentStartRef.current = Date.now();

      recognition.onstart = () => {
        setState((s) => ({ ...s, isListening: true, error: null }));
      };

      recognition.onresult = (event: SpeechRecognitionEvent) => {
        let interimTranscript = "";
        let finalTranscript = "";
        const newSegments: TranscriptSegment[] = [];

        for (let i = event.resultIndex; i < event.results.length; i++) {
          const result = event.results[i];
          const text = result[0].transcript;
          const confidence = result[0].confidence;

          if (result.isFinal) {
            finalTranscript += text;
            const now = Date.now();
            newSegments.push({
              text: text.trim(),
              startMs: segmentStartRef.current - sessionStartRef.current,
              endMs: now - sessionStartRef.current,
              confidence,
              isFinal: true,
            });
            segmentStartRef.current = now;
          } else {
            interimTranscript += text;
          }
        }

        setState((s) => ({
          ...s,
          transcript: s.transcript + finalTranscript,
          segments: [...s.segments, ...newSegments],
          interimText: interimTranscript,
        }));
      };

      recognition.onerror = (event) => {
        // "no-speech" and "aborted" are not real errors
        if (event.error !== "no-speech" && event.error !== "aborted") {
          setState((s) => ({ ...s, error: event.error }));
        }
      };

      recognition.onend = () => {
        setState((s) => ({ ...s, isListening: false, interimText: "" }));
      };

      recognition.start();
      recognitionRef.current = recognition;
    } catch (error) {
      setState((s) => ({
        ...s,
        error: error instanceof Error ? error.message : "Failed to start",
      }));
    }
  }, [language]);

  const stopListening = useCallback(() => {
    recognitionRef.current?.stop();
    recognitionRef.current = null;
    setState((s) => ({ ...s, isListening: false, interimText: "" }));
  }, []);

  const reset = useCallback(() => {
    recognitionRef.current?.stop();
    recognitionRef.current = null;
    setState({
      isListening: false,
      transcript: "",
      segments: [],
      interimText: "",
      error: null,
    });
  }, []);

  /**
   * Transcribe an audio file.
   * On web: Uses Web Speech API with audio playback trick or falls back to upload.
   * On native: Could use Whisper.cpp or cloud API.
   *
   * For now, this is a placeholder that returns empty segments.
   * Integrate with your preferred transcription service.
   */
  const transcribeAudio = useCallback(
    async (uri: string): Promise<TranscriptSegment[]> => {
      // Option 1: Use Whisper API (costs money but accurate)
      // Option 2: Use browser's built-in (free but needs live audio)
      // Option 3: Use Whisper.cpp WASM (free but CPU-intensive)

      console.log("Transcribe audio:", uri);

      // Placeholder: Return demo segments
      // In production, integrate with:
      // - OpenAI Whisper API: https://platform.openai.com/docs/guides/speech-to-text
      // - Whisper.cpp WASM: https://github.com/nicobytes/whisper-web
      // - AssemblyAI, Deepgram, etc.

      return [
        {
          text: "Transcription placeholder - integrate with Whisper API or Whisper.cpp WASM",
          startMs: 0,
          endMs: 5000,
          confidence: 1,
          isFinal: true,
        },
      ];
    },
    []
  );

  return {
    ...state,
    startListening,
    stopListening,
    reset,
    transcribeAudio,
  };
}

/**
 * Helper: Format milliseconds to MM:SS
 */
export function formatTimestamp(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/**
 * Helper: Group segments into paragraphs by pause duration
 */
export function groupSegments(
  segments: TranscriptSegment[],
  pauseThresholdMs = 2000
): string[] {
  const paragraphs: string[] = [];
  let currentParagraph: string[] = [];

  for (let i = 0; i < segments.length; i++) {
    currentParagraph.push(segments[i].text);

    const nextSegment = segments[i + 1];
    if (nextSegment) {
      const pause = nextSegment.startMs - segments[i].endMs;
      if (pause > pauseThresholdMs) {
        paragraphs.push(currentParagraph.join(" "));
        currentParagraph = [];
      }
    }
  }

  if (currentParagraph.length > 0) {
    paragraphs.push(currentParagraph.join(" "));
  }

  return paragraphs;
}
