import React from "react";
import {
  View,
  useWindowDimensions,
  Pressable,
  ScrollView,
  Image,
  Platform,
} from "react-native";
import {
  Text,
  Card,
  Button,
  ActivityIndicator,
  useTheme,
  Chip,
  TextInput,
  RadioButton,
  Checkbox,
  Surface,
} from "react-native-paper";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import * as ImagePicker from "expo-image-picker";
import * as Global from "../../Global";
import * as URL from "../../URL";
import * as I18N from "../../i18n";
import { ReportType, ReportSeverity } from "../../myTypes";
import { STATUS_BAR_HEIGHT } from "../../assets/styles";
import { uploadFileMultipart } from "../../utils/webUpload";

// Native imports (conditional)
let FileSystemLegacy: any;
let FileSystemUploadType: any;

if (Platform.OS !== "web") {
  FileSystemLegacy = require("expo-file-system/legacy");
  FileSystemUploadType = require("expo-file-system/legacy").FileSystemUploadType;
}

const i18n = I18N.getI18n();

const REPORT_TYPES = [
  { value: ReportType.HARASSMENT, label: "Harassment", icon: "account-alert", color: "#EF4444" },
  { value: ReportType.FAKE_PROFILE, label: "Fake/Catfish Profile", icon: "account-question", color: "#F59E0B" },
  { value: ReportType.INAPPROPRIATE_CONTENT, label: "Inappropriate Content", icon: "image-off", color: "#EC4899" },
  { value: ReportType.SCAM, label: "Scam/Fraud", icon: "cash-remove", color: "#DC2626" },
  { value: ReportType.UNDERAGE, label: "Underage User", icon: "account-child", color: "#7C3AED" },
  { value: ReportType.THREATS, label: "Threats/Violence", icon: "shield-alert", color: "#991B1B" },
  { value: ReportType.SPAM, label: "Spam", icon: "email-alert", color: "#6B7280" },
  { value: ReportType.OTHER, label: "Other", icon: "help-circle", color: "#3B82F6" },
];

const SEVERITY_LEVELS = [
  { value: ReportSeverity.LOW, label: "Low", description: "Annoying but not harmful", color: "#F59E0B" },
  { value: ReportSeverity.MEDIUM, label: "Medium", description: "Concerning behavior", color: "#F97316" },
  { value: ReportSeverity.HIGH, label: "High", description: "Serious violation", color: "#EF4444" },
  { value: ReportSeverity.CRITICAL, label: "Critical", description: "Immediate danger/illegal", color: "#991B1B" },
];

const ReportUser = ({ route, navigation }: any) => {
  const { colors } = useTheme();
  const { height, width } = useWindowDimensions();

  const { userId, userName } = route.params || {};

  const [step, setStep] = React.useState(1);
  const [submitting, setSubmitting] = React.useState(false);

  // Form state
  const [reportType, setReportType] = React.useState<ReportType | null>(null);
  const [severity, setSeverity] = React.useState<ReportSeverity>(ReportSeverity.MEDIUM);
  const [description, setDescription] = React.useState("");
  const [evidence, setEvidence] = React.useState<string[]>([]);
  const [blockUser, setBlockUser] = React.useState(true);
  const [makePublic, setMakePublic] = React.useState(false);

  async function pickImage() {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsMultipleSelection: true,
      quality: 0.8,
    });

    if (!result.canceled) {
      setEvidence([...evidence, ...result.assets.map(a => a.uri)]);
    }
  }

  function removeEvidence(index: number) {
    const newEvidence = [...evidence];
    newEvidence.splice(index, 1);
    setEvidence(newEvidence);
  }

  async function submitReport() {
    if (!reportType) {
      Global.ShowToast("Please select a report type");
      return;
    }

    if (description.length < 20) {
      Global.ShowToast("Please provide more details (at least 20 characters)");
      return;
    }

    setSubmitting(true);
    try {
      // Create the report
      const response = await Global.Fetch(URL.API_REPORT_CREATE, 'post', {
        reportedUserId: userId,
        type: reportType,
        severity,
        description,
        blockUser,
        makePublic,
      });

      const reportId = response.data.id;

      // Upload evidence if any
      if (evidence.length > 0) {
        for (let i = 0; i < evidence.length; i++) {
          const uri = evidence[i];
          const uploadUrl = Global.format(URL.API_REPORT_EVIDENCE, reportId);

          if (Platform.OS === "web") {
            // Use web upload utility
            await uploadFileMultipart(
              uploadUrl,
              uri,
              'file',
              `evidence_${i}.jpg`
            );
          } else {
            // Use native expo-file-system
            const uploadTask = FileSystemLegacy.createUploadTask(
              uploadUrl,
              uri,
              {
                httpMethod: 'POST',
                uploadType: FileSystemUploadType.MULTIPART,
                fieldName: 'file',
              }
            );
            await uploadTask.uploadAsync();
          }
        }
      }

      Global.ShowToast("Report submitted. Thank you for helping keep our community safe.");
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
            Report User
          </Text>
        </View>
      </View>

      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 16 }}>
        {/* Who are you reporting */}
        <Card style={{ marginBottom: 20, backgroundColor: colors.errorContainer }}>
          <Card.Content>
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              <MaterialCommunityIcons name="shield-alert" size={24} color={colors.error} />
              <View style={{ marginLeft: 12 }}>
                <Text style={{ color: colors.onErrorContainer, fontWeight: '600' }}>
                  Reporting: {userName || 'User'}
                </Text>
                <Text style={{ color: colors.onErrorContainer, fontSize: 12 }}>
                  All reports are reviewed by our safety team
                </Text>
              </View>
            </View>
          </Card.Content>
        </Card>

        {/* Step 1: Report Type */}
        {step >= 1 && (
          <>
            <Text style={{ fontSize: 16, fontWeight: '600', marginBottom: 12 }}>
              What happened?
            </Text>

            <View style={{ gap: 8, marginBottom: 24 }}>
              {REPORT_TYPES.map((type) => (
                <Pressable
                  key={type.value}
                  onPress={() => {
                    setReportType(type.value);
                    if (step === 1) setStep(2);
                  }}
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    padding: 16,
                    borderRadius: 12,
                    backgroundColor: reportType === type.value
                      ? type.color + '20'
                      : colors.surfaceVariant,
                    borderWidth: reportType === type.value ? 2 : 0,
                    borderColor: type.color,
                  }}
                >
                  <MaterialCommunityIcons
                    name={type.icon as any}
                    size={24}
                    color={reportType === type.value ? type.color : colors.onSurfaceVariant}
                  />
                  <Text style={{
                    marginLeft: 12,
                    fontWeight: reportType === type.value ? '600' : '400',
                    color: reportType === type.value ? type.color : colors.onSurface,
                  }}>
                    {type.label}
                  </Text>
                </Pressable>
              ))}
            </View>
          </>
        )}

        {/* Step 2: Severity & Description */}
        {step >= 2 && (
          <>
            <Text style={{ fontSize: 16, fontWeight: '600', marginBottom: 12 }}>
              How serious is this?
            </Text>

            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 24 }}>
              {SEVERITY_LEVELS.map((level) => (
                <Pressable
                  key={level.value}
                  onPress={() => setSeverity(level.value)}
                  style={{
                    flex: 1,
                    minWidth: 140,
                    padding: 12,
                    borderRadius: 8,
                    backgroundColor: severity === level.value
                      ? level.color + '20'
                      : colors.surfaceVariant,
                    borderWidth: severity === level.value ? 2 : 0,
                    borderColor: level.color,
                  }}
                >
                  <Text style={{
                    fontWeight: '600',
                    color: severity === level.value ? level.color : colors.onSurface,
                  }}>
                    {level.label}
                  </Text>
                  <Text style={{ fontSize: 11, color: colors.onSurfaceVariant }}>
                    {level.description}
                  </Text>
                </Pressable>
              ))}
            </View>

            <Text style={{ fontSize: 16, fontWeight: '600', marginBottom: 12 }}>
              Tell us more
            </Text>

            <TextInput
              mode="outlined"
              placeholder="Describe what happened in detail. Include dates, times, and any other relevant information."
              value={description}
              onChangeText={(text) => {
                setDescription(text);
                if (step === 2 && text.length > 20) setStep(3);
              }}
              multiline
              numberOfLines={5}
              style={{ marginBottom: 24 }}
            />
          </>
        )}

        {/* Step 3: Evidence */}
        {step >= 3 && (
          <>
            <Text style={{ fontSize: 16, fontWeight: '600', marginBottom: 8 }}>
              Evidence (Optional)
            </Text>
            <Text style={{ color: colors.onSurfaceVariant, fontSize: 12, marginBottom: 12 }}>
              Screenshots or photos can help our team investigate faster
            </Text>

            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}>
              {evidence.map((uri, index) => (
                <View key={index} style={{ position: 'relative' }}>
                  <Image
                    source={{ uri }}
                    style={{ width: 80, height: 80, borderRadius: 8 }}
                  />
                  <Pressable
                    onPress={() => removeEvidence(index)}
                    style={{
                      position: 'absolute',
                      top: -8,
                      right: -8,
                      backgroundColor: colors.error,
                      borderRadius: 12,
                      padding: 4,
                    }}
                  >
                    <MaterialCommunityIcons name="close" size={14} color="white" />
                  </Pressable>
                </View>
              ))}

              <Pressable
                onPress={pickImage}
                style={{
                  width: 80,
                  height: 80,
                  borderRadius: 8,
                  borderWidth: 2,
                  borderStyle: 'dashed',
                  borderColor: colors.primary,
                  justifyContent: 'center',
                  alignItems: 'center',
                }}
              >
                <MaterialCommunityIcons name="plus" size={24} color={colors.primary} />
              </Pressable>
            </View>

            {/* Options */}
            <Card style={{ marginBottom: 24 }}>
              <Card.Content>
                <Pressable
                  onPress={() => setBlockUser(!blockUser)}
                  style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 16 }}
                >
                  <Checkbox
                    status={blockUser ? 'checked' : 'unchecked'}
                    onPress={() => setBlockUser(!blockUser)}
                  />
                  <View style={{ marginLeft: 8, flex: 1 }}>
                    <Text style={{ fontWeight: '500' }}>Block this user</Text>
                    <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>
                      They won't be able to see or contact you
                    </Text>
                  </View>
                </Pressable>

                <Pressable
                  onPress={() => setMakePublic(!makePublic)}
                  style={{ flexDirection: 'row', alignItems: 'center' }}
                >
                  <Checkbox
                    status={makePublic ? 'checked' : 'unchecked'}
                    onPress={() => setMakePublic(!makePublic)}
                  />
                  <View style={{ marginLeft: 8, flex: 1 }}>
                    <Text style={{ fontWeight: '500' }}>Make report public</Text>
                    <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>
                      Others can see this report was filed (your identity stays private)
                    </Text>
                  </View>
                </Pressable>
              </Card.Content>
            </Card>
          </>
        )}

        <View style={{ height: 100 }} />
      </ScrollView>

      {/* Submit Button */}
      {step >= 3 && (
        <View style={{ padding: 16, paddingBottom: 32, backgroundColor: colors.background, borderTopWidth: 1, borderTopColor: colors.surfaceVariant }}>
          <Button
            mode="contained"
            onPress={submitReport}
            loading={submitting}
            disabled={submitting || !reportType || description.length < 20}
            buttonColor={colors.error}
          >
            Submit Report
          </Button>
        </View>
      )}
    </View>
  );
};

export default ReportUser;
