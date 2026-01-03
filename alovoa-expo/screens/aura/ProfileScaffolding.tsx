import React from "react";
import {
  View,
  useWindowDimensions,
  Pressable,
  ScrollView,
} from "react-native";
import {
  Text,
  Card,
  Button,
  ActivityIndicator,
  useTheme,
  Chip,
  Surface,
  ProgressBar,
  Divider,
} from "react-native-paper";
import Slider from "@react-native-community/slider";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import * as Global from "../../Global";
import * as URL from "../../URL";
import * as I18N from "../../i18n";
import {
  ScaffoldedProfileDto,
  ScoreWithConfidence,
  AttachmentStyle,
} from "../../myTypes";
import { STATUS_BAR_HEIGHT } from "../../assets/styles";

const i18n = I18N.getI18n();

const BIG_FIVE_META: Record<string, { icon: string; color: string; lowLabel: string; highLabel: string }> = {
  openness: {
    icon: "lightbulb-on",
    color: "#8B5CF6",
    lowLabel: "Practical",
    highLabel: "Creative",
  },
  conscientiousness: {
    icon: "clipboard-check",
    color: "#10B981",
    lowLabel: "Flexible",
    highLabel: "Organized",
  },
  extraversion: {
    icon: "account-group",
    color: "#F59E0B",
    lowLabel: "Introverted",
    highLabel: "Extraverted",
  },
  agreeableness: {
    icon: "handshake",
    color: "#EC4899",
    lowLabel: "Analytical",
    highLabel: "Cooperative",
  },
  neuroticism: {
    icon: "heart-pulse",
    color: "#EF4444",
    lowLabel: "Calm",
    highLabel: "Sensitive",
  },
};

const VALUES_META: Record<string, { icon: string; color: string; label: string }> = {
  progressive: { icon: "scale-balance", color: "#EC4899", label: "Progressive" },
  egalitarian: { icon: "equal", color: "#8B5CF6", label: "Egalitarian" },
};

const LIFESTYLE_META: Record<string, { icon: string; color: string; label: string }> = {
  social: { icon: "account-group", color: "#14B8A6", label: "Social" },
  health: { icon: "heart-pulse", color: "#EF4444", label: "Health Focus" },
  workLife: { icon: "briefcase-clock", color: "#3B82F6", label: "Work-Life" },
  finance: { icon: "currency-usd", color: "#22C55E", label: "Financial" },
};

const ATTACHMENT_STYLES: Record<AttachmentStyle, { label: string; description: string; color: string }> = {
  [AttachmentStyle.SECURE]: {
    label: "Secure",
    description: "Comfortable with intimacy and independence",
    color: "#10B981",
  },
  [AttachmentStyle.ANXIOUS]: {
    label: "Anxious",
    description: "Values closeness, sensitive to relationship cues",
    color: "#F59E0B",
  },
  [AttachmentStyle.AVOIDANT]: {
    label: "Avoidant",
    description: "Values independence, prefers emotional distance",
    color: "#6366F1",
  },
  [AttachmentStyle.FEARFUL_AVOIDANT]: {
    label: "Fearful-Avoidant",
    description: "Desires closeness but fears getting hurt",
    color: "#EF4444",
  },
};

const ProfileScaffolding = ({ navigation }: any) => {
  const { colors } = useTheme();
  const { height, width } = useWindowDimensions();

  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [confirming, setConfirming] = React.useState(false);
  const [scaffoldedProfile, setScaffoldedProfileDto] = React.useState<ScaffoldedProfileDto | null>(null);

  // Editable state
  const [bigFive, setBigFive] = React.useState<Record<string, number>>({});
  const [values, setValues] = React.useState<Record<string, number>>({});
  const [lifestyle, setLifestyle] = React.useState<Record<string, number>>({});
  const [attachmentAnxiety, setAttachmentAnxiety] = React.useState(50);
  const [attachmentAvoidance, setAttachmentAvoidance] = React.useState(50);

  React.useEffect(() => {
    load();
  }, []);

  async function load() {
    setLoading(true);
    try {
      const response = await Global.Fetch(URL.API_SCAFFOLDING_PROFILE);
      const profile = response.data as ScaffoldedProfileDto;
      setScaffoldedProfileDto(profile);

      // Initialize editable state from profile
      if (profile.bigFive) {
        const bf: Record<string, number> = {};
        Object.entries(profile.bigFive).forEach(([key, value]) => {
          bf[key] = (value as ScoreWithConfidence).score;
        });
        setBigFive(bf);
      }

      if (profile.values) {
        const v: Record<string, number> = {};
        Object.entries(profile.values).forEach(([key, value]) => {
          v[key] = (value as ScoreWithConfidence).score;
        });
        setValues(v);
      }

      if (profile.lifestyle) {
        const l: Record<string, number> = {};
        Object.entries(profile.lifestyle).forEach(([key, value]) => {
          l[key] = (value as ScoreWithConfidence).score;
        });
        setLifestyle(l);
      }

      if (profile.attachment) {
        setAttachmentAnxiety(profile.attachment.anxiety?.score || 50);
        setAttachmentAvoidance(profile.attachment.avoidance?.score || 50);
      }
    } catch (e) {
      console.error(e);
      Global.ShowToast(i18n.t('error.generic'));
    }
    setLoading(false);
  }

  async function saveAdjustments() {
    setSaving(true);
    try {
      await Global.Fetch(URL.API_SCAFFOLDING_ADJUST, 'post', {
        bigFive,
        values,
        lifestyle,
        attachment: {
          anxiety: attachmentAnxiety,
          avoidance: attachmentAvoidance,
        },
      });
      Global.ShowToast("Adjustments saved!");
    } catch (e) {
      console.error(e);
      Global.ShowToast(i18n.t('error.generic'));
    }
    setSaving(false);
  }

  async function confirmProfile() {
    setConfirming(true);
    try {
      await Global.Fetch(URL.API_SCAFFOLDING_CONFIRM, 'post', {
        bigFive,
        values,
        lifestyle,
        attachment: {
          anxiety: attachmentAnxiety,
          avoidance: attachmentAvoidance,
        },
      });

      Global.ShowToast("Profile confirmed! You're now matchable.");
      navigation.goBack();
    } catch (e) {
      console.error(e);
      Global.ShowToast(i18n.t('error.generic'));
    }
    setConfirming(false);
  }

  async function reRecord() {
    try {
      await Global.Fetch(URL.API_SCAFFOLDING_RERECORD, 'post');
      Global.ShowToast("Video cleared. Record a new intro.");
      Global.navigate("VideoIntro", false, {});
    } catch (e) {
      console.error(e);
      Global.ShowToast(i18n.t('error.generic'));
    }
  }

  function getConfidenceColor(confidence: number): string {
    if (confidence >= 0.7) return "#10B981";
    if (confidence >= 0.5) return "#F59E0B";
    return "#EF4444";
  }

  function getConfidenceLabel(confidence: number): string {
    if (confidence >= 0.7) return "High";
    if (confidence >= 0.5) return "Medium";
    return "Low";
  }

  function getAttachmentStyle(): AttachmentStyle {
    const anxious = attachmentAnxiety > 50;
    const avoidant = attachmentAvoidance > 50;

    if (!anxious && !avoidant) return AttachmentStyle.SECURE;
    if (anxious && !avoidant) return AttachmentStyle.ANXIOUS;
    if (!anxious && avoidant) return AttachmentStyle.AVOIDANT;
    return AttachmentStyle.FEARFUL_AVOIDANT;
  }

  if (loading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: colors.background }}>
        <ActivityIndicator size="large" />
        <Text style={{ marginTop: 16 }}>Loading your profile...</Text>
      </View>
    );
  }

  if (!scaffoldedProfile) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: colors.background, padding: 24 }}>
        <MaterialCommunityIcons name="video-off" size={64} color={colors.onSurfaceVariant} />
        <Text style={{ fontSize: 18, marginTop: 16, textAlign: 'center' }}>
          No Scaffolded Profile Available
        </Text>
        <Text style={{ color: colors.onSurfaceVariant, marginTop: 8, textAlign: 'center' }}>
          Record a video introduction first and our AI will analyze it.
        </Text>
        <Button
          mode="contained"
          onPress={() => Global.navigate("VideoIntro", false, {})}
          style={{ marginTop: 24 }}
          icon="video"
        >
          Record Video Intro
        </Button>
      </View>
    );
  }

  const attachmentStyle = getAttachmentStyle();
  const attachmentInfo = ATTACHMENT_STYLES[attachmentStyle];

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView style={{ flex: 1 }}>
        <View style={{ paddingTop: STATUS_BAR_HEIGHT + 16, paddingHorizontal: 16 }}>
          {/* Header */}
          <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 20 }}>
            <Pressable onPress={() => navigation.goBack()} style={{ marginRight: 12 }}>
              <MaterialCommunityIcons name="arrow-left" size={24} color={colors.onSurface} />
            </Pressable>
            <Text style={{ fontSize: 24, fontWeight: '600' }}>
              Review Your Profile
            </Text>
          </View>

          {/* Intro Card */}
          <Card style={{ marginBottom: 20, backgroundColor: colors.primaryContainer }}>
            <Card.Content>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <MaterialCommunityIcons name="brain" size={32} color={colors.primary} />
                <View style={{ marginLeft: 12, flex: 1 }}>
                  <Text style={{ fontWeight: '600', color: colors.onPrimaryContainer }}>
                    AI-Generated Profile
                  </Text>
                  <Text style={{ color: colors.onPrimaryContainer, fontSize: 12 }}>
                    Based on your video intro. Adjust anything that doesn't feel right!
                  </Text>
                </View>
              </View>
            </Card.Content>
          </Card>

          {/* Low Confidence Areas */}
          {scaffoldedProfile.lowConfidenceAreas && scaffoldedProfile.lowConfidenceAreas.length > 0 && (
            <Card style={{ marginBottom: 20, borderLeftWidth: 4, borderLeftColor: "#F59E0B" }}>
              <Card.Content>
                <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 8 }}>
                  <MaterialCommunityIcons name="alert" size={20} color="#F59E0B" />
                  <Text style={{ marginLeft: 8, fontWeight: '600', color: "#F59E0B" }}>
                    Low Confidence Areas
                  </Text>
                </View>
                <Text style={{ color: colors.onSurfaceVariant, fontSize: 13 }}>
                  These areas need your input: {scaffoldedProfile.lowConfidenceAreas.join(', ')}
                </Text>
              </Card.Content>
            </Card>
          )}

          {/* Big Five Personality */}
          <Text style={{ fontSize: 18, fontWeight: '600', marginBottom: 16 }}>
            Personality (Big Five)
          </Text>

          {Object.entries(BIG_FIVE_META).map(([trait, meta]) => {
            const value = bigFive[trait] || 50;
            const confidence = scaffoldedProfile.bigFive?.[trait]?.confidence || 0.5;

            return (
              <Card key={trait} style={{ marginBottom: 12 }}>
                <Card.Content>
                  <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 8 }}>
                    <MaterialCommunityIcons name={meta.icon as any} size={24} color={meta.color} />
                    <Text style={{ marginLeft: 8, fontWeight: '600', textTransform: 'capitalize', flex: 1 }}>
                      {trait}
                    </Text>
                    <Chip
                      style={{ backgroundColor: getConfidenceColor(confidence) + '20' }}
                      textStyle={{ fontSize: 10 }}
                    >
                      {getConfidenceLabel(confidence)} confidence
                    </Chip>
                  </View>

                  <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 4 }}>
                    <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, flex: 1 }}>
                      {meta.lowLabel}
                    </Text>
                    <Text style={{ fontSize: 18, fontWeight: '700', color: meta.color }}>
                      {Math.round(value)}%
                    </Text>
                    <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, flex: 1, textAlign: 'right' }}>
                      {meta.highLabel}
                    </Text>
                  </View>

                  <Slider
                    value={value}
                    onValueChange={(v) => setBigFive({ ...bigFive, [trait]: v })}
                    minimumValue={0}
                    maximumValue={100}
                    step={1}
                    minimumTrackTintColor={meta.color}
                    maximumTrackTintColor={colors.surfaceVariant}
                    thumbTintColor={meta.color}
                  />
                </Card.Content>
              </Card>
            );
          })}

          {/* Values */}
          <Text style={{ fontSize: 18, fontWeight: '600', marginTop: 12, marginBottom: 16 }}>
            Values
          </Text>

          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginBottom: 20 }}>
            {Object.entries(VALUES_META).map(([key, meta]) => {
              const value = values[key] || 50;

              return (
                <Card key={key} style={{ flex: 1, minWidth: 150 }}>
                  <Card.Content>
                    <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 8 }}>
                      <MaterialCommunityIcons name={meta.icon as any} size={20} color={meta.color} />
                      <Text style={{ marginLeft: 8, fontWeight: '500' }}>{meta.label}</Text>
                    </View>
                    <Text style={{ fontSize: 24, fontWeight: '700', color: meta.color }}>
                      {Math.round(value)}%
                    </Text>
                    <Slider
                      value={value}
                      onValueChange={(v) => setValues({ ...values, [key]: v })}
                      minimumValue={0}
                      maximumValue={100}
                      step={1}
                      minimumTrackTintColor={meta.color}
                      style={{ marginTop: 8 }}
                    />
                  </Card.Content>
                </Card>
              );
            })}
          </View>

          {/* Lifestyle */}
          <Text style={{ fontSize: 18, fontWeight: '600', marginBottom: 16 }}>
            Lifestyle
          </Text>

          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginBottom: 20 }}>
            {Object.entries(LIFESTYLE_META).map(([key, meta]) => {
              const value = lifestyle[key] || 50;

              return (
                <Card key={key} style={{ flex: 1, minWidth: 150 }}>
                  <Card.Content>
                    <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 8 }}>
                      <MaterialCommunityIcons name={meta.icon as any} size={20} color={meta.color} />
                      <Text style={{ marginLeft: 8, fontWeight: '500', fontSize: 13 }}>{meta.label}</Text>
                    </View>
                    <Text style={{ fontSize: 24, fontWeight: '700', color: meta.color }}>
                      {Math.round(value)}%
                    </Text>
                    <Slider
                      value={value}
                      onValueChange={(v) => setLifestyle({ ...lifestyle, [key]: v })}
                      minimumValue={0}
                      maximumValue={100}
                      step={1}
                      minimumTrackTintColor={meta.color}
                      style={{ marginTop: 8 }}
                    />
                  </Card.Content>
                </Card>
              );
            })}
          </View>

          {/* Attachment Style */}
          <Text style={{ fontSize: 18, fontWeight: '600', marginBottom: 16 }}>
            Attachment Style
          </Text>

          <Card style={{ marginBottom: 20, borderLeftWidth: 4, borderLeftColor: attachmentInfo.color }}>
            <Card.Content>
              <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 12 }}>
                <MaterialCommunityIcons name="link-variant" size={28} color={attachmentInfo.color} />
                <View style={{ marginLeft: 12 }}>
                  <Text style={{ fontSize: 20, fontWeight: '600' }}>{attachmentInfo.label}</Text>
                  <Text style={{ color: colors.onSurfaceVariant }}>{attachmentInfo.description}</Text>
                </View>
              </View>

              <Divider style={{ marginVertical: 12 }} />

              <View style={{ marginBottom: 16 }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 }}>
                  <Text style={{ color: colors.onSurfaceVariant }}>Attachment Anxiety</Text>
                  <Text style={{ fontWeight: '600' }}>{Math.round(attachmentAnxiety)}%</Text>
                </View>
                <Slider
                  value={attachmentAnxiety}
                  onValueChange={setAttachmentAnxiety}
                  minimumValue={0}
                  maximumValue={100}
                  step={1}
                  minimumTrackTintColor="#F59E0B"
                />
              </View>

              <View>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 }}>
                  <Text style={{ color: colors.onSurfaceVariant }}>Attachment Avoidance</Text>
                  <Text style={{ fontWeight: '600' }}>{Math.round(attachmentAvoidance)}%</Text>
                </View>
                <Slider
                  value={attachmentAvoidance}
                  onValueChange={setAttachmentAvoidance}
                  minimumValue={0}
                  maximumValue={100}
                  step={1}
                  minimumTrackTintColor="#6366F1"
                />
              </View>
            </Card.Content>
          </Card>

          {/* Actions */}
          <Button
            mode="contained"
            onPress={confirmProfile}
            loading={confirming}
            disabled={confirming}
            style={{ marginBottom: 12 }}
            icon="check"
          >
            Confirm Profile
          </Button>

          <Button
            mode="outlined"
            onPress={saveAdjustments}
            loading={saving}
            disabled={saving}
            style={{ marginBottom: 12 }}
          >
            Save Adjustments
          </Button>

          <Button
            mode="text"
            onPress={reRecord}
            textColor={colors.error}
            style={{ marginBottom: 32 }}
            icon="video-plus"
          >
            Re-record Video
          </Button>
        </View>
      </ScrollView>
    </View>
  );
};

export default ProfileScaffolding;
