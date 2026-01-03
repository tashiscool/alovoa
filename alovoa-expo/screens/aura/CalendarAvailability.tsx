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
  Switch,
  Divider,
} from "react-native-paper";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import * as Global from "../../Global";
import * as URL from "../../URL";
import * as I18N from "../../i18n";
import { UserCalendarAvailability, DayAvailability, TimeSlot } from "../../myTypes";
import { STATUS_BAR_HEIGHT } from "../../assets/styles";

const i18n = I18N.getI18n();

const DAYS = [
  { key: 'monday', label: 'Monday', shortLabel: 'Mon' },
  { key: 'tuesday', label: 'Tuesday', shortLabel: 'Tue' },
  { key: 'wednesday', label: 'Wednesday', shortLabel: 'Wed' },
  { key: 'thursday', label: 'Thursday', shortLabel: 'Thu' },
  { key: 'friday', label: 'Friday', shortLabel: 'Fri' },
  { key: 'saturday', label: 'Saturday', shortLabel: 'Sat' },
  { key: 'sunday', label: 'Sunday', shortLabel: 'Sun' },
];

const TIME_SLOTS = [
  { start: '09:00', end: '12:00', label: 'Morning (9AM-12PM)' },
  { start: '12:00', end: '17:00', label: 'Afternoon (12PM-5PM)' },
  { start: '17:00', end: '21:00', label: 'Evening (5PM-9PM)' },
  { start: '21:00', end: '23:00', label: 'Night (9PM-11PM)' },
];

const CalendarAvailability = ({ navigation }: any) => {
  const { colors } = useTheme();
  const { height, width } = useWindowDimensions();

  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [availability, setAvailability] = React.useState<UserCalendarAvailability | null>(null);
  const [edited, setEdited] = React.useState(false);

  React.useEffect(() => {
    load();
  }, []);

  async function load() {
    setLoading(true);
    try {
      const response = await Global.Fetch(URL.API_CALENDAR_AVAILABILITY);
      setAvailability(response.data || getDefaultAvailability());
    } catch (e) {
      console.error(e);
      setAvailability(getDefaultAvailability());
    }
    setLoading(false);
  }

  function getDefaultAvailability(): UserCalendarAvailability {
    const defaultDays: Record<string, DayAvailability> = {};
    DAYS.forEach(day => {
      defaultDays[day.key] = {
        enabled: day.key !== 'sunday',
        slots: [
          { start: '17:00', end: '21:00' }, // Default to evenings
        ],
      };
    });

    return {
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      days: defaultDays,
      videoDatesEnabled: true,
      minimumNoticeHours: 24,
    };
  }

  async function save() {
    if (!availability) return;

    setSaving(true);
    try {
      await Global.Fetch(URL.API_CALENDAR_UPDATE, 'post', availability);
      Global.ShowToast("Availability saved!");
      setEdited(false);
    } catch (e) {
      console.error(e);
      Global.ShowToast(i18n.t('error.generic'));
    }
    setSaving(false);
  }

  function toggleDay(dayKey: string) {
    if (!availability) return;

    const newAvailability = { ...availability };
    newAvailability.days[dayKey].enabled = !newAvailability.days[dayKey].enabled;
    setAvailability(newAvailability);
    setEdited(true);
  }

  function toggleSlot(dayKey: string, slot: TimeSlot) {
    if (!availability) return;

    const newAvailability = { ...availability };
    const day = newAvailability.days[dayKey];
    const existingIndex = day.slots.findIndex(
      s => s.start === slot.start && s.end === slot.end
    );

    if (existingIndex >= 0) {
      day.slots.splice(existingIndex, 1);
    } else {
      day.slots.push(slot);
    }

    setAvailability(newAvailability);
    setEdited(true);
  }

  function hasSlot(dayKey: string, slot: TimeSlot): boolean {
    if (!availability) return false;
    return availability.days[dayKey]?.slots?.some(
      s => s.start === slot.start && s.end === slot.end
    ) || false;
  }

  function toggleVideoDates() {
    if (!availability) return;
    setAvailability({
      ...availability,
      videoDatesEnabled: !availability.videoDatesEnabled,
    });
    setEdited(true);
  }

  function updateNoticeHours(hours: number) {
    if (!availability) return;
    setAvailability({
      ...availability,
      minimumNoticeHours: hours,
    });
    setEdited(true);
  }

  if (loading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: colors.background }}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView style={{ flex: 1 }}>
        <View style={{ paddingTop: STATUS_BAR_HEIGHT + 16, paddingHorizontal: 16 }}>
          {/* Header */}
          <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 20 }}>
            <Pressable onPress={() => navigation.goBack()} style={{ marginRight: 12 }}>
              <MaterialCommunityIcons name="arrow-left" size={24} color={colors.onSurface} />
            </Pressable>
            <Text style={{ fontSize: 24, fontWeight: '600', flex: 1 }}>
              Availability
            </Text>
            {edited && (
              <Chip icon="content-save" textStyle={{ fontSize: 12 }}>
                Unsaved
              </Chip>
            )}
          </View>

          {/* Explanation */}
          <Card style={{ marginBottom: 20, backgroundColor: colors.primaryContainer }}>
            <Card.Content>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <MaterialCommunityIcons name="calendar-clock" size={32} color={colors.primary} />
                <View style={{ marginLeft: 12, flex: 1 }}>
                  <Text style={{ fontWeight: '600', color: colors.onPrimaryContainer }}>
                    Set Your Schedule
                  </Text>
                  <Text style={{ color: colors.onPrimaryContainer, fontSize: 12 }}>
                    Tell matches when you're free for video dates
                  </Text>
                </View>
              </View>
            </Card.Content>
          </Card>

          {/* Video Dates Toggle */}
          <Card style={{ marginBottom: 20 }}>
            <Card.Content>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ fontWeight: '600' }}>Video Dates</Text>
                  <Text style={{ color: colors.onSurfaceVariant, fontSize: 12 }}>
                    Allow matches to schedule video dates with you
                  </Text>
                </View>
                <Switch
                  value={availability?.videoDatesEnabled || false}
                  onValueChange={toggleVideoDates}
                />
              </View>
            </Card.Content>
          </Card>

          {/* Minimum Notice */}
          {availability?.videoDatesEnabled && (
            <Card style={{ marginBottom: 20 }}>
              <Card.Content>
                <Text style={{ fontWeight: '600', marginBottom: 12 }}>Minimum Notice</Text>
                <Text style={{ color: colors.onSurfaceVariant, fontSize: 12, marginBottom: 12 }}>
                  How much advance notice do you need?
                </Text>

                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                  {[2, 6, 12, 24, 48].map((hours) => (
                    <Chip
                      key={hours}
                      selected={availability.minimumNoticeHours === hours}
                      onPress={() => updateNoticeHours(hours)}
                      style={{
                        backgroundColor: availability.minimumNoticeHours === hours
                          ? colors.primaryContainer
                          : colors.surfaceVariant,
                      }}
                    >
                      {hours < 24 ? `${hours} hours` : `${hours / 24} day${hours > 24 ? 's' : ''}`}
                    </Chip>
                  ))}
                </View>
              </Card.Content>
            </Card>
          )}

          {/* Weekly Schedule */}
          {availability?.videoDatesEnabled && (
            <>
              <Text style={{ fontSize: 18, fontWeight: '600', marginBottom: 16 }}>
                Weekly Schedule
              </Text>

              {DAYS.map((day) => {
                const dayAvail = availability.days[day.key];

                return (
                  <Card key={day.key} style={{ marginBottom: 12 }}>
                    <Card.Content>
                      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: dayAvail?.enabled ? 12 : 0 }}>
                        <Text style={{ fontWeight: '600', fontSize: 16 }}>{day.label}</Text>
                        <Switch
                          value={dayAvail?.enabled || false}
                          onValueChange={() => toggleDay(day.key)}
                        />
                      </View>

                      {dayAvail?.enabled && (
                        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                          {TIME_SLOTS.map((slot) => {
                            const isSelected = hasSlot(day.key, slot);
                            return (
                              <Pressable
                                key={slot.start}
                                onPress={() => toggleSlot(day.key, slot)}
                                style={{
                                  paddingHorizontal: 12,
                                  paddingVertical: 8,
                                  borderRadius: 8,
                                  backgroundColor: isSelected ? colors.primaryContainer : colors.surfaceVariant,
                                  borderWidth: isSelected ? 2 : 0,
                                  borderColor: colors.primary,
                                }}
                              >
                                <Text style={{
                                  fontSize: 13,
                                  color: isSelected ? colors.primary : colors.onSurfaceVariant,
                                  fontWeight: isSelected ? '600' : '400',
                                }}>
                                  {slot.label}
                                </Text>
                              </Pressable>
                            );
                          })}
                        </View>
                      )}
                    </Card.Content>
                  </Card>
                );
              })}
            </>
          )}

          {/* Timezone */}
          <Card style={{ marginBottom: 24 }}>
            <Card.Content>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <MaterialCommunityIcons name="earth" size={24} color={colors.primary} />
                <View style={{ marginLeft: 12, flex: 1 }}>
                  <Text style={{ fontWeight: '600' }}>Timezone</Text>
                  <Text style={{ color: colors.onSurfaceVariant }}>
                    {availability?.timezone || 'Unknown'}
                  </Text>
                </View>
              </View>
            </Card.Content>
          </Card>

          <View style={{ height: 100 }} />
        </View>
      </ScrollView>

      {/* Save Button */}
      <View style={{ padding: 16, paddingBottom: 32, backgroundColor: colors.background, borderTopWidth: 1, borderTopColor: colors.surfaceVariant }}>
        <Button
          mode="contained"
          onPress={save}
          disabled={!edited || saving}
          loading={saving}
        >
          Save Availability
        </Button>
      </View>
    </View>
  );
};

export default CalendarAvailability;
