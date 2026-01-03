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
  Chip,
  Button,
  ActivityIndicator,
  useTheme,
  Divider,
  Surface,
} from "react-native-paper";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import * as Global from "../../Global";
import * as URL from "../../URL";
import * as I18N from "../../i18n";
import {
  UserAssessmentProfile,
  AttachmentStyle,
} from "../../myTypes";
import { STATUS_BAR_HEIGHT } from "../../assets/styles";
import VerticalView from "../../components/VerticalView";

const i18n = I18N.getI18n();

// Big Five trait descriptions
const BIG_FIVE_INFO: Record<string, { low: string; high: string; icon: string; color: string }> = {
  openness: {
    low: "Practical, conventional, prefers routine",
    high: "Curious, creative, open to new experiences",
    icon: "lightbulb-on",
    color: "#8B5CF6",
  },
  conscientiousness: {
    low: "Flexible, spontaneous, carefree",
    high: "Organized, disciplined, goal-oriented",
    icon: "clipboard-check",
    color: "#10B981",
  },
  extraversion: {
    low: "Reserved, reflective, enjoys solitude",
    high: "Outgoing, energetic, seeks social interaction",
    icon: "account-group",
    color: "#F59E0B",
  },
  agreeableness: {
    low: "Analytical, competitive, skeptical",
    high: "Cooperative, trusting, empathetic",
    icon: "handshake",
    color: "#EC4899",
  },
  neuroticism: {
    low: "Calm, emotionally stable, resilient",
    high: "Sensitive, prone to stress, emotionally reactive",
    icon: "heart-pulse",
    color: "#EF4444",
  },
};

// Attachment style descriptions
const ATTACHMENT_INFO: Record<AttachmentStyle, { title: string; description: string; color: string }> = {
  [AttachmentStyle.SECURE]: {
    title: "Secure",
    description: "Comfortable with intimacy and independence. You easily trust others and feel worthy of love.",
    color: "#10B981",
  },
  [AttachmentStyle.ANXIOUS]: {
    title: "Anxious",
    description: "You crave closeness and may worry about your partner's feelings. You're sensitive to relationship cues.",
    color: "#F59E0B",
  },
  [AttachmentStyle.AVOIDANT]: {
    title: "Avoidant",
    description: "You value independence highly and may feel uncomfortable with too much closeness.",
    color: "#6366F1",
  },
  [AttachmentStyle.FEARFUL_AVOIDANT]: {
    title: "Fearful-Avoidant",
    description: "You desire closeness but fear getting hurt. Relationships can feel complicated.",
    color: "#EF4444",
  },
};

const AssessmentResults = ({ navigation }: any) => {
  const { colors } = useTheme();
  const { height, width } = useWindowDimensions();

  const [loading, setLoading] = React.useState(true);
  const [profile, setProfile] = React.useState<UserAssessmentProfile | null>(null);

  React.useEffect(() => {
    load();
  }, []);

  async function load() {
    setLoading(true);
    try {
      const response = await Global.Fetch(URL.API_ASSESSMENT_PROFILE);
      setProfile(response.data);
    } catch (e) {
      console.error(e);
      Global.ShowToast(i18n.t('error.generic'));
    }
    setLoading(false);
  }

  async function recalculate() {
    setLoading(true);
    try {
      await Global.Fetch(URL.API_ASSESSMENT_RECALCULATE, 'post');
      await load();
      Global.ShowToast("Profile recalculated!");
    } catch (e) {
      console.error(e);
      Global.ShowToast(i18n.t('error.generic'));
    }
  }

  function getTraitDescription(trait: string, value: number): string {
    const info = BIG_FIVE_INFO[trait];
    if (!info) return "";
    return value >= 50 ? info.high : info.low;
  }

  if (loading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: colors.background }}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  if (!profile) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: colors.background, padding: 24 }}>
        <MaterialCommunityIcons name="clipboard-text-outline" size={64} color={colors.onSurfaceVariant} />
        <Text style={{ fontSize: 20, marginTop: 16, textAlign: 'center' }}>
          Answer more questions to see your results
        </Text>
        <Text style={{ color: colors.onSurfaceVariant, marginTop: 8, textAlign: 'center' }}>
          You need at least 20 answers to generate your personality profile.
        </Text>
        <Button mode="contained" onPress={() => navigation.goBack()} style={{ marginTop: 24 }}>
          Answer Questions
        </Button>
      </View>
    );
  }

  const attachmentInfo = ATTACHMENT_INFO[profile.attachmentStyle] || ATTACHMENT_INFO[AttachmentStyle.SECURE];

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <VerticalView onRefresh={load} style={{ padding: 0 }}>
        <View style={{ paddingTop: STATUS_BAR_HEIGHT + 16, paddingHorizontal: 16 }}>
          {/* Header */}
          <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 24 }}>
            <Pressable onPress={() => navigation.goBack()} style={{ marginRight: 12 }}>
              <MaterialCommunityIcons name="arrow-left" size={24} color={colors.onSurface} />
            </Pressable>
            <Text style={{ fontSize: 24, fontWeight: '600', flex: 1 }}>
              Your Results
            </Text>
            <Button mode="text" onPress={recalculate}>
              Refresh
            </Button>
          </View>

          {/* Stats Overview */}
          <Card style={{ marginBottom: 20, backgroundColor: colors.primaryContainer }}>
            <Card.Content>
              <View style={{ flexDirection: 'row', justifyContent: 'space-around' }}>
                <View style={{ alignItems: 'center' }}>
                  <Text style={{ fontSize: 32, fontWeight: '700', color: colors.primary }}>
                    {profile.questionsAnswered}
                  </Text>
                  <Text style={{ color: colors.onPrimaryContainer }}>Questions Answered</Text>
                </View>
                <View style={{ alignItems: 'center' }}>
                  <MaterialCommunityIcons
                    name={profile.profileComplete ? "check-decagram" : "clock-outline"}
                    size={32}
                    color={profile.profileComplete ? "#10B981" : colors.onPrimaryContainer}
                  />
                  <Text style={{ color: colors.onPrimaryContainer }}>
                    {profile.profileComplete ? "Complete" : "In Progress"}
                  </Text>
                </View>
              </View>
            </Card.Content>
          </Card>

          {/* Big Five Personality */}
          <Text style={{ fontSize: 20, fontWeight: '600', marginBottom: 16 }}>
            Big Five Personality
          </Text>

          <Card style={{ marginBottom: 24 }}>
            <Card.Content>
              {Object.entries(BIG_FIVE_INFO).map(([trait, info]) => {
                const value = (profile as any)[trait] || 50;
                return (
                  <View key={trait} style={{ marginBottom: 20 }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 8 }}>
                      <MaterialCommunityIcons
                        name={info.icon as any}
                        size={24}
                        color={info.color}
                      />
                      <Text style={{ marginLeft: 8, fontWeight: '600', textTransform: 'capitalize', flex: 1 }}>
                        {trait}
                      </Text>
                      <Text style={{ fontWeight: '600', color: info.color }}>
                        {value}%
                      </Text>
                    </View>

                    {/* Progress Bar */}
                    <View style={{ height: 12, backgroundColor: colors.surfaceVariant, borderRadius: 6, overflow: 'hidden' }}>
                      <View
                        style={{
                          height: '100%',
                          width: `${value}%`,
                          backgroundColor: info.color,
                          borderRadius: 6,
                        }}
                      />
                    </View>

                    <Text style={{ fontSize: 13, color: colors.onSurfaceVariant, marginTop: 6 }}>
                      {getTraitDescription(trait, value)}
                    </Text>
                  </View>
                );
              })}
            </Card.Content>
          </Card>

          {/* Attachment Style */}
          <Text style={{ fontSize: 20, fontWeight: '600', marginBottom: 16 }}>
            Attachment Style
          </Text>

          <Card style={{ marginBottom: 24, borderLeftWidth: 4, borderLeftColor: attachmentInfo.color }}>
            <Card.Content>
              <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 12 }}>
                <MaterialCommunityIcons
                  name="link-variant"
                  size={28}
                  color={attachmentInfo.color}
                />
                <Text style={{ marginLeft: 12, fontSize: 20, fontWeight: '600' }}>
                  {attachmentInfo.title}
                </Text>
              </View>

              <Text style={{ color: colors.onSurfaceVariant, lineHeight: 22 }}>
                {attachmentInfo.description}
              </Text>

              <Divider style={{ marginVertical: 16 }} />

              {/* Attachment Dimensions */}
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <View style={{ flex: 1, marginRight: 8 }}>
                  <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 4 }}>
                    ANXIETY
                  </Text>
                  <View style={{ height: 8, backgroundColor: colors.surfaceVariant, borderRadius: 4 }}>
                    <View
                      style={{
                        height: '100%',
                        width: `${profile.attachmentAnxiety}%`,
                        backgroundColor: '#F59E0B',
                        borderRadius: 4,
                      }}
                    />
                  </View>
                  <Text style={{ fontSize: 12, marginTop: 4 }}>{profile.attachmentAnxiety}%</Text>
                </View>

                <View style={{ flex: 1, marginLeft: 8 }}>
                  <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 4 }}>
                    AVOIDANCE
                  </Text>
                  <View style={{ height: 8, backgroundColor: colors.surfaceVariant, borderRadius: 4 }}>
                    <View
                      style={{
                        height: '100%',
                        width: `${profile.attachmentAvoidance}%`,
                        backgroundColor: '#6366F1',
                        borderRadius: 4,
                      }}
                    />
                  </View>
                  <Text style={{ fontSize: 12, marginTop: 4 }}>{profile.attachmentAvoidance}%</Text>
                </View>
              </View>
            </Card.Content>
          </Card>

          {/* Values & Lifestyle */}
          <Text style={{ fontSize: 20, fontWeight: '600', marginBottom: 16 }}>
            Values & Lifestyle
          </Text>

          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginBottom: 24 }}>
            <Surface style={{ flex: 1, minWidth: 150, padding: 16, borderRadius: 12 }} elevation={1}>
              <MaterialCommunityIcons name="scale-balance" size={24} color="#EC4899" />
              <Text style={{ marginTop: 8, fontWeight: '500' }}>Progressive</Text>
              <Text style={{ fontSize: 24, fontWeight: '700', color: "#EC4899" }}>
                {profile.progressive}%
              </Text>
            </Surface>

            <Surface style={{ flex: 1, minWidth: 150, padding: 16, borderRadius: 12 }} elevation={1}>
              <MaterialCommunityIcons name="equal" size={24} color="#8B5CF6" />
              <Text style={{ marginTop: 8, fontWeight: '500' }}>Egalitarian</Text>
              <Text style={{ fontSize: 24, fontWeight: '700', color: "#8B5CF6" }}>
                {profile.egalitarian}%
              </Text>
            </Surface>

            <Surface style={{ flex: 1, minWidth: 150, padding: 16, borderRadius: 12 }} elevation={1}>
              <MaterialCommunityIcons name="account-group" size={24} color="#14B8A6" />
              <Text style={{ marginTop: 8, fontWeight: '500' }}>Social</Text>
              <Text style={{ fontSize: 24, fontWeight: '700', color: "#14B8A6" }}>
                {profile.socialOrientation}%
              </Text>
            </Surface>

            <Surface style={{ flex: 1, minWidth: 150, padding: 16, borderRadius: 12 }} elevation={1}>
              <MaterialCommunityIcons name="heart-pulse" size={24} color="#EF4444" />
              <Text style={{ marginTop: 8, fontWeight: '500' }}>Health Focus</Text>
              <Text style={{ fontSize: 24, fontWeight: '700', color: "#EF4444" }}>
                {profile.healthFocus}%
              </Text>
            </Surface>

            <Surface style={{ flex: 1, minWidth: 150, padding: 16, borderRadius: 12 }} elevation={1}>
              <MaterialCommunityIcons name="briefcase-clock" size={24} color="#3B82F6" />
              <Text style={{ marginTop: 8, fontWeight: '500' }}>Work-Life Balance</Text>
              <Text style={{ fontSize: 24, fontWeight: '700', color: "#3B82F6" }}>
                {profile.workLifeBalance}%
              </Text>
            </Surface>

            <Surface style={{ flex: 1, minWidth: 150, padding: 16, borderRadius: 12 }} elevation={1}>
              <MaterialCommunityIcons name="currency-usd" size={24} color="#22C55E" />
              <Text style={{ marginTop: 8, fontWeight: '500' }}>Financial Ambition</Text>
              <Text style={{ fontSize: 24, fontWeight: '700', color: "#22C55E" }}>
                {profile.financialAmbition}%
              </Text>
            </Surface>
          </View>

          {/* Continue Button */}
          <Button
            mode="contained"
            onPress={() => Global.navigate("Assessment.Home", false, {})}
            style={{ marginBottom: 24 }}
            icon="arrow-right"
            contentStyle={{ flexDirection: 'row-reverse' }}
          >
            Answer More Questions
          </Button>

          <View style={{ height: 50 }} />
        </View>
      </VerticalView>
    </View>
  );
};

export default AssessmentResults;
