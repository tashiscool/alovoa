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
  TextInput,
  SegmentedButtons,
} from "react-native-paper";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import * as Global from "../../Global";
import * as URL from "../../URL";
import * as I18N from "../../i18n";
import { VideoDateFeedback as FeedbackType } from "../../myTypes";
import { STATUS_BAR_HEIGHT } from "../../assets/styles";

const i18n = I18N.getI18n();

const RATING_LABELS: Record<number, string> = {
  1: "Poor",
  2: "Fair",
  3: "Good",
  4: "Great",
  5: "Amazing",
};

const CONNECTION_OPTIONS = [
  { value: 'no_spark', label: 'No Spark' },
  { value: 'friendly', label: 'Friendly' },
  { value: 'interested', label: 'Interested' },
  { value: 'strong_connection', label: 'Strong Connection' },
];

const VideoDateFeedback = ({ route, navigation }: any) => {
  const { colors } = useTheme();
  const { height, width } = useWindowDimensions();

  const { videoDateId } = route.params || {};

  const [submitting, setSubmitting] = React.useState(false);
  const [overallRating, setOverallRating] = React.useState(0);
  const [connectionFelt, setConnectionFelt] = React.useState('');
  const [wouldMeetAgain, setWouldMeetAgain] = React.useState<boolean | null>(null);
  const [notes, setNotes] = React.useState('');
  const [reportIssue, setReportIssue] = React.useState(false);
  const [issueType, setIssueType] = React.useState('');

  const ISSUE_TYPES = [
    { value: 'no_show', label: 'No Show', icon: 'account-off' },
    { value: 'inappropriate', label: 'Inappropriate Behavior', icon: 'alert' },
    { value: 'fake_profile', label: 'Fake Profile', icon: 'account-alert' },
    { value: 'harassment', label: 'Harassment', icon: 'shield-alert' },
    { value: 'other', label: 'Other Issue', icon: 'help-circle' },
  ];

  async function submitFeedback() {
    if (overallRating === 0) {
      Global.ShowToast("Please rate your experience");
      return;
    }

    setSubmitting(true);
    try {
      const feedback: Partial<FeedbackType> = {
        overallRating,
        connectionFelt,
        wouldMeetAgain: wouldMeetAgain || false,
        privateNotes: notes,
        hasIssue: reportIssue,
        issueType: reportIssue ? issueType : undefined,
      };

      await Global.Fetch(Global.format(URL.API_VIDEO_DATE_FEEDBACK, videoDateId), 'post', feedback);

      Global.ShowToast("Thank you for your feedback!");
      navigation.goBack();
    } catch (e) {
      console.error(e);
      Global.ShowToast(i18n.t('error.generic'));
    }
    setSubmitting(false);
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      {/* Header */}
      <View style={{ paddingTop: STATUS_BAR_HEIGHT + 8, paddingHorizontal: 16, paddingBottom: 8 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <Pressable onPress={() => navigation.goBack()} style={{ marginRight: 12 }}>
            <MaterialCommunityIcons name="close" size={24} color={colors.onSurface} />
          </Pressable>
          <Text style={{ fontSize: 20, fontWeight: '600' }}>
            How Was Your Date?
          </Text>
        </View>
      </View>

      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 16 }}>
        {/* Overall Rating */}
        <Card style={{ marginBottom: 20 }}>
          <Card.Content>
            <Text style={{ fontSize: 16, fontWeight: '600', marginBottom: 16 }}>
              Overall Experience
            </Text>

            <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 8, marginBottom: 8 }}>
              {[1, 2, 3, 4, 5].map((rating) => (
                <Pressable
                  key={rating}
                  onPress={() => setOverallRating(rating)}
                >
                  <MaterialCommunityIcons
                    name={rating <= overallRating ? "star" : "star-outline"}
                    size={40}
                    color={rating <= overallRating ? "#F59E0B" : colors.onSurfaceVariant}
                  />
                </Pressable>
              ))}
            </View>

            {overallRating > 0 && (
              <Text style={{ textAlign: 'center', color: "#F59E0B", fontWeight: '500' }}>
                {RATING_LABELS[overallRating]}
              </Text>
            )}
          </Card.Content>
        </Card>

        {/* Connection Level */}
        <Card style={{ marginBottom: 20 }}>
          <Card.Content>
            <Text style={{ fontSize: 16, fontWeight: '600', marginBottom: 16 }}>
              How did you feel?
            </Text>

            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {CONNECTION_OPTIONS.map((option) => (
                <Chip
                  key={option.value}
                  selected={connectionFelt === option.value}
                  onPress={() => setConnectionFelt(option.value)}
                  style={{
                    backgroundColor: connectionFelt === option.value
                      ? colors.primaryContainer
                      : colors.surfaceVariant,
                  }}
                >
                  {option.label}
                </Chip>
              ))}
            </View>
          </Card.Content>
        </Card>

        {/* Would Meet Again */}
        <Card style={{ marginBottom: 20 }}>
          <Card.Content>
            <Text style={{ fontSize: 16, fontWeight: '600', marginBottom: 16 }}>
              Would you want to meet again?
            </Text>

            <View style={{ flexDirection: 'row', gap: 12 }}>
              <Pressable
                onPress={() => setWouldMeetAgain(true)}
                style={{
                  flex: 1,
                  padding: 16,
                  borderRadius: 12,
                  alignItems: 'center',
                  backgroundColor: wouldMeetAgain === true ? '#D1FAE5' : colors.surfaceVariant,
                }}
              >
                <MaterialCommunityIcons
                  name="thumb-up"
                  size={32}
                  color={wouldMeetAgain === true ? '#10B981' : colors.onSurfaceVariant}
                />
                <Text style={{
                  marginTop: 8,
                  fontWeight: '500',
                  color: wouldMeetAgain === true ? '#065F46' : colors.onSurfaceVariant,
                }}>
                  Yes!
                </Text>
              </Pressable>

              <Pressable
                onPress={() => setWouldMeetAgain(false)}
                style={{
                  flex: 1,
                  padding: 16,
                  borderRadius: 12,
                  alignItems: 'center',
                  backgroundColor: wouldMeetAgain === false ? '#FEE2E2' : colors.surfaceVariant,
                }}
              >
                <MaterialCommunityIcons
                  name="thumb-down"
                  size={32}
                  color={wouldMeetAgain === false ? '#EF4444' : colors.onSurfaceVariant}
                />
                <Text style={{
                  marginTop: 8,
                  fontWeight: '500',
                  color: wouldMeetAgain === false ? '#991B1B' : colors.onSurfaceVariant,
                }}>
                  No
                </Text>
              </Pressable>
            </View>
          </Card.Content>
        </Card>

        {/* Private Notes */}
        <Card style={{ marginBottom: 20 }}>
          <Card.Content>
            <Text style={{ fontSize: 16, fontWeight: '600', marginBottom: 8 }}>
              Private Notes (Optional)
            </Text>
            <Text style={{ color: colors.onSurfaceVariant, fontSize: 12, marginBottom: 12 }}>
              Only you can see these notes. Helps you remember the conversation.
            </Text>

            <TextInput
              mode="outlined"
              placeholder="What did you talk about? Any memorable moments?"
              value={notes}
              onChangeText={setNotes}
              multiline
              numberOfLines={3}
            />
          </Card.Content>
        </Card>

        {/* Report Issue */}
        <Card style={{ marginBottom: 20 }}>
          <Card.Content>
            <Pressable
              onPress={() => setReportIssue(!reportIssue)}
              style={{ flexDirection: 'row', alignItems: 'center' }}
            >
              <MaterialCommunityIcons
                name={reportIssue ? "checkbox-marked" : "checkbox-blank-outline"}
                size={24}
                color={reportIssue ? colors.error : colors.onSurfaceVariant}
              />
              <Text style={{ marginLeft: 12, flex: 1 }}>
                Report an Issue
              </Text>
            </Pressable>

            {reportIssue && (
              <View style={{ marginTop: 16 }}>
                <Text style={{ color: colors.onSurfaceVariant, marginBottom: 12 }}>
                  What happened?
                </Text>

                {ISSUE_TYPES.map((issue) => (
                  <Pressable
                    key={issue.value}
                    onPress={() => setIssueType(issue.value)}
                    style={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      padding: 12,
                      marginBottom: 8,
                      borderRadius: 8,
                      backgroundColor: issueType === issue.value
                        ? '#FEE2E2'
                        : colors.surfaceVariant,
                    }}
                  >
                    <MaterialCommunityIcons
                      name={issue.icon as any}
                      size={24}
                      color={issueType === issue.value ? '#EF4444' : colors.onSurfaceVariant}
                    />
                    <Text style={{
                      marginLeft: 12,
                      color: issueType === issue.value ? '#991B1B' : colors.onSurface,
                    }}>
                      {issue.label}
                    </Text>
                  </Pressable>
                ))}
              </View>
            )}
          </Card.Content>
        </Card>

        <View style={{ height: 100 }} />
      </ScrollView>

      {/* Submit Button */}
      <View style={{ padding: 16, paddingBottom: 32, backgroundColor: colors.background, borderTopWidth: 1, borderTopColor: colors.surfaceVariant }}>
        <Button
          mode="contained"
          onPress={submitFeedback}
          disabled={submitting || overallRating === 0}
          loading={submitting}
        >
          Submit Feedback
        </Button>
      </View>
    </View>
  );
};

export default VideoDateFeedback;
