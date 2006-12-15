/**
 * DAQTestFrame
 * Date: Oct 18, 2005 9:04:51 AM
 * 
 * (c) 2005 IceCube Collaboration
 */
package icecube.daq.testFrame;

import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.common.DAQComponentInputProcess;
import icecube.daq.splicer.Splicer;
import icecube.daq.testUtil.OutputDestination;
import icecube.daq.testUtil.InputSourceManager;
import icecube.icebucket.logging.LoggingConsumer;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This is the main class to test one or more icecube components
 *
 * @author artur
 * @version $Id: DAQTestFrame.java,v 1.9 2006/08/24 21:50:25 artur Exp $
 */
public class DAQTestFrame {

    private DAQTestComponent[] components;

    private static Log log = LogFactory.getLog(DAQTestFrame.class);

    public DAQTestFrame(String xmlConfigFile) throws Exception {
        log.info("Creating and Configuring DAQTestFrame based on " + xmlConfigFile);
        TestFrameXMLParser testFrameParser = new TestFrameXMLParser(xmlConfigFile);

        testFrameParser.parse();

        components = testFrameParser.getDAQComponents();
    }

    public void start() throws Exception {
        // get all components and start each of them
        for (int i = components.length -1; i >= 0; i--) {
            DAQTestComponent comp = components[i];
            comp.startProcessing();
        }
    }

    public void stop() throws Exception {
        // get all components and start each of them
        for (int i = 0; i < components.length; i++) {
            DAQTestComponent comp = components[i];
            comp.stop();
        }
    }

    public void sendLastAndStop() throws Exception {
        // get all components and start each of them
        for (int i = 0; i < components.length; i++) {
            DAQTestComponent comp = components[i];
            if (comp.getName().startsWith(TestFrameConstants.STRING_HUB)){
                //comp.sendLastAndStop();
                break;
            }
        }
    }

    public void waitUntilAllStopped() throws Exception {
        List list = new ArrayList();
        for (int i = 0; i < components.length; i++) {
            list.add(components[i]);
        }
        int start = list.size();
        while (true) {
            for (int i = 0; i < list.size(); i++) {
                DAQTestComponent comp = (DAQTestComponent)list.get(i);
                if (!comp.isRunning()) {
                    list.remove(comp);
                }
            }
            if (list.size() == 0) {
                break;
            } 
            
            Thread.sleep(200);
        }
        Thread.sleep(1000);
        for (int i = 0; i < components.length; i++) {
            DAQTestComponent comp = components[i];
            comp.printBufferCacheReport();
        }
    }

    public static void main(String[] args) {
        try {
            // default duration of the running time
            int duration = 600;
            if (args != null && args.length > 0){
                duration = Integer.parseInt(args[0]);
            }

            LoggingConsumer.installDefault();
            String configFile = "testframe-config.xml";

            DAQTestFrame testFrame = new DAQTestFrame(configFile);
            testFrame.start();

            //Thread.sleep(1000 * duration);

            //testFrame.sendLastAndStop();

            testFrame.waitUntilAllStopped();
            System.out.println("Testing....DONE");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
