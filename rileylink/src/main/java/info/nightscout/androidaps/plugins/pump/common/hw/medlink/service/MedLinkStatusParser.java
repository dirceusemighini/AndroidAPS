package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus;

import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;

/**
 * Created by Dirceu on 13/12/20.
 */
public class MedLinkStatusParser {

    private static Pattern dateTimeFullPattern = Pattern.compile("\\d{2}-\\d{2}-\\d{4}\\s\\d{2}:\\d{2}");
    private static Pattern dateTimePartialPattern = Pattern.compile("\\d{2}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}");

    public static MedLinkPumpStatus parseStatus(String[] pumpAnswer, MedLinkPumpStatus pumpStatus, HasAndroidInjector injector) {

//        13‑12‑2020 18:36  54%
        Iterator<String> messageIterator = Arrays.stream(pumpAnswer).map(f -> f.toLowerCase()).iterator();
        String message = null;
        while (messageIterator.hasNext()) {
            message = messageIterator.next().trim();

            if (parseDateTime(message, dateTimeFullPattern, true) != null &&
                    message.contains("%")) {
                break;
            }
        }
        MedLinkPumpStatus timeMedLinkPumpStatus = parsePumpTimeMedLinkBattery(message, pumpStatus);
        MedLinkPumpStatus bgMedLinkPumpStatus = parseBG(messageIterator, timeMedLinkPumpStatus, injector);

        MedLinkPumpStatus lastBolusStatus = parseLastBolus(messageIterator, bgMedLinkPumpStatus);
//                18:36:49.381
//        18:36:49.495 Last bolus: 0.2u 13‑12‑20 18:32
        moveIterator(messageIterator);
//        18:36:49.496 Square bolus: 0.0u delivered: 0.000u
        moveIterator(messageIterator);
//        18:36:49.532 Square bolus time: 0h:00m / 0h:00m
        moveIterator(messageIterator);
//        18:36:49.570 ISIG: 20.62nA
        moveIterator(messageIterator);
//        18:36:49.607 Calibration factor: 6.419
        moveIterator(messageIterator);
//        18:36:49.681 Next calibration time:  5:00
        moveIterator(messageIterator);
//        18:36:49.683 Sensor uptime: 1483min
        MedLinkPumpStatus sageStatus = parseSensorAgeStatus(messageIterator, lastBolusStatus);
//        18:36:49.719 BG target:  75‑160
        MedLinkPumpStatus batteryStatus = parseBatteryVoltage(messageIterator, sageStatus);
//        18:36:49.832 Pump battery voltage: 1.43V
        MedLinkPumpStatus reservoirStatus = parseReservoir(messageIterator, batteryStatus);

//        18:36:49.907 Reservoir:  66.12u
        MedLinkPumpStatus basalStatus = parseCurrentBasal(messageIterator, reservoirStatus);
//        18:36:49.982 Basal scheme: STD
        moveIterator(messageIterator);
//        18:36:49.983 Basal: 0.600u/h
        MedLinkPumpStatus tempBasalStatus = parseTempBasal(messageIterator, basalStatus);

//        18:36:50.020 TBR: 100%   0h:00m
        MedLinkPumpStatus dailyTotal = parseTodayInsulin(messageIterator, tempBasalStatus);
//        18:36:50.058 Insulin today: 37.625u
        moveIterator(messageIterator);
//        18:36:50.095 Insulin yesterday: 48.625u
        moveIterator(messageIterator);

//        18:36:50.132 Max. bolus: 15.0u
//        moveIterator(messageIterator);
//        18:36:50.171 Easy bolus step: 0.1J
//        moveIterator(messageIterator);
//        18:36:50.244 Max. basal rate: 2.000J/h
//        moveIterator(messageIterator);
//        18:36:50.282 Insulin duration time: 3h
//        moveIterator(messageIterator);
        MedLinkPumpStatus pumpState = parsePumpState(dailyTotal, messageIterator);
//        18:36:50.448 Pump status: NORMAL
//        moveIterator(messageIterator);
//        18:36:50.471 EomEomEom
        return pumpState;
    }

    private static MedLinkPumpStatus parsePumpState(MedLinkPumpStatus pumpStatus,
                                                    Iterator<String> messageIterator) {
//        18:36:50.448 Pump status: NORMAL

        while (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            if (currentLine.contains("pump status")) {
                String status = currentLine.split(":")[1];
                if (status.equals("normal")) {
                    pumpStatus.pumpStatusType = PumpStatusType.Running;
                } else if (status.equals("suspend")) {
                    pumpStatus.pumpStatusType = PumpStatusType.Suspended;
                }
                break;
            } else if (currentLine.contains("eomeom")) {
                break;
            }
        }

        return pumpStatus;
    }

    private static MedLinkPumpStatus parseSensorAgeStatus(Iterator<String> messageIterator, MedLinkPumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            //        18:36:49.683 Sensor uptime: 1483min
            if (currentLine.contains("sensor uptime:")) {
                Pattern pattern = Pattern.compile("\\d+");
                Matcher matcher = pattern.matcher(currentLine);
                if (matcher.find()) {
                    pumpStatus.sensorAge = Integer.valueOf(matcher.group());
                }
            }
        }
        return pumpStatus;
    }

    private static MedLinkPumpStatus parseTodayInsulin(Iterator<String> messageIterator, MedLinkPumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            //        Insulin today: 37.625u
            if (currentLine.contains("insulin today:")) {
                Pattern reservoirPattern = Pattern.compile("\\d+\\.\\d+u");
                Matcher matcher = reservoirPattern.matcher(currentLine);
                if (matcher.find()) {
                    String totalInsulinToday = matcher.group();
                    pumpStatus.dailyTotalUnits = Double.parseDouble(totalInsulinToday.substring(0, totalInsulinToday.length() - 1));
                }
            }
        }
        return pumpStatus;
    }

    private static MedLinkPumpStatus parseTempBasal(Iterator<String> messageIterator, MedLinkPumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            //        18:36:50.020 TBR: 100%   0h:00m
            if (currentLine.contains("tbr:")) {
                Pattern reservoirPattern = Pattern.compile("\\d+%");
                Matcher matcher = reservoirPattern.matcher(currentLine);
                if (matcher.find()) {
                    String reservoirRemaining = matcher.group();
                    pumpStatus.tempBasalRatio = Integer.parseInt(reservoirRemaining.substring(0, reservoirRemaining.length() - 1));
                    Pattern remTempTimePattern = Pattern.compile("\\d+h:\\d+m");
                    Matcher remTempTimeMatcher = remTempTimePattern.matcher(currentLine);
                    if (remTempTimeMatcher.find()) {
                        String remaingTime = remTempTimeMatcher.group();
                        String[] hourMinute = remaingTime.split(":");
                        String hour = hourMinute[0];
                        String minute = hourMinute[1];
                        pumpStatus.tempBasalRemainMin = 60 * Integer.parseInt(hour.substring(0, hour.length() - 1)) + Integer.parseInt(minute.substring(0, minute.length() - 1));
                    }
                }
            }
        }
        return pumpStatus;
    }

    private static MedLinkPumpStatus parseCurrentBasal(Iterator<String> messageIterator, MedLinkPumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            //        18:36:49.983 Basal: 0.600u/h
            if (currentLine.contains("basal scheme:")) {
                String[] basalScheme = currentLine.split(":");
                pumpStatus.activeProfileName = basalScheme[1].trim();
                currentLine = messageIterator.next();
                if (currentLine.contains("basal:")) {
                    Pattern reservoirPattern = Pattern.compile("\\d+\\.\\d+u/h");
                    Matcher matcher = reservoirPattern.matcher(currentLine);
                    if (matcher.find()) {
                        String currentBasal = matcher.group();
                        pumpStatus.currentBasal = Double.parseDouble(currentBasal.substring(0, currentBasal.length() - 3));
                    }
                }
            }
        }
        return pumpStatus;
    }

    private static MedLinkPumpStatus parseReservoir(Iterator<String> messageIterator, MedLinkPumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            //        18:36:49.907 Reservoir:  66.12u
            if (currentLine.contains("reservoir")) {
                Pattern reservoirPattern = Pattern.compile("\\d+\\.\\d+u");
                Matcher matcher = reservoirPattern.matcher(currentLine);
                if (matcher.find()) {
                    String reservoirRemaining = matcher.group();
                    pumpStatus.reservoirRemainingUnits = Double.parseDouble(reservoirRemaining.substring(0, reservoirRemaining.length() - 1));
                }
            }
        }
        return pumpStatus;
    }

    private static void moveIterator(Iterator<String> messageIterator) {
        if (messageIterator.hasNext()) {
            messageIterator.next();
        }
    }

    private static MedLinkPumpStatus parseBatteryVoltage(Iterator<String> messageIterator, MedLinkPumpStatus pumpStatus) {
//        18:36:49.832 Pump battery voltage: 1.43V
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
            if (currentLine.contains("pump battery voltage")) {
                Pattern lastBolusPattern = Pattern.compile("\\d\\.\\d{1,2}V");
                Matcher matcher = lastBolusPattern.matcher(currentLine);
                if (matcher.find()) {
                    String batteryVoltage = matcher.group();
                    pumpStatus.batteryVoltage = Double.valueOf(batteryVoltage.substring(0, batteryVoltage.length() - 1));

                }
            }
        }
        return pumpStatus;
    }

    private static MedLinkPumpStatus parseLastBolus(Iterator<String> messageIterator, MedLinkPumpStatus pumpStatus) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();
//        18:36:49.495 Last bolus: 0.2u 13‑12‑20 18:32
            if (currentLine.contains("last bolus")) {
                Pattern lastBolusPattern = Pattern.compile("\\d{1,2}\\.\\du");
                Matcher matcher = lastBolusPattern.matcher(currentLine);
                if (matcher.find()) {
                    String lastBolusAmount = matcher.group();
                    pumpStatus.lastBolusAmount = Double.valueOf(lastBolusAmount.substring(0, lastBolusAmount.length() - 1));
                    Date dateTime = parseDateTime(currentLine, dateTimePartialPattern, false);
                    if (dateTime != null) {
                        pumpStatus.lastBolusTime = dateTime;
                    }
                }

            }

        }
        return pumpStatus;
    }

    private static MedLinkPumpStatus parseBG(Iterator<String> messageIterator, MedLinkPumpStatus pumpStatus, HasAndroidInjector injector) {
        if (messageIterator.hasNext()) {
            String currentLine = messageIterator.next();

//            BG: 120 13‑12‑20 18:33

            Pattern bgLinePattern = Pattern.compile("bg:\\s+\\d{2,3}");
            Matcher matcher = bgLinePattern.matcher(currentLine);
            if (matcher.find()) {
                String matched = matcher.group(0);
                Double bg = Double.valueOf(matched.substring(3));
                Date bgDate = parseDateTime(currentLine, dateTimePartialPattern, false);
                if(bgDate != null) {
                    BgReading reading = new BgReading(injector,
                            bgDate.getTime(), bg, null, pumpStatus.lastBGTimestamp,
                            pumpStatus.latestBG, Source.PUMP);
                    pumpStatus.reading = reading;
                }
                pumpStatus.lastBGTimestamp = bgDate.getTime();
                pumpStatus.latestBG = bg;
            }
        }
        return pumpStatus;
    }

    private static MedLinkPumpStatus parsePumpTimeMedLinkBattery(String currentLine, MedLinkPumpStatus pumpStatus) {
        if (currentLine != null) {
//            String currentLine = messageIterator.next();
//            while(currentLine.startsWith("ready")){
//                currentLine = messageIterator.next();
//            }
            //        13‑12‑2020 18:36  54%

            Date dateTime = parseDateTime(currentLine, dateTimeFullPattern, true);
            if (dateTime != null) {
                pumpStatus.lastDateTime = dateTime.getTime();
            }
            Pattern battery = Pattern.compile("\\d+%");
            Matcher batteryMatcher = battery.matcher(currentLine);
            if (batteryMatcher.find()) {
                String percentage = batteryMatcher.group(0);
                pumpStatus.batteryRemaining = Integer.parseInt(percentage.substring(0, percentage.length() - 1));
            }
        }
        return pumpStatus;
    }

    private static Date parseDateTime(String currentLine, Pattern pattern, boolean fourDigitYear) {
        Matcher matcher = pattern.matcher(currentLine);
        if (matcher.find()) {
            String datePattern;
            if (fourDigitYear) {
                datePattern = "dd-MM-yyyy HH:mm";
            } else {
                datePattern = "dd-MM-yy HH:mm";
            }
            SimpleDateFormat formatter = new SimpleDateFormat(datePattern, Locale.getDefault());
            return formatter.parse(matcher.group(), new ParsePosition(0));
        } else {
            return null;

        }
    }

    public boolean partialMatch(MedLinkPumpStatus pumpStatus) {
        return pumpStatus.lastDateTime != 0 || pumpStatus.lastBolusTime != null ||
                pumpStatus.batteryVoltage != 0d ||
                pumpStatus.reservoirRemainingUnits != 0.0d || pumpStatus.currentBasal != 0.0d ||
                pumpStatus.dailyTotalUnits != null;
    }

    public boolean fullMatch(MedLinkPumpStatus pumpStatus) {
        return pumpStatus.lastDateTime != 0 && pumpStatus.lastBolusTime != null &&
                pumpStatus.batteryVoltage != 0d &&
                pumpStatus.dailyTotalUnits != null;
    }
}