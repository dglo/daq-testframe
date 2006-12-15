#!/bin/sh

java -server -Xms76m -Xmx656m -cp lib/daq-testframe.jar icecube.daq.testFrame.DAQTestFrame $*
