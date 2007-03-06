/**
 * TestFrameXMLParser
 * Date: Oct 18, 2005 10:33:34 AM
 * 
 * (c) 2005 IceCube Collaboration
 */
package icecube.daq.testFrame;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.io.SAXReader;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.DocumentException;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.net.URL;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import icecube.daq.payload.*;
import icecube.daq.payload.impl.SourceID4B;
import icecube.daq.common.*;
import icecube.daq.io.*;
import icecube.daq.testUtil.*;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.secBuilder.SBComponent;
import icecube.daq.secBuilder.SecBuilderCompConfig;
import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.trigger.control.ITriggerManager;
import icecube.daq.trigger.control.ITriggerControl;
import icecube.daq.trigger.config.TriggerBuilder;
import icecube.daq.trigger.component.IniceTriggerComponent;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.stringhub.StringHubComponent;

/**
 * This class parses the xml configuration file for the test frame
 *
 * @author artur
 * @version $Id: TestFrameXMLParser.java,v 1.30 2006/08/24 21:50:25 artur Exp $
 */
public class TestFrameXMLParser implements TestFrameConstants {

    private static final Log log = LogFactory.getLog(TestFrameXMLParser.class);

    private Document root;
    private Element testFrameElement;
    private List daqComponents = new ArrayList();

    // pipes connecting stringProcessors and iniceTrigger
    private List spOutputToIniceSinkChannels = new ArrayList();
    private List spOutputToIniceSourceChannels = new ArrayList();

    // pipes connecting stringProcessors and tcalBuilder
    private List spOutputToTcalSinkChannels = new ArrayList();
    private List spOutputToTcalSourceChannels = new ArrayList();

    // pipes connecting stringProcessors and monitorBuilder
    private List spOutputToMonitorSinkChannels = new ArrayList();
    private List spOutputToMonitorSourceChannels = new ArrayList();

    // pipes connecting stringProcessors and snBuilder
    private List spOutputToSNSinkChannels = new ArrayList();
    private List spOutputToSNSourceChannels = new ArrayList();

    // pipes connecting stringProcessors and eventBuilder for the request engine
    private List ebReqOutputToSPSinkChannels = new ArrayList();
    private List ebReqOutputToSPSourceChannels = new ArrayList();

    // pipes connecting stringProcessors and eventBuilder for the flush engine
    private List ebFlushOutputToSPSinkChannels = new ArrayList();
    private List ebFlushOutputToSPSourceChannels = new ArrayList();

    // pipes connecting stringProc and eventBuilder to send hit data
    private List ebOutputSinkChannels = new ArrayList();
    private List ebOutputSourceChannels = new ArrayList();


    // pipes connecting icetopDataHandlers and icetopTrigger
    private List icetopDataHandlerOutputToIcetopSinkChannels = new ArrayList();
    private List icetopDataHandlerOutputToIcetopSourceChannels = new ArrayList();

    // pipes connecting icetopDataHandlers and tcalBuilder
    private List icetopDataHandlerOutputToTcalSinkChannels = new ArrayList();
    private List icetopDataHandlerOutputToTcalSourceChannels = new ArrayList();

    // pipes connecting icetopDataHandlers and monitorBuilder
    private List icetopDataHandlerOutputToMonitorSinkChannels = new ArrayList();
    private List icetopDataHandlerOutputToMonitorSourceChannels = new ArrayList();

    // pipes connecting icetopDataHandlers and monitorBuilder
    private List icetopDataHandlerOutputToSNSinkChannels = new ArrayList();
    private List icetopDataHandlerOutputToSNSourceChannels = new ArrayList();

    // pipes connecting icetopDataHandlers and eventBuilder for the request engine
    private List ebReqOutputToIcetopDataHandlerSinkChannels = new ArrayList();
    private List ebReqOutputToIcetopDataHandlerSourceChannels = new ArrayList();

    // pipes connecting icetopDataHandlers and eventBuilder for the flush engine
    private List ebFlushOutputToIcetopDataHandlerSinkChannels = new ArrayList();
    private List ebFlushOutputToIcetopDataHandlerSourceChannels = new ArrayList();

    // pipes connecting icetopDataHandler and eventBuilder to send hit data
    private List ebOutputIcetopDataHandlerSinkChannels = new ArrayList();
    private List ebOutputIcetopDataHandlerSourceChannels = new ArrayList();

    private List spSourceIDs = new ArrayList();
    private List icetopDataHandlerSourceIDs = new ArrayList();

    // pipes connecting iniceTrigger and globalTrigger
    private Pipe.SinkChannel iniceTriggerOutputSinkChannel;
    private Pipe.SourceChannel iniceTriggerOutputSourceChannel;

    // pipes connecting iceTop and globalTrigger
    private Pipe.SinkChannel icetopTriggerOutputSinkChannel;
    private Pipe.SourceChannel icetopTriggerOutputSourceChannel;

    // pipes connecting globalTrigger and eventBuilder
    private Pipe.SourceChannel globalTriggerOutputSourceChannel;
    private Pipe.SinkChannel globalTriggerOutputSinkChannel;
    private int runNumber = 0;
    private boolean isInIceActive = false;

    public TestFrameXMLParser(String configFile) throws Exception {

        if (configFile == null) {
            throw new IllegalArgumentException("ConfigFile cannot be null.");
        }

        URL url = getClass().getResource("/" + configFile);
        if (url == null) {
            throw new IllegalArgumentException("couldn't find " + configFile);
        }

        root = new SAXReader().read(url.openStream());
        testFrameElement = root.getRootElement();
        if (testFrameElement == null) {
            throw new IllegalArgumentException("invalid configuration of " + configFile);
        }

        Element runNumberElement = testFrameElement.element(RUN_NUMBER);
        if (runNumberElement != null && runNumberElement.hasContent()) {
            runNumber = Integer.parseInt(runNumberElement.getText());
        }
    }

    public void parse() throws Exception {
        initStringHubs();
        initSecondaryBuilders();
        initIniceTrigger();
        initGlobalTrigger();
        initEventBuilder();
    }

    public void initSecondaryBuilders() throws DAQCompException {
        Element sbElement = testFrameElement.element(SECONDARY_BUILDERS);
        if (sbElement == null || !sbElement.hasContent()) {
            return;
        }

        // check if the secondaryBuilders is active
        Element activeElement = sbElement.element(ACTIVE);
        if (activeElement != null) {
            if (activeElement.getText().equalsIgnoreCase("false")) {
                return;
            }
        } else {
            return;
        }

        // create secondary builder component
        int granularity = 256;
        Element granularityElement = sbElement.element(GRANULARITY);
        if (granularityElement != null) {
            granularity = Integer.parseInt(granularityElement.getText());
        }

        long maxCachedBytes = 30000000;
        Element cachedBytesElement = sbElement.element(MAX_NUM_CACHED_BYTES);
        if (cachedBytesElement != null) {
            maxCachedBytes = Long.parseLong(cachedBytesElement.getText());
        }

        long maxNumAcquiredBytes = 30000000;
        Element acquiredBytesElement = sbElement.element(MAX_NUM_ACQUIRED_BYTES);
        if (acquiredBytesElement != null) {
            maxNumAcquiredBytes = Long.parseLong(acquiredBytesElement.getText());
        }

        int secBuilderCounter = 0;
        boolean isTcalEnabled = false;
        Element isTcalElement = sbElement.element(IS_TCAL_ENABLED);
        if (isTcalElement != null) {
            isTcalEnabled = Boolean.parseBoolean(isTcalElement.getText());
            if (isTcalEnabled) {
                ++secBuilderCounter;
            }
        }

        boolean isSnEnabled = false;
        Element isSnElement = sbElement.element(IS_SN_ENABLED);
        if (isSnElement != null) {
            isSnEnabled = Boolean.parseBoolean(isSnElement.getText());
            if (isSnEnabled) {
                ++secBuilderCounter;
            }
        }

        boolean isMoniEnabled = false;
        Element isMoniElement = sbElement.element(IS_MONI_ENABLED);
        if (isMoniElement != null) {
            isMoniEnabled = Boolean.parseBoolean(isMoniElement.getText());
            if (isMoniEnabled) {
                ++secBuilderCounter;
            }
        }

        SBComponent sbComponent = new SBComponent(new SecBuilderCompConfig(granularity, maxCachedBytes, maxNumAcquiredBytes,
                isTcalEnabled, isSnEnabled, isMoniEnabled, false));
        sbComponent.setRunNumber(runNumber);

        // configure input source
        String source = RANDOM_GENERATOR;
        Element sourceElement = sbElement.element(SOURCE);
        List inputSourceManagers = new ArrayList();
        if (sourceElement != null) {
            if (source.equalsIgnoreCase(RANDOM_GENERATOR)) {
                Element inputElement = sbElement.element(INPUT_SOURCE);
                try {
                    for (int i = 0; i < secBuilderCounter; i++) {
                        inputSourceManagers.add(new InputSourceGeneratorMng
                                (InputSourceXMLParser.parseGenerator(inputElement.getText())));
                    }
                } catch (DocumentException de) {
                    log.error("Problem on parsing the generator: ", de);
                    throw new RuntimeException(de);
                }
            }
        }

        // make up input channels
        DAQTestComponent secBuilder = new DAQTestComponent(sbComponent);
        if (inputSourceManagers.size() > 0) {
            if (isTcalEnabled) {
                InputSourceManager inputSourceMng = (InputSourceManager) inputSourceManagers.remove(0);
                secBuilder.addInputSourceManager(inputSourceMng);
                addInputChannels(sbComponent, DAQConnector.TYPE_TCAL_DATA, inputSourceMng);
            }
            if (isSnEnabled) {
                InputSourceManager inputSourceMng = (InputSourceManager) inputSourceManagers.remove(0);
                secBuilder.addInputSourceManager(inputSourceMng);
                addInputChannels(sbComponent, DAQConnector.TYPE_SN_DATA, inputSourceMng);
            }
            if (isMoniEnabled) {
                InputSourceManager inputSourceMng = (InputSourceManager) inputSourceManagers.remove(0);
                secBuilder.addInputSourceManager(inputSourceMng);
                addInputChannels(sbComponent, DAQConnector.TYPE_MONI_DATA, inputSourceMng);
            }
        }

        daqComponents.add(secBuilder);
    }

    private void initStringHubs() throws Exception {

        Properties properties = new Properties(System.getProperties());

        Element stringHubsElement = testFrameElement.element(STRING_HUBS);
        if (stringHubsElement == null || !stringHubsElement.hasContent()) {
            return;
        }

        Iterator stringHubItr = stringHubsElement.elementIterator();

        while (stringHubItr.hasNext()) {
            Element shElement = (Element) stringHubItr.next();
            Element activeElement = shElement.element(ACTIVE);
            if (activeElement != null) {
                if (activeElement.getText().equalsIgnoreCase("false")) {
                    return;
                }
            }

            Element sourceIDElement = shElement.element(SOURCE_ID);
            if (sourceIDElement != null) {
                properties.setProperty("icecube.daq.stringhub.componentId", sourceIDElement.getText());
            }

            Element inputSourceElement = shElement.element(INPUT_SOURCE);
            String simulator = "false";
            if (inputSourceElement != null) {
                if (inputSourceElement.getText().equals(SIMULATOR)){
                    simulator = "true";
                }
            }
            properties.setProperty("icecube.daq.stringhub.simulation", simulator);

            Element shSimConfigElement = shElement.element(SH_SIM_CONFIG);
            String shSimConfigFileName = "null";
            if (shSimConfigElement != null){
                shSimConfigFileName = shSimConfigElement.getText();
            }
            properties.setProperty("icecube.daq.stringhub.simulation.config", shSimConfigFileName);

            // TODO: fix this later
            properties.setProperty("icecube.daq.stringhub.configPath", "./config");

            System.setProperties(properties);

            StringHubComponent shComp = new StringHubComponent(Integer.getInteger("icecube.daq.stringhub.componentId"));
            shComp.setGlobalConfigurationDir("./config");

            Element ebReqSourceElement = shElement.element(EB_REQ_DATA_SOURCE);
            Pipe.SinkChannel ebReqSinkChannel = null;
            Pipe.SourceChannel ebReqSourceChannel = null;
            if (ebReqSourceElement != null) {
                if (ebReqSourceElement.getText().equalsIgnoreCase(EB_REQ_DATA_SOURCE)) {
                    // create and add to the list sink/source channels that feed EB req engine
                    Pipe pipe = Pipe.open();
                    ebReqSinkChannel = pipe.sink();
                    ebReqSinkChannel.configureBlocking(false);
                    ebReqOutputToSPSinkChannels.add(ebReqSinkChannel);
                    ebReqSourceChannel = pipe.source();
                    ebReqSourceChannel.configureBlocking(false);
                    ebReqOutputToSPSourceChannels.add(ebReqSourceChannel);
                }
            }

            if (ebReqSinkChannel != null && ebReqSourceChannel != null) {
                addInputChannel(shComp, DAQConnector.TYPE_READOUT_REQUEST, ebReqSourceChannel);
            }

            Element ebDestElement = shElement.element(EB_DATA_DEST);
            Pipe.SourceChannel ebOutputSourceChannel = null;
            Pipe.SinkChannel ebOutputSinkChannel = null;
            if (ebDestElement != null){
                if (ebDestElement.getText().equalsIgnoreCase(EVENT_BUILDER)) {
                    Pipe pipe = Pipe.open();
                    ebOutputSourceChannel = pipe.source();
                    ebOutputSourceChannel.configureBlocking(false);
                    ebOutputSinkChannel = pipe.sink();
                    ebOutputSinkChannel.configureBlocking(false);
                    ebOutputSourceChannels.add(ebOutputSourceChannel);
                    ebOutputSinkChannels.add(ebOutputSinkChannel);
                }
            }

            if (ebOutputSinkChannel != null && ebOutputSourceChannel != null){
                addOutputChannel(shComp, DAQConnector.TYPE_READOUT_DATA, ebOutputSinkChannel,
                    new SourceID4B(shComp.getId()));
            }

            Element hitdataDestElement = shElement.element(HIT_DATA_DEST);
            Pipe.SinkChannel spDataOutputSinkChannel = null;
            Pipe.SourceChannel spDataOutputSourceChannel = null;
            if (hitdataDestElement != null) {
                if (hitdataDestElement.getText().equalsIgnoreCase(INICE_TRIGGER)) {
                    // create and add to the list sink/source channels that feed iniceTrigger
                    Pipe pipe = Pipe.open();
                    spDataOutputSinkChannel = pipe.sink();
                    spDataOutputSinkChannel.configureBlocking(false);
                    spOutputToIniceSinkChannels.add(spDataOutputSinkChannel);
                    spDataOutputSourceChannel = pipe.source();
                    spDataOutputSourceChannel.configureBlocking(false);
                    spOutputToIniceSourceChannels.add(spDataOutputSourceChannel);
                }
            }
            if (spDataOutputSinkChannel != null && spDataOutputSourceChannel != null){
                addOutputChannel(shComp, DAQConnector.TYPE_STRING_HIT, spDataOutputSinkChannel,
                    new SourceID4B(shComp.getId()));
            }

            shComp.configuring("hub1001sim");
        }
    }

    private void initIniceTrigger() throws DAQCompException {

        Element iniceTriggerElement = testFrameElement.element(INICE_TRIGGER);
        if (iniceTriggerElement == null || !iniceTriggerElement.hasContent()) {
            return;
        }

        // check if the secondaryBuilders is active
        Element activeElement = iniceTriggerElement.element(ACTIVE);
        if (activeElement != null) {
            if (activeElement.getText().equalsIgnoreCase("false")) {
                return;
            }
        } else {
            return;
        }

        boolean connectedToStringProc = false;
        String source = RANDOM_GENERATOR;
        Element sourceElement = iniceTriggerElement.element(SOURCE);
        InputSourceManager inputSourceManager = null;
        if (sourceElement != null) {
            source = sourceElement.getText();
            if (source.equalsIgnoreCase(STRING_HUB)) {
                connectedToStringProc = true;
            } else if (source.equalsIgnoreCase(RANDOM_GENERATOR)) {
                Element inputElement = iniceTriggerElement.element(INPUT_SOURCE);
                try {
                    inputSourceManager = new InputSourceGeneratorMng(InputSourceXMLParser.parseGenerator(inputElement.getText()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (source.equalsIgnoreCase(FILE_READER)) {
                Element inputElement = iniceTriggerElement.element(INPUT_SOURCE);
                try {
                    inputSourceManager = new FileInputSourceMng(InputSourceXMLParser.parseFileInput(inputElement.getText()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException("invalid value for source element");
            }
        }

        String dest = DISPOSER_OUTPUT_DEST;
        Element destElement = iniceTriggerElement.element(DEST);
        OutputDestination[] dests = null;
        if (destElement != null) {
            dest = destElement.getText();
            if (dest.equalsIgnoreCase(GLOBAL_TRIGGER)) {
                try {
                    Pipe pipe = Pipe.open();
                    iniceTriggerOutputSourceChannel = pipe.source();
                    iniceTriggerOutputSourceChannel.configureBlocking(false);
                    iniceTriggerOutputSinkChannel = pipe.sink();
                    iniceTriggerOutputSinkChannel.configureBlocking(false);
                    isInIceActive = true;
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            } else if (dest.equalsIgnoreCase(DISPOSER_OUTPUT_DEST)) {
                Element outputElement = iniceTriggerElement.element(OUTPUT_DESTS);
                try {
                    dests = OutputDestinationXMLParser.parseDisposerOutputDestination(outputElement.getText());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (dest.equalsIgnoreCase(FILE_WRITER_CHANNEL)) {
                Element outputElement = iniceTriggerElement.element(OUTPUT_DESTS);
                try {
                    dests = OutputDestinationXMLParser.parseFileOutput(outputElement.getText());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException("invalid value for dest element");
            }
        }

        IniceTriggerComponent iniceTriggerComp = new IniceTriggerComponent();
        DAQTestComponent triggerComp = new DAQTestComponent(iniceTriggerComp);
        try {
            parseTriggerBuilder(iniceTriggerElement, iniceTriggerComp.getTriggerManager());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // make up input channels
        if (inputSourceManager != null) {
            addInputChannels(iniceTriggerComp, DAQConnector.TYPE_STRING_HIT, inputSourceManager);
            triggerComp.addInputSourceManager(inputSourceManager);
        } else if (connectedToStringProc) {
            for (int i = 0; i < spOutputToIniceSourceChannels.size(); i++) {
                Pipe.SourceChannel sourceChannel = (Pipe.SourceChannel) spOutputToIniceSourceChannels.get(i);
                addInputChannel(iniceTriggerComp, DAQConnector.TYPE_STRING_HIT, sourceChannel);
            }
        }
        // make up output channels
        if (dests != null) {
            addOutputChannels(iniceTriggerComp, DAQConnector.TYPE_TRIGGER,
                    dests, iniceTriggerComp.getSourceID());
            triggerComp.setOutputDestination(dests);
        } else {
            // tie the output channel to global trigger
            addOutputChannel(iniceTriggerComp, DAQConnector.TYPE_TRIGGER, iniceTriggerOutputSinkChannel,
                    iniceTriggerComp.getSourceID());
        }
        daqComponents.add(triggerComp);
    }


    private void initGlobalTrigger() throws DAQCompException {

        Element globalTriggerElement = testFrameElement.element(GLOBAL_TRIGGER);
        if (globalTriggerElement == null || !globalTriggerElement.hasContent()) {
            return;
        }

        // check if the secondaryBuilders is active
        Element activeElement = globalTriggerElement.element(ACTIVE);
        if (activeElement != null) {
            if (activeElement.getText().equalsIgnoreCase("false")) {
                return;
            }
        } else {
            return;
        }

        String source = RANDOM_GENERATOR;
        Element sourceElement = globalTriggerElement.element(SOURCE);
        InputSourceManager inputSourceManager = null;
        if (sourceElement != null) {
            source = sourceElement.getText();
            if (source.equalsIgnoreCase(INICE_TRIGGER)) {
                isInIceActive = true;
            } else if (source.equalsIgnoreCase(RANDOM_GENERATOR)) {
                Element inputElement = globalTriggerElement.element(INPUT_SOURCE);
                try {
                    inputSourceManager = new InputSourceGeneratorMng(InputSourceXMLParser.parseGenerator(inputElement.getText()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (source.equalsIgnoreCase(FILE_READER)) {
                Element inputElement = globalTriggerElement.element(INPUT_SOURCE);
                try {
                    inputSourceManager = new FileInputSourceMng(InputSourceXMLParser.parseFileInput(inputElement.getText()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException("invalid value for source element");
            }
        }

        String dest = DISPOSER_OUTPUT_DEST;
        Element destElement = globalTriggerElement.element(DEST);
        OutputDestination[] dests = null;
        if (destElement != null) {
            dest = destElement.getText();
            if (dest.equalsIgnoreCase(EVENT_BUILDER)) {
                try {
                    Pipe pipe = Pipe.open();
                    globalTriggerOutputSourceChannel = pipe.source();
                    globalTriggerOutputSourceChannel.configureBlocking(false);
                    globalTriggerOutputSinkChannel = pipe.sink();
                    globalTriggerOutputSinkChannel.configureBlocking(false);
                    log.info("connecting GlobalTrigger -> EventBuilder.....");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (dest.equalsIgnoreCase(DISPOSER_OUTPUT_DEST)) {
                try {
                    dests = OutputDestinationXMLParser.parseDisposerOutputDestination(globalTriggerElement.element(OUTPUT_DESTS).getText());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (dest.equalsIgnoreCase(FILE_WRITER_CHANNEL)) {
                try {
                    dests = OutputDestinationXMLParser.parseFileOutput(globalTriggerElement.element(OUTPUT_DESTS).getText());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException("invalid value for dest element " + dest);
            }
        }

        GlobalTriggerComponent globalTriggerComp = new GlobalTriggerComponent();
        DAQTestComponent triggerComp = new DAQTestComponent(globalTriggerComp);
        try {
            parseTriggerBuilder(globalTriggerElement, globalTriggerComp.getTriggerManager());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // add input channels from inice trigger
        if (inputSourceManager != null) {
            addInputChannels(globalTriggerComp, DAQConnector.TYPE_TRIGGER, inputSourceManager);
            triggerComp.addInputSourceManager(inputSourceManager);
        } else {
            if (isInIceActive) {
                addInputChannel(globalTriggerComp, DAQConnector.TYPE_TRIGGER, iniceTriggerOutputSourceChannel);
            } else {
                log.error("Config error: InIce input to GT is off!");
            }
        }

        // add output channels
        if (dests != null) {
            addOutputChannels(globalTriggerComp, DAQConnector.TYPE_GLOBAL_TRIGGER, dests,
                    globalTriggerComp.getSourceID());
            triggerComp.setOutputDestination(dests);
        } else {
            addOutputChannel(globalTriggerComp, DAQConnector.TYPE_GLOBAL_TRIGGER, globalTriggerOutputSinkChannel,
                    globalTriggerComp.getSourceID());
        }

        daqComponents.add(triggerComp);
    }

    private void initEventBuilder() throws DAQCompException {
        Element ebElement = testFrameElement.element(EVENT_BUILDER);
        if (ebElement == null || !ebElement.hasContent()) {
            return;
        }

        // check if the secondaryBuilders is active
        Element activeElement = ebElement.element(ACTIVE);
        if (activeElement != null) {
            if (activeElement.getText().equalsIgnoreCase("false")) {
                return;
            }
        } else {
            return;
        }

        // init EBComponent
        EBComponent ebComp = new EBComponent();
        ebComp.setRunNumber(runNumber);

        // get input output engines out of EBComponent
        String frontEndSource = RANDOM_GENERATOR;
        Element frontEndSourceElement = ebElement.element(EB_FRONT_END);
        boolean isGlobalTrigger = false;
        InputSource[] inputSources = null;
        InputSourceManager gtInputSourceManager = null;
        Element ebInputSourceElement = ebElement.element(EB_INPUT_SOURCE);
        if (frontEndSourceElement != null) {
            frontEndSource = frontEndSourceElement.getText();
            if (frontEndSource.equalsIgnoreCase(GLOBAL_TRIGGER)) {
                isGlobalTrigger = true;
            } else if (frontEndSource.equalsIgnoreCase(RANDOM_GENERATOR)) {
                if (ebInputSourceElement != null) {
                    try {
                        inputSources = InputSourceXMLParser.parseEventGenerator(ebInputSourceElement.getText());
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new RuntimeException("ebInputSource element must not be null");
                }
                gtInputSourceManager = new InputSourceGeneratorMng(new InputSource[]{inputSources[0]});
            } else if (frontEndSource.equalsIgnoreCase(FILE_READER)) {
                if (ebInputSourceElement != null) {
                    try {
                        inputSources = InputSourceXMLParser.parseFileInput(ebInputSourceElement.getText());
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new RuntimeException("ebInputSource element must not be null");
                }
                for (int i = 0; i < inputSources.length; i++) {
                    if (inputSources[i].getSourceID() > 2999) {
                        gtInputSourceManager = new FileInputSourceMng(new InputSource[]{inputSources[i]});
                        break;
                    }
                }
            } else {
                throw new RuntimeException("invalid value for source element");
            }
        }

        InputSourceManager spInputSourceManager = null;
        String backEndSource = RANDOM_GENERATOR;
        boolean isStringHub = false;
        Element backEndSourceElement = ebElement.element(EB_BACK_END);
        if (backEndSourceElement != null) {
            backEndSource = backEndSourceElement.getText();
            if (backEndSource.equalsIgnoreCase(STRING_HUBS)) {
                isStringHub = true;
            } else if (backEndSource.equalsIgnoreCase(RANDOM_GENERATOR)) {
                spInputSourceManager = new InputSourceGeneratorMng(new InputSource[]{inputSources[1]});
            } else if (frontEndSource.equalsIgnoreCase(FILE_READER)) {
                for (int i = 0; i < inputSources.length; i++) {
                    if (inputSources[i].getSourceID() < 3000) {
                        spInputSourceManager = new FileInputSourceMng(new InputSource[]{inputSources[i]});
                        break;
                    }
                }
            } else {
                throw new RuntimeException("invalid value for source element");
            }
        }

        // add channels to input engines
        if (gtInputSourceManager != null) {
            addInputChannels(ebComp, DAQConnector.TYPE_GLOBAL_TRIGGER, gtInputSourceManager);
        } else if (isGlobalTrigger) {
            addInputChannel(ebComp, DAQConnector.TYPE_GLOBAL_TRIGGER, globalTriggerOutputSourceChannel);
            log.info("Connecting EventBuilder -> GlobalTrigger.....");
        } else {
            throw new UnsupportedOperationException("Unknown configuration");
        }

        if (spInputSourceManager != null) {
            addInputChannels(ebComp, DAQConnector.TYPE_READOUT_DATA, spInputSourceManager);
        } else {
            if (isStringHub) {
                for (int i = 0; i < ebOutputSourceChannels.size(); i++) {
                    Pipe.SourceChannel ebOutputSourceChannel = (Pipe.SourceChannel) ebOutputSourceChannels.get(i);
                    addInputChannel(ebComp, DAQConnector.TYPE_READOUT_DATA, ebOutputSourceChannel);
                }
            }

            if (!isStringHub) {
                throw new UnsupportedOperationException("Unknown configuration");
            }
        }

        // add channels to output engines
        String ebReqOutputDest = DISPOSER_OUTPUT_DEST;
        Element reqDestElement = ebElement.element(EB_REQ_OUTPUT_ENG);
        OutputDestination[] reqDests = null;
        boolean isReqSPOutput = false;
        if (reqDestElement != null) {
            ebReqOutputDest = reqDestElement.getText();
            if (ebReqOutputDest.equalsIgnoreCase(STRING_HUBS)) {
                isReqSPOutput = true;
            } else if (ebReqOutputDest.equalsIgnoreCase(DISPOSER_OUTPUT_DEST)) {
                Element outputElement = ebElement.element(EB_REQ_OUTPUT_DEST);
                try {
                    reqDests = OutputDestinationXMLParser.parseDisposerOutputDestination(outputElement.getText());
                } catch (Exception ioe) {
                    throw new RuntimeException(ioe);
                }
            } else if (ebReqOutputDest.equalsIgnoreCase(FILE_WRITER_CHANNEL)) {
                Element outputElement = ebElement.element(EB_REQ_OUTPUT_DEST);
                try {
                    reqDests = OutputDestinationXMLParser.parseFileOutput(outputElement.getText());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException("invalid value for dest element");
            }
        }

        String ebFlushOutputDest = DISPOSER_OUTPUT_DEST;
        Element flushDestElement = ebElement.element(EB_FLUSH_OUTPUT_ENG);
        OutputDestination[] flushDests = null;
        boolean isFlushSPOutput = false;
        if (flushDestElement != null) {
            ebFlushOutputDest = flushDestElement.getText();
            if (ebFlushOutputDest.equalsIgnoreCase(STRING_HUBS)) {
                isFlushSPOutput = true;
            } else if (ebFlushOutputDest.equalsIgnoreCase(DISPOSER_OUTPUT_DEST)) {
                Element outputElement = ebElement.element(EB_FLUSH_OUTPUT_DEST);
                try {
                    flushDests = OutputDestinationXMLParser.parseDisposerOutputDestination(outputElement.getText());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (ebFlushOutputDest.equalsIgnoreCase(FILE_WRITER_CHANNEL)) {
                Element outputElement = ebElement.element(EB_FLUSH_OUTPUT_DEST);
                try {
                    flushDests = OutputDestinationXMLParser.parseFileOutput(outputElement.getText());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException("invalid value for dest element");
            }
        }
        List allDests = new ArrayList();
        if (reqDests != null) {
            addOutputChannels(ebComp, DAQConnector.TYPE_READOUT_REQUEST, reqDests);
            for (int i = 0; i < reqDests.length; i++) {
                allDests.add(reqDests[i]);
            }
        } else {
            if (isReqSPOutput) {
                for (int i = 0; i < ebReqOutputToSPSinkChannels.size(); i++) {
                    Pipe.SinkChannel sinkChannel = (Pipe.SinkChannel) ebReqOutputToSPSinkChannels.get(i);
                    addOutputChannel(ebComp, DAQConnector.TYPE_READOUT_REQUEST, sinkChannel);
                }
            }
            if (!isReqSPOutput) {
                throw new UnsupportedOperationException("Unknown configuration");
            }
        }

        if (flushDests != null) {
            addOutputChannels(ebComp, DAQConnector.TYPE_GENERIC_CACHE, flushDests);
            for (int i = 0; i < flushDests.length; i++) {
                allDests.add(flushDests[i]);
            }
        } else {
            if (isFlushSPOutput) {
                for (int i = 0; i < ebFlushOutputToSPSinkChannels.size(); i++) {
                    Pipe.SinkChannel sinkChannel = (Pipe.SinkChannel) ebFlushOutputToSPSinkChannels.get(i);
                    addOutputChannel(ebComp, DAQConnector.TYPE_GENERIC_CACHE, sinkChannel);
                }
            }
            if (!isFlushSPOutput) {
                throw new UnsupportedOperationException("Unknown configuration");
            }
        }

        DAQTestComponent ebCompTest = new DAQTestComponent(ebComp);
        if (gtInputSourceManager != null) {
            ebCompTest.addInputSourceManager(gtInputSourceManager);
        }
        if (spInputSourceManager != null) {
            ebCompTest.addInputSourceManager(spInputSourceManager);
        }
        if (allDests.size() > 0) {
            ebCompTest.setOutputDestination((OutputDestination[])
                    allDests.toArray(new OutputDestination[allDests.size()]));
        }
        daqComponents.add(ebCompTest);
    }

    // add inputChannels
    private void addInputChannels(DAQComponent daqComponent,
                                  String type,
                                  InputSourceManager inputSourceManager) throws DAQCompException {

        PayloadInputEngine inputEngine = daqComponent.getInputEngine(type);
        InputSource[] inputSources = inputSourceManager.getInputSources();
        for (int i = 0; i < inputSources.length; i++) {
            inputEngine.addDataChannel(((ReadableByteChannel) inputSources[i].getSourceChannel()),
                    daqComponent.getByteBufferCache(type));
        }
    }

    private void addInputChannel(DAQComponent daqComponent, String type, Pipe.SourceChannel sourceChannel)
            throws DAQCompException {
        PayloadInputEngine inputEngine = daqComponent.getInputEngine(type);
        inputEngine.addDataChannel(sourceChannel, daqComponent.getByteBufferCache(type));
    }

    private void addOutputChannels(DAQComponent daqComponent,
                                   String type,
                                   OutputDestination[] dests) throws DAQCompException {

        PayloadOutputEngine outputEngine = daqComponent.getOutputEngine(type);
        for (int i = 0; i < dests.length; i++) {
            outputEngine.addDataChannel(((WritableByteChannel) dests[i].getSinkChannel()),
                    daqComponent.getByteBufferCache(type));
        }
    }

    private void addOutputChannels(DAQComponent daqComponent,
                                   String type,
                                   OutputDestination[] dests,
                                   ISourceID sourceID) {

        PayloadDestinationOutputEngine outputEngine =
                (PayloadDestinationOutputEngine) daqComponent.getOutputEngine(type);
        for (int i = 0; i < dests.length; i++) {
            outputEngine.addDataChannel(((WritableByteChannel) dests[i].getSinkChannel()), sourceID);
        }
    }

    private void addOutputChannel(DAQComponent daqComponent, String type, Pipe.SinkChannel sinkChannel)
            throws DAQCompException {
        PayloadOutputEngine outputEngine = daqComponent.getOutputEngine(type);
        outputEngine.addDataChannel(sinkChannel, daqComponent.getByteBufferCache(type));
    }

    private void addOutputChannel(DAQComponent daqComponent, String type, Pipe.SinkChannel sinkChannel,
                                  ISourceID sourceID) {
        PayloadDestinationOutputEngine outputEngine =
                (PayloadDestinationOutputEngine) daqComponent.getOutputEngine(type);
        outputEngine.addDataChannel(sinkChannel, sourceID);
    }


    // parse TriggerBuilder and SplicedAnalysis
    private void parseTriggerBuilder(Element componentElement,
                                     ITriggerManager splicedAnalysis) throws Exception {
        Element activeTriggersElement = componentElement.element(TRIGGER_CONFIG);
        List triggerList = null;
        if (activeTriggersElement != null) {
            URL url = getClass().getResource("/" + activeTriggersElement.getText());
            triggerList = TriggerBuilder.buildTriggers(url.openStream());
        }

        Iterator triggerIter = triggerList.iterator();
        while (triggerIter.hasNext()) {
            ITriggerControl trigger = (ITriggerControl) triggerIter.next();
            trigger.setTriggerHandler(splicedAnalysis);
            splicedAnalysis.addTrigger(trigger);
        }
    }

    public DAQTestComponent[] getDAQComponents() {
        return (DAQTestComponent[]) daqComponents.toArray(new DAQTestComponent[daqComponents.size()]);
    }

    class TestFrameObserver implements DAQComponentObserver {

        private boolean sinkStopNotificationCalled;
        private boolean sinkErrorNotificationCalled;
        private boolean sourceStopNotificationCalled;
        private boolean sourceErrorNotificationCalled;

        public synchronized void update(Object object, String notificationID) {
            if (object instanceof NormalState) {
                NormalState state = (NormalState) object;
                if (state == NormalState.STOPPED) {
                    if (notificationID.equals(DAQCmdInterface.SINK)) {
                        sinkStopNotificationCalled = true;
                    } else if (notificationID.equals(DAQCmdInterface.SOURCE)) {
                        sourceStopNotificationCalled = true;
                    }
                }
            } else if (object instanceof ErrorState) {
                ErrorState state = (ErrorState) object;
                if (state == ErrorState.UNKNOWN_ERROR) {
                    if (notificationID.equals(DAQCmdInterface.SINK)) {
                        sinkErrorNotificationCalled = true;
                    } else if (notificationID.equals(DAQCmdInterface.SOURCE)) {
                        sourceErrorNotificationCalled = true;
                    }
                }
            }
        }
    }
}
