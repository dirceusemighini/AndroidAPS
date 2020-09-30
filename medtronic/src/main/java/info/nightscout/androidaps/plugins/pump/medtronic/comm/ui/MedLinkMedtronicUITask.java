package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui;

import org.joda.time.LocalDateTime;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedLinkMedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicUIResponseType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil;

/**
 * Created by Dirceu on 25/09/20.
 * copied from {@link MedtronicUITask}
 */

public class MedLinkMedtronicUITask {
    //TODO build parsers here

    @Inject RxBusWrapper rxBus;
    @Inject AAPSLogger aapsLogger;
    @Inject MedtronicPumpStatus medtronicPumpStatus;
    @Inject MedLinkMedtronicUtil medtronicUtil;

    private final HasAndroidInjector injector;

    public MedLinkMedtronicCommandType commandType;
    public Object returnData;
    String errorDescription;
    // boolean invalid = false;
    private Object[] parameters;
    // private boolean received;
    MedtronicUIResponseType responseType;


    public MedLinkMedtronicUITask(HasAndroidInjector injector, MedLinkMedtronicCommandType commandType) {
        this.injector = injector;
        this.injector.androidInjector().inject(this);
        this.commandType = commandType;
    }


    public MedLinkMedtronicUITask(HasAndroidInjector injector, MedLinkMedtronicCommandType commandType, Object... parameters) {
        this.injector = injector;
        this.injector.androidInjector().inject(this);
        this.commandType = commandType;
        this.parameters = parameters;
    }


    public void execute(MedLinkMedtronicCommunicationManager communicationManager) {

        aapsLogger.debug(LTag.PUMP, "MedtronicUITask: @@@ In execute. {}", commandType);

        switch (commandType) {
            case PumpModel: {
                returnData = communicationManager.getPumpModel();
            }
            break;
//
//            case GetBasalProfileSTD: {
//                returnData = communicationManager.getBasalProfile();
//            }
//            break;
//
//            case GetRemainingInsulin: {
//                returnData = communicationManager.getRemainingInsulin();
//            }
//            break;

            case GetRealTimeClock: {
                returnData = communicationManager.getPumpTime();
                medtronicUtil.setPumpTime(null);
            }
            break;

//            case SetRealTimeClock: {
//                returnData = communicationManager.setPumpTime();
//            }
//            break;
//
//            case GetBatteryStatus: {
//                returnData = communicationManager.getRemainingBattery();
//            }
//            break;
//
//            case SetTemporaryBasal: {
//                TempBasalPair tbr = getTBRSettings();
//                if (tbr != null) {
//                    returnData = communicationManager.setTBR(tbr);
//                }
//            }
//            break;
//
//            case ReadTemporaryBasal: {
//                returnData = communicationManager.getTemporaryBasal();
//            }
//            break;


            case Settings:
            case Settings_512: {
                returnData = communicationManager.getPumpSettings();
            }
            break;

            case SetBolus: {
                Double amount = getDoubleFromParameters(0);

                if (amount != null)
                    returnData = communicationManager.setBolus(amount);
            }
            break;

//            case CancelTBR: {
//                returnData = communicationManager.cancelTBR();
//            }
//            break;

//            case SetBasalProfileSTD:
//            case SetBasalProfileA: {
//                BasalProfile profile = (BasalProfile) parameters[0];
//
//                returnData = communicationManager.setBasalProfile(profile);
//            }
//            break;

//            case GetHistoryData: {
//                returnData = communicationManager.getPumpHistory((PumpHistoryEntry) parameters[0],
//                        (LocalDateTime) parameters[1]);
//            }
//            break;

            default: {
                aapsLogger.warn(LTag.PUMP, "This commandType is not supported (yet) - {}.", commandType);
                // invalid = true;
                responseType = MedtronicUIResponseType.Invalid;
            }

        }

        if (responseType == null) {
            if (returnData == null) {
                errorDescription = communicationManager.getErrorResponse();
                this.responseType = MedtronicUIResponseType.Error;
            } else {
                this.responseType = MedtronicUIResponseType.Data;
            }
        }

    }


    private TempBasalPair getTBRSettings() {
        return new TempBasalPair(getDoubleFromParameters(0), //
                false, //
                getIntegerFromParameters(1));
    }


    private Float getFloatFromParameters(int index) {
        return (Float) parameters[index];
    }


    Double getDoubleFromParameters(int index) {
        return (Double) parameters[index];
    }


    private Integer getIntegerFromParameters(int index) {
        return (Integer) parameters[index];
    }


    public Object getResult() {
        return returnData;
    }


    public boolean isReceived() {
        return (returnData != null || errorDescription != null);
    }


    void postProcess(MedLinkMedtronicUIPostprocessor postprocessor) {

        aapsLogger.debug(LTag.PUMP, "MedtronicUITask: @@@ In execute. {}", commandType);

        if (responseType == MedtronicUIResponseType.Data) {
            postprocessor.postProcessData(this);
        }

        if (responseType == MedtronicUIResponseType.Invalid) {
            rxBus.send(new EventRileyLinkDeviceStatusChange(PumpDeviceState.ErrorWhenCommunicating,
                    "Unsupported command in MedtronicUITask"));
        } else if (responseType == MedtronicUIResponseType.Error) {
            rxBus.send(new EventRileyLinkDeviceStatusChange(PumpDeviceState.ErrorWhenCommunicating,
                    errorDescription));
        } else {
            rxBus.send(new EventMedtronicPumpValuesChanged());
            medtronicPumpStatus.setLastCommunicationToNow();
        }

        medtronicUtil.setCurrentCommand(null);
    }


    public boolean hasData() {
        return (responseType == MedtronicUIResponseType.Data);
    }


    Object getParameter(int index) {
        return parameters[index];
    }


    public MedtronicUIResponseType getResponseType() {
        return this.responseType;
    }

}