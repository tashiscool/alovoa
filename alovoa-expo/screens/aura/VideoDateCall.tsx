import React from "react";
import {
  View,
  useWindowDimensions,
  Pressable,
  StyleSheet,
  Platform,
} from "react-native";
import {
  Text,
  Button,
  ActivityIndicator,
  useTheme,
  IconButton,
  Surface,
  Avatar,
} from "react-native-paper";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import * as Global from "../../Global";
import * as URL from "../../URL";
import * as I18N from "../../i18n";
import { VideoDate, VideoDateStatus } from "../../myTypes";
import { STATUS_BAR_HEIGHT } from "../../assets/styles";

// Platform-specific imports
import WebCamera, { WebCameraRef, requestWebCameraPermissions } from "../../components/WebCamera";

// Native imports (conditional)
let Camera: any;
let CameraView: any;
let Audio: any;

if (Platform.OS !== "web") {
  Camera = require("expo-camera").Camera;
  CameraView = require("expo-camera").CameraView;
  Audio = require("expo-av").Audio;
}

type CameraType = "front" | "back";

const i18n = I18N.getI18n();

enum CallState {
  CONNECTING,
  WAITING,
  CONNECTED,
  ENDED,
}

const VideoDateCall = ({ route, navigation }: any) => {
  const { colors } = useTheme();
  const { height, width } = useWindowDimensions();

  const { videoDateId } = route.params || {};

  // Use different refs for web vs native
  const nativeCameraRef = React.useRef<any>(null);
  const webCameraRef = React.useRef<WebCameraRef>(null);
  const cameraRef = Platform.OS === "web" ? webCameraRef : nativeCameraRef;
  const timerRef = React.useRef<NodeJS.Timeout | null>(null);

  const [callState, setCallState] = React.useState<CallState>(CallState.CONNECTING);
  const [videoDate, setVideoDate] = React.useState<VideoDate | null>(null);
  const [hasPermission, setHasPermission] = React.useState<boolean | null>(null);
  const [cameraType, setCameraType] = React.useState<CameraType>("front");
  const [isMuted, setIsMuted] = React.useState(false);
  const [isVideoEnabled, setIsVideoEnabled] = React.useState(true);
  const [callDuration, setCallDuration] = React.useState(0);
  const [partnerConnected, setPartnerConnected] = React.useState(false);

  React.useEffect(() => {
    requestPermissions();
    joinCall();

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
      leaveCall();
    };
  }, []);

  React.useEffect(() => {
    if (callState === CallState.CONNECTED) {
      // Start timer
      timerRef.current = setInterval(() => {
        setCallDuration(prev => prev + 1);
      }, 1000);
    } else if (timerRef.current) {
      clearInterval(timerRef.current);
    }
  }, [callState]);

  async function requestPermissions() {
    if (Platform.OS === "web") {
      const result = await requestWebCameraPermissions();
      setHasPermission(result.status === "granted");
    } else {
      const [cameraStatus, audioStatus] = await Promise.all([
        Camera.requestCameraPermissionsAsync(),
        Audio.requestPermissionsAsync(),
      ]);
      setHasPermission(
        cameraStatus.status === "granted" && audioStatus.status === "granted"
      );
    }
  }

  async function joinCall() {
    try {
      const response = await Global.Fetch(Global.format(URL.API_VIDEO_DATE_JOIN, videoDateId), 'post');
      setVideoDate(response.data);

      // In a real app, this would connect to a WebRTC signaling server
      // For now, we'll simulate the connection
      setCallState(CallState.WAITING);

      // Simulate partner connecting after a few seconds
      // In production, this would be handled by WebRTC/signaling
      setTimeout(() => {
        setPartnerConnected(true);
        setCallState(CallState.CONNECTED);
      }, 3000);
    } catch (e) {
      console.error(e);
      Global.ShowToast("Failed to join call");
      navigation.goBack();
    }
  }

  async function leaveCall() {
    try {
      await Global.Fetch(Global.format(URL.API_VIDEO_DATE_LEAVE, videoDateId), 'post');
    } catch (e) {
      console.error(e);
    }
  }

  function endCall() {
    setCallState(CallState.ENDED);
    if (timerRef.current) {
      clearInterval(timerRef.current);
    }
  }

  function formatDuration(seconds: number): string {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  function flipCamera() {
    setCameraType(prev => prev === "front" ? "back" : "front");
  }

  function goToFeedback() {
    navigation.replace("VideoDate.Feedback", { videoDateId });
  }

  // Permission denied
  if (hasPermission === false) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#000', padding: 24 }}>
        <MaterialCommunityIcons name="camera-off" size={64} color="white" />
        <Text style={{ fontSize: 18, marginTop: 16, textAlign: 'center', color: 'white' }}>
          Camera and microphone permissions are required for video calls.
        </Text>
        <Button mode="contained" onPress={requestPermissions} style={{ marginTop: 24 }}>
          Grant Permissions
        </Button>
      </View>
    );
  }

  // Call ended screen
  if (callState === CallState.ENDED) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: colors.background, padding: 24 }}>
        <MaterialCommunityIcons name="phone-hangup" size={64} color={colors.primary} />
        <Text style={{ fontSize: 24, fontWeight: '600', marginTop: 16 }}>
          Call Ended
        </Text>
        <Text style={{ color: colors.onSurfaceVariant, marginTop: 8 }}>
          Duration: {formatDuration(callDuration)}
        </Text>

        <Button
          mode="contained"
          onPress={goToFeedback}
          style={{ marginTop: 32 }}
          icon="star"
        >
          Leave Feedback
        </Button>

        <Button
          mode="text"
          onPress={() => navigation.goBack()}
          style={{ marginTop: 12 }}
        >
          Skip Feedback
        </Button>
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: '#000' }}>
      {/* Partner Video (Placeholder - would be WebRTC stream) */}
      <View style={styles.partnerVideo}>
        {callState === CallState.CONNECTED && partnerConnected ? (
          <View style={{ flex: 1, backgroundColor: '#1F2937', justifyContent: 'center', alignItems: 'center' }}>
            {/* In production, this would show the partner's video stream */}
            <Avatar.Text
              size={120}
              label={videoDate?.partnerName?.substring(0, 2).toUpperCase() || "?"}
            />
            <Text style={{ color: 'white', marginTop: 16, fontSize: 18 }}>
              {videoDate?.partnerName}
            </Text>
          </View>
        ) : (
          <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
            <ActivityIndicator size="large" color="white" />
            <Text style={{ color: 'white', marginTop: 16 }}>
              {callState === CallState.CONNECTING ? 'Connecting...' : 'Waiting for partner...'}
            </Text>
          </View>
        )}
      </View>

      {/* Self View (Picture-in-Picture) */}
      {isVideoEnabled && (
        <View style={styles.selfView}>
          {Platform.OS === "web" ? (
            <WebCamera
              ref={webCameraRef}
              style={{ flex: 1 }}
              facing={cameraType}
            />
          ) : (
            <CameraView
              ref={nativeCameraRef}
              style={{ flex: 1 }}
              facing={cameraType}
            />
          )}
        </View>
      )}

      {/* Top Bar */}
      <View style={styles.topBar}>
        <View style={styles.callInfo}>
          {callState === CallState.CONNECTED && (
            <>
              <View style={styles.liveIndicator} />
              <Text style={{ color: 'white', fontWeight: '600' }}>
                {formatDuration(callDuration)}
              </Text>
            </>
          )}
        </View>

        <Pressable onPress={flipCamera} style={styles.flipButton}>
          <MaterialCommunityIcons name="camera-flip" size={24} color="white" />
        </Pressable>
      </View>

      {/* Partner Name Overlay */}
      {videoDate && (
        <View style={styles.nameOverlay}>
          <Text style={{ color: 'white', fontSize: 18, fontWeight: '600' }}>
            {videoDate.partnerName}
          </Text>
        </View>
      )}

      {/* Controls */}
      <View style={styles.controls}>
        <Surface style={styles.controlsInner} elevation={4}>
          {/* Mute */}
          <Pressable
            onPress={() => setIsMuted(!isMuted)}
            style={[styles.controlButton, isMuted && styles.controlButtonActive]}
          >
            <MaterialCommunityIcons
              name={isMuted ? "microphone-off" : "microphone"}
              size={28}
              color="white"
            />
          </Pressable>

          {/* Video Toggle */}
          <Pressable
            onPress={() => setIsVideoEnabled(!isVideoEnabled)}
            style={[styles.controlButton, !isVideoEnabled && styles.controlButtonActive]}
          >
            <MaterialCommunityIcons
              name={isVideoEnabled ? "video" : "video-off"}
              size={28}
              color="white"
            />
          </Pressable>

          {/* End Call */}
          <Pressable onPress={endCall} style={styles.endCallButton}>
            <MaterialCommunityIcons name="phone-hangup" size={32} color="white" />
          </Pressable>
        </Surface>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  partnerVideo: {
    flex: 1,
    backgroundColor: '#111',
  },
  selfView: {
    position: 'absolute',
    top: STATUS_BAR_HEIGHT + 60,
    right: 16,
    width: 120,
    height: 160,
    borderRadius: 12,
    overflow: 'hidden',
    borderWidth: 2,
    borderColor: 'white',
  },
  topBar: {
    position: 'absolute',
    top: STATUS_BAR_HEIGHT,
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  callInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.5)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  liveIndicator: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#EF4444',
    marginRight: 8,
  },
  flipButton: {
    backgroundColor: 'rgba(0,0,0,0.5)',
    padding: 12,
    borderRadius: 24,
  },
  nameOverlay: {
    position: 'absolute',
    bottom: 140,
    left: 16,
    backgroundColor: 'rgba(0,0,0,0.5)',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
  },
  controls: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 16,
    paddingBottom: 40,
  },
  controlsInner: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 24,
    backgroundColor: 'rgba(0,0,0,0.8)',
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 40,
  },
  controlButton: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: 'rgba(255,255,255,0.2)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  controlButtonActive: {
    backgroundColor: '#6B7280',
  },
  endCallButton: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#EF4444',
    justifyContent: 'center',
    alignItems: 'center',
  },
});

export default VideoDateCall;
