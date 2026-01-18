import React from "react";
import {
  View,
  StyleSheet,
  Pressable,
  Image,
  Dimensions,
} from "react-native";
import { Text, Button, Surface, useTheme, IconButton } from "react-native-paper";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import { Video, ResizeMode, AVPlaybackStatus } from "expo-av";
import * as Global from "../Global";
import * as URL from "../URL";

interface VideoFirstDisplayProps {
  profileUuid: string;
  videoUrl?: string;
  thumbnailUrl?: string;
  videoDuration?: number;
  videoWatchRequired: boolean;
  videoWatched: boolean;
  photos: string[];
  onVideoWatched?: () => void;
}

const SCREEN_WIDTH = Dimensions.get("window").width;

/**
 * Video-First Display Component
 * Shows video introduction prominently with blurred photos until video is watched.
 * Part of AURA's authenticity-first design philosophy.
 */
const VideoFirstDisplay: React.FC<VideoFirstDisplayProps> = ({
  profileUuid,
  videoUrl,
  thumbnailUrl,
  videoDuration,
  videoWatchRequired,
  videoWatched: initialVideoWatched,
  photos,
  onVideoWatched,
}) => {
  const { colors } = useTheme();
  const videoRef = React.useRef<Video>(null);

  const [videoWatched, setVideoWatched] = React.useState(initialVideoWatched);
  const [isPlaying, setIsPlaying] = React.useState(false);
  const [showVideo, setShowVideo] = React.useState(true);
  const [watchProgress, setWatchProgress] = React.useState(0);
  const [hasStartedWatching, setHasStartedWatching] = React.useState(false);

  // Minimum percentage to consider "watched"
  const WATCH_THRESHOLD = 0.7; // 70%

  const shouldBlurPhotos = videoWatchRequired && !videoWatched && videoUrl;

  // Record watch start when video plays
  const handlePlaybackStatusUpdate = async (status: AVPlaybackStatus) => {
    if (!status.isLoaded) return;

    if (status.isPlaying && !hasStartedWatching) {
      setHasStartedWatching(true);
      // Record watch start
      try {
        await Global.Fetch(Global.format(URL.API_VIDEO_FIRST_WATCH, profileUuid), "post");
      } catch (e) {
        console.log("Failed to record video watch start", e);
      }
    }

    // Track progress
    if (status.durationMillis && status.positionMillis) {
      const progress = status.positionMillis / status.durationMillis;
      setWatchProgress(progress);

      // Check if threshold reached
      if (progress >= WATCH_THRESHOLD && !videoWatched) {
        markAsWatched(Math.floor(status.positionMillis / 1000));
      }
    }

    // Video completed
    if (status.didJustFinish && !videoWatched) {
      markAsWatched(Math.floor((status.durationMillis || 0) / 1000));
    }

    setIsPlaying(status.isPlaying);
  };

  const markAsWatched = async (durationSeconds: number) => {
    setVideoWatched(true);
    onVideoWatched?.();

    // Record completion
    try {
      await Global.Fetch(
        Global.format(URL.API_VIDEO_FIRST_PROGRESS, profileUuid),
        "post",
        { durationSeconds, completed: true },
        "application/json"
      );
    } catch (e) {
      console.log("Failed to record video completion", e);
    }
  };

  const playVideo = async () => {
    if (videoRef.current) {
      await videoRef.current.playAsync();
      setIsPlaying(true);
    }
  };

  const pauseVideo = async () => {
    if (videoRef.current) {
      await videoRef.current.pauseAsync();
      setIsPlaying(false);
    }
  };

  // Format duration for display
  const formatDuration = (seconds?: number) => {
    if (!seconds) return "";
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, "0")}`;
  };

  return (
    <View style={styles.container}>
      {/* Video Section - Always shown first */}
      {videoUrl && showVideo && (
        <Surface style={[styles.videoSection, { backgroundColor: colors.surfaceVariant }]}>
          <View style={styles.videoHeader}>
            <MaterialCommunityIcons name="video" size={20} color={colors.primary} />
            <Text style={[styles.videoTitle, { color: colors.onSurface }]}>
              Video Introduction
            </Text>
            {videoDuration && (
              <Text style={[styles.videoDuration, { color: colors.onSurfaceVariant }]}>
                {formatDuration(videoDuration)}
              </Text>
            )}
          </View>

          <View style={styles.videoContainer}>
            <Video
              ref={videoRef}
              source={{ uri: videoUrl }}
              style={styles.video}
              useNativeControls
              resizeMode={ResizeMode.COVER}
              posterSource={{ uri: thumbnailUrl }}
              usePoster={!isPlaying}
              onPlaybackStatusUpdate={handlePlaybackStatusUpdate}
            />

            {!isPlaying && (
              <Pressable style={styles.playOverlay} onPress={playVideo}>
                <View style={[styles.playButton, { backgroundColor: colors.primary }]}>
                  <MaterialCommunityIcons name="play" size={40} color="white" />
                </View>
              </Pressable>
            )}
          </View>

          {/* Progress indicator */}
          {hasStartedWatching && !videoWatched && (
            <View style={styles.progressContainer}>
              <View style={[styles.progressBar, { backgroundColor: colors.surfaceVariant }]}>
                <View
                  style={[
                    styles.progressFill,
                    {
                      backgroundColor: colors.primary,
                      width: `${Math.min(watchProgress * 100, 100)}%`,
                    },
                  ]}
                />
              </View>
              <Text style={[styles.progressText, { color: colors.onSurfaceVariant }]}>
                {Math.floor(watchProgress * 100)}% watched
                {watchProgress < WATCH_THRESHOLD && ` (${Math.floor(WATCH_THRESHOLD * 100)}% to unlock photos)`}
              </Text>
            </View>
          )}

          {videoWatched && (
            <View style={styles.watchedBadge}>
              <MaterialCommunityIcons name="check-circle" size={16} color={colors.primary} />
              <Text style={[styles.watchedText, { color: colors.primary }]}>Video watched</Text>
            </View>
          )}
        </Surface>
      )}

      {/* Photos Section */}
      {photos.length > 0 && (
        <View style={styles.photosSection}>
          {/* Video-first banner when photos are blurred */}
          {shouldBlurPhotos && (
            <Surface style={[styles.blurBanner, { backgroundColor: colors.primaryContainer }]}>
              <MaterialCommunityIcons name="video-account" size={24} color={colors.primary} />
              <View style={styles.blurBannerText}>
                <Text style={[styles.blurTitle, { color: colors.onPrimaryContainer }]}>
                  Watch the video first
                </Text>
                <Text style={[styles.blurSubtitle, { color: colors.onPrimaryContainer }]}>
                  Get to know them through their introduction before seeing photos
                </Text>
              </View>
            </Surface>
          )}

          {/* Photo grid */}
          <View style={styles.photoGrid}>
            {photos.map((photo, index) => (
              <View key={index} style={styles.photoWrapper}>
                <Image
                  source={{ uri: photo }}
                  style={[
                    styles.photo,
                    shouldBlurPhotos && styles.blurredPhoto,
                  ]}
                  blurRadius={shouldBlurPhotos ? 25 : 0}
                />
                {shouldBlurPhotos && (
                  <View style={styles.photoOverlay}>
                    <MaterialCommunityIcons name="lock" size={24} color="white" />
                  </View>
                )}
              </View>
            ))}
          </View>
        </View>
      )}

      {/* No video fallback */}
      {!videoUrl && photos.length > 0 && (
        <View style={styles.noVideoNote}>
          <MaterialCommunityIcons name="video-off" size={16} color={colors.onSurfaceVariant} />
          <Text style={[styles.noVideoText, { color: colors.onSurfaceVariant }]}>
            No video introduction
          </Text>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  videoSection: {
    borderRadius: 12,
    marginBottom: 16,
    overflow: "hidden",
  },
  videoHeader: {
    flexDirection: "row",
    alignItems: "center",
    padding: 12,
    gap: 8,
  },
  videoTitle: {
    fontSize: 16,
    fontWeight: "600",
    flex: 1,
  },
  videoDuration: {
    fontSize: 14,
  },
  videoContainer: {
    width: "100%",
    aspectRatio: 9 / 16,
    maxHeight: 400,
    position: "relative",
  },
  video: {
    width: "100%",
    height: "100%",
  },
  playOverlay: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.3)",
  },
  playButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    justifyContent: "center",
    alignItems: "center",
  },
  progressContainer: {
    padding: 12,
  },
  progressBar: {
    height: 4,
    borderRadius: 2,
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
    borderRadius: 2,
  },
  progressText: {
    fontSize: 12,
    marginTop: 6,
    textAlign: "center",
  },
  watchedBadge: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    padding: 8,
    gap: 6,
  },
  watchedText: {
    fontSize: 14,
    fontWeight: "500",
  },
  photosSection: {
    marginTop: 8,
  },
  blurBanner: {
    flexDirection: "row",
    alignItems: "center",
    padding: 16,
    borderRadius: 12,
    marginBottom: 12,
    gap: 12,
  },
  blurBannerText: {
    flex: 1,
  },
  blurTitle: {
    fontSize: 16,
    fontWeight: "600",
  },
  blurSubtitle: {
    fontSize: 13,
    marginTop: 2,
    opacity: 0.9,
  },
  photoGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  photoWrapper: {
    width: (SCREEN_WIDTH - 48) / 3,
    aspectRatio: 1,
    borderRadius: 8,
    overflow: "hidden",
    position: "relative",
  },
  photo: {
    width: "100%",
    height: "100%",
  },
  blurredPhoto: {
    opacity: 0.8,
  },
  photoOverlay: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.4)",
  },
  noVideoNote: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    padding: 12,
    gap: 6,
  },
  noVideoText: {
    fontSize: 13,
  },
});

export default VideoFirstDisplay;
