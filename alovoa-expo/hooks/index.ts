/**
 * Audio Recording & Transcription Hooks
 *
 * Pattern: Record → Transcribe → Store
 *
 * Usage:
 * ```tsx
 * import {
 *   useAudioRecorder,
 *   useTranscription,
 *   useAudioPlayer,
 *   useRecordingStorage,
 * } from "./hooks";
 *
 * function RecordingScreen() {
 *   const recorder = useAudioRecorder();
 *   const transcription = useTranscription();
 *   const player = useAudioPlayer();
 *   const storage = useRecordingStorage();
 *
 *   const handleRecord = async () => {
 *     // Start recording + live transcription (web only)
 *     await recorder.start();
 *     await transcription.startListening();
 *   };
 *
 *   const handleStop = async () => {
 *     transcription.stopListening();
 *     const result = await recorder.stop();
 *
 *     if (result) {
 *       await storage.save({
 *         title: "New Recording",
 *         audioUri: result.uri,
 *         durationMs: result.durationMs,
 *         transcript: transcription.transcript,
 *         segments: transcription.segments,
 *         markers: [],
 *       });
 *     }
 *   };
 *
 *   const handlePlay = async (recording: StoredRecording) => {
 *     await player.load(recording.audioUri);
 *     await player.play();
 *   };
 * }
 * ```
 */

export {
  useAudioRecorder,
  type RecordingResult,
} from "./useAudioRecorder";

export {
  useTranscription,
  formatTimestamp,
  groupSegments,
  type TranscriptSegment,
} from "./useTranscription";

export {
  useAudioPlayer,
  formatDuration,
  getProgress,
} from "./useAudioPlayer";

export {
  useRecordingStorage,
  type StoredRecording,
} from "./useRecordingStorage";
