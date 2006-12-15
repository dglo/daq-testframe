/**
 * DAQTestComponent
 * Date: Oct 18, 2005 10:42:01 AM
 * 
 * (c) 2005 IceCube Collaboration
 */
package icecube.daq.testFrame;

import icecube.daq.splicer.Splicer;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ByteBufferCache;
import icecube.daq.testUtil.InputSourceManager;
import icecube.daq.testUtil.OutputDestination;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.component.DAQCompException;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * A representation of a component i.e. iniceTrigger, globalTrigger, eventBuilder, etc.
 *
 * @author artur
 * @version $Id: DAQTestComponent.java,v 1.15 2006/06/28 19:42:09 artur Exp $
 */
public class DAQTestComponent implements TestFrameConstants {

    private static final Log log = LogFactory.getLog(DAQTestComponent.class);

    private List inputSourceManagers;
    private OutputDestination[] dests;
    private boolean isRunning;
    private DAQComponent daqComponent;

    public DAQTestComponent(DAQComponent daqComponent) {

        if (!daqComponent.getName().equalsIgnoreCase(SECONDARY_BUILDERS) &&
                !daqComponent.getName().equalsIgnoreCase(EVENT_BUILDER) &&
                !daqComponent.getName().equalsIgnoreCase(INICE_TRIGGER)) {
            throw new IllegalArgumentException(daqComponent.getName() +
                    " is not a valid component name.");
        }

        if (daqComponent == null) {
            throw new IllegalArgumentException("DAQComponent is null");
        }
        this.daqComponent = daqComponent;

        inputSourceManagers = new ArrayList();
    }

    public String getName() {
        return daqComponent.getName();
    }

    public void setRunNumber(int runNumber){
        daqComponent.setRunNumber(runNumber);
    }

    public void addInputSourceManager(InputSourceManager inputSourceManager) {
        inputSourceManagers.add(inputSourceManager);
    }

    public InputSourceManager[] getInputSourceManager() {
        return (InputSourceManager[]) inputSourceManagers.toArray(new InputSourceManager[inputSourceManagers.size()]);
    }

    public void setOutputDestination(OutputDestination[] dests) {
        this.dests = dests;
    }

    public OutputDestination[] getOutputDestination() {
        return dests;
    }

    public boolean isRunning() {

        if (isInputManagerRunning() || daqComponent.isRunning() ||
                 isOutputManagerRunning()) {
            isRunning = true;
        } else {
            isRunning = false;
        }

        return isRunning;
    }

    public void startProcessing() throws Exception {

        if (dests != null) {
            for (int y = 0; y < dests.length; y++) {
                OutputDestination outputDest = dests[y];
                outputDest.startProcessing();
            }
        }
        daqComponent.connect();
        daqComponent.configure();
        daqComponent.start();
        daqComponent.startEngines();
        startInputMngProcessing();

        isRunning = true;
    }

    public void stop() throws Exception {
        stopInputMngProcessing();
    }
    /*
    public void sendLastAndStop() throws Exception{
        if (name.startsWith(STRING_PROC)){
            for (int i = 0; i < inputSourceManagers.size(); i++) {
                InputSourceManager ism = (InputSourceSimulatorMng) inputSourceManagers.get(i);
                ism.stopProcessing();
            }
        }
    }
    */
    public void printBufferCacheReport(){
        List bufferCaches = new ArrayList();
        IByteBufferCache bufferCache = null;
        if (daqComponent.getName().equalsIgnoreCase(SECONDARY_BUILDERS)) {
            try {
                bufferCache = daqComponent.getByteBufferCache(DAQConnector.TYPE_TCAL_DATA);
            }catch(DAQCompException e){
                log.info(e);
            }
            if (bufferCache != null){
                bufferCaches.add(bufferCache);
            }
            try {
                bufferCache = daqComponent.getByteBufferCache(DAQConnector.TYPE_SN_DATA);
            } catch(DAQCompException e){
                log.info(e);
            }
            if (bufferCache != null){
                bufferCaches.add(bufferCache);
            }
            try {
                bufferCache = daqComponent.getByteBufferCache(DAQConnector.TYPE_MONI_DATA);
            } catch(DAQCompException e){
                log.info(e);
            }
            if (bufferCache != null){
                bufferCaches.add(bufferCache);
            }
        }
        for (int i = 0; i < bufferCaches.size(); i++){
            System.out.println("======== " + daqComponent.getName() + " ======== ");
            System.out.println((ByteBufferCache)bufferCaches.get(i));
            System.out.println("=============================== ");
        }
        if (daqComponent.getName().equalsIgnoreCase(INICE_TRIGGER)) {
            try {
                bufferCache = daqComponent.getByteBufferCache("");
            }catch(DAQCompException e){
                log.info(e);
            }
            if (bufferCache != null){
                bufferCaches.add(bufferCache);
            }
        }
        for (int i = 0; i < bufferCaches.size(); i++){
            System.out.println("======== " + daqComponent.getName() + " ======== ");
            System.out.println((ByteBufferCache)bufferCaches.get(i));
            System.out.println("=============================== ");
        }
    }

    private boolean isInputManagerRunning() {
        for (int i = 0; i < inputSourceManagers.size(); i++) {
            InputSourceManager ism = (InputSourceManager) inputSourceManagers.get(i);
            if (ism.isRunning()) {
                return true;
            }
        }
        return false;
    }

    private boolean isOutputManagerRunning() {
        if (dests == null) {
            return false;
        }
        for (int i = 0; i < dests.length; i++) {
            if (dests[i].isRunning()) {
                return true;
            }
        }
        return false;
    }

    private void startInputMngProcessing() throws IOException {
        
        for (int i = 0; i < inputSourceManagers.size(); i++) {
            InputSourceManager ism = (InputSourceManager) inputSourceManagers.get(i);
            ism.startProcessing();
        }
    }

    private void stopInputMngProcessing() throws IOException {
        for (int i = 0; i < inputSourceManagers.size(); i++) {
            InputSourceManager ism = (InputSourceManager) inputSourceManagers.get(i);
            ism.stopProcessing();
        }
    }
}
