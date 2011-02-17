/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.reconfiguration.util;

import navigators.smart.tom.util.Logger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.StringTokenizer;

public class TOMConfiguration extends Configuration {

    protected int n;
    protected int f;
    protected int requestTimeout;
    protected int freezeInitialTimeout;
    protected int tomPeriod;
    protected int paxosHighMark;
    protected int revivalHighMark;
    protected int replyVerificationTime;
    protected int maxBatchSize;
    protected int numberOfNonces;
    protected int inQueueSize;
    protected int outQueueSize;
    protected boolean decideMessagesEnabled;
    protected boolean verifyTimestamps;
    protected boolean useSenderThread;
    protected RSAKeyLoader rsaLoader;
    protected Logger log;
    protected int clientServerCommSystem;
    private int maxMessageSize;
    private int debug;
    private int numNIOThreads;   
    private int commBuffering;
    private int useMACs;
    private int useSignatures;
    private boolean  stateTransferEnabled;
    private int checkpoint_period;
    private int useControlFlow;
    
    
    private int[] initialView;
    private int ttpId;
    
    /** Creates a new instance of TOMConfiguration */
    public TOMConfiguration(int processId) {
        super(processId);
    }

    /** Creates a new instance of TOMConfiguration */
    public TOMConfiguration(int processId, String configHome) {
        super(processId, configHome);
    }

    /** Creates a new instance of TOMConfiguration */
    public TOMConfiguration(int processId, String configHome, String hostsFileName) {
        super(processId, configHome, hostsFileName);
    }

    @Override
    protected void init() {
        super.init();
        try {
            n = Integer.parseInt(configs.remove("system.servers.num").toString());
            String s = (String) configs.remove("system.servers.f");
            if (s == null) {
                f = (int) Math.ceil((n - 1) / 3);
            } else {
                f = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.paxos.freeze.timeout");
            if (s == null) {
                freezeInitialTimeout = n * 10;
            } else {
                freezeInitialTimeout = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.paxos.decideMessages");
            if (s == null) {
                decideMessagesEnabled = false;
            } else {
                decideMessagesEnabled = Boolean.parseBoolean(s);
            }

            s = (String) configs.remove("system.totalordermulticast.timeout");
            if (s == null) {
                requestTimeout = freezeInitialTimeout / 2;
            } else {
                requestTimeout = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.totalordermulticast.period");
            if (s == null) {
                tomPeriod = n * 5;
            } else {
                tomPeriod = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.totalordermulticast.highMark");
            if (s == null) {
                paxosHighMark = 10000;
            } else {
                paxosHighMark = Integer.parseInt(s);
                if (paxosHighMark < 10) {
                    paxosHighMark = 10;
                }
            }

            s = (String) configs.remove("system.totalordermulticast.revival_highMark");
            if (s == null) {
                revivalHighMark = 10;
            } else {
                revivalHighMark = Integer.parseInt(s);
                if (revivalHighMark < 1) {
                    revivalHighMark = 1;
                }
            }

            s = (String) configs.remove("system.totalordermulticast.maxbatchsize");
            if (s == null) {
                maxBatchSize = 100;
            } else {
                maxBatchSize = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.totalordermulticast.maxMessageSize");
            if (s == null) {
                maxMessageSize = 200; //the same as used in upright
            } else {
                maxMessageSize = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.debug");
            if (s == null) {
                Logger.debug = false;
            } else {
                debug = Integer.parseInt(s);
                if (debug==0)
                    Logger.debug = false;
                else
                    Logger.debug = true;
            }

            s = (String) configs.remove("system.totalordermulticast.replayVerificationTime");
            if (s == null) {
                replyVerificationTime = 0;
            } else {
                replyVerificationTime = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.totalordermulticast.nonces");
            if (s == null) {
                numberOfNonces = 0;
            } else {
                numberOfNonces = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.totalordermulticast.verifyTimestamps");
            if (s == null) {
                verifyTimestamps = false;
            } else {
                verifyTimestamps = Boolean.parseBoolean(s);
            }

            s = (String) configs.remove("system.communication.useSenderThread");
            if (s == null) {
                useSenderThread = false;
            } else {
                useSenderThread = Boolean.parseBoolean(s);
            }

            s = (String) configs.remove("system.communication.clientServerCommSystem");
            if (s == null) {
                clientServerCommSystem = 1;
            } else {
                clientServerCommSystem = Integer.parseInt(s);
            }


            s = (String) configs.remove("system.communication.numNIOThreads");
            if (s == null) {
                numNIOThreads = 2;
            } else {
                numNIOThreads = Integer.parseInt(s);
            }

             s = (String) configs.remove("system.communication.commBuffering");
            if (s == null) {
                commBuffering = 0;
            } else {
                commBuffering = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.communication.useMACs");
            if (s == null) {
                useMACs = 0;
            } else {
                useMACs = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.communication.useSignatures");
            if (s == null) {
                useSignatures = 0;
            } else {
                useSignatures = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.totalordermulticast.state_transfer");
            if (s == null) {
                stateTransferEnabled = false;
            } else {
                stateTransferEnabled = Boolean.parseBoolean(s);
            }

            s = (String) configs.remove("system.totalordermulticast.checkpoint_period");
            if (s == null) {
                checkpoint_period = 1;
            } else {
                checkpoint_period = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.communication.useControlFlow");
            if (s == null) {
                useControlFlow = 0;
            } else {
                useControlFlow = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.initial.view");
            if (s == null) {
                initialView = new int[n];
                for(int i=0; i<n; i++) {
                     initialView[i] = i;
                }
            } else {
                 StringTokenizer str = new StringTokenizer(s,",");
                 initialView = new int[str.countTokens()];
                 for(int i = 0; i < initialView.length; i++){
                     initialView[i] = Integer.parseInt(str.nextToken());
                 }
            }

            s = (String) configs.remove("system.ttp.id");
            if (s == null) {
                ttpId = -1;
            } else {
                ttpId = Integer.parseInt(s);
            }
            
            s = (String) configs.remove("system.communication.inQueueSize");
            if (s == null) {
                inQueueSize = 200;
            } else {

                inQueueSize = Integer.parseInt(s);
                if (inQueueSize < 1) {
                    inQueueSize = 200;
                }

            }

            s = (String) configs.remove("system.communication.outQueueSize");
            if (s == null) {
                outQueueSize = 200;
            } else {

                outQueueSize = Integer.parseInt(s);
                if (outQueueSize < 1) {
                    outQueueSize = 200;
                }

            }
            
            rsaLoader = new RSAKeyLoader(this, TOMConfiguration.configHome);

        } catch (Exception e) {
            System.err.println("Wrong system.config file format.");
            e.printStackTrace();
        }

    }

    public boolean isTheTTP(){
        return (this.getTTPId() == this.getProcessId());
    }
    
    public final int[] getInitialView(){
        return this.initialView;
    }

    public int getTTPId() {
        return ttpId;
    }
    
    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public int getReplyVerificationTime() {
        return replyVerificationTime;
    }

    public int getN() {
        return n;
    }

    public int getF() {
        return f;
    }

    public int getTOMPeriod() {
        return tomPeriod;
    }

    public int getFreezeInitialTimeout() {
        return freezeInitialTimeout;
    }

    public int getPaxosHighMark() {
        return paxosHighMark;
    }

    public int getRevivalHighMark() {
        return revivalHighMark;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public boolean isDecideMessagesEnabled() {
        return decideMessagesEnabled;
    }

    public boolean isStateTransferEnabled() {
        return stateTransferEnabled;
    }

    public boolean canVerifyTimestamps() {
        return verifyTimestamps;
    }

    public int getInQueueSize() {
        return inQueueSize;
    }

    public int getOutQueueSize() {
        return outQueueSize;
    }

    public boolean isUseSenderThread() {
        return useSenderThread;
    }

    /**
     *
     * @return 0 (Netty), 1 (MINA)
     */
    public int clientServerCommSystem() {
        return clientServerCommSystem;
    }

     /**
     *     *
     */
    public int getNumberOfNIOThreads() {
        return numNIOThreads;
    }

    /**     * @return the numberOfNonces     */
    public int getNumberOfNonces() {
        return numberOfNonces;
    }

    /**
     * Number of requests from clients buffered by the client communication system before delivering to the TOM Layer
     */
    public int getCommBuffering() {
        return commBuffering;
    }

    /**
     * Indicates if signatures should be used (1) or not (0) to authenticate client requests
     */
    public int getUseSignatures() {
        return useSignatures;
    }

    /**
     * Indicates if MACs should be used (1) or not (0) to authenticate client-server and server-server messages
     */
    public int getUseMACs() {
        return useMACs;
    }

    /**
     * Indicates the checkpoint period used when fetching the state from the application
     */
    public int getCheckpoint_period() {
        return checkpoint_period;
    }

     /**
     * Indicates if a simple control flow mechanism should be used to avoid an overflow of client requests
     */
    public int getUseControlFlow() {
        return useControlFlow;
    }

   /* public PublicKey[] getRSAServersPublicKeys() {
        try {
            return rsaLoader.loadServersPublicKeys();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }*/

    public PublicKey getRSAPublicKey(int id) {
        try {
            return rsaLoader.loadPublicKey(id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

   /* public void increasePortNumber() {
        for (int i = 0; i < getN(); i++) {
            hosts.setPort(i, hosts.getPort(i) + 1);
        }

    }
*/
    public PrivateKey getRSAPrivateKey() {
        try {
            return rsaLoader.loadPrivateKey();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
