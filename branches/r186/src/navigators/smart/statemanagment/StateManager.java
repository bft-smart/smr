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

package navigators.smart.statemanagment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.reconfiguration.ServerViewManager;
import navigators.smart.reconfiguration.views.View;
import navigators.smart.tom.core.DeliveryThread;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.leaderchange.LCManager;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMUtil;

/**
 * TODO: Don't know if this class will be used. For now, leave it here
 *
 *  Check if the changes for supporting dynamicity are correct
 * @author Joao Sousa
 */
public class StateManager {

    private StateLog log;
    private HashSet<SenderEid> senderEids = null;
    private HashSet<SenderState> senderStates = null;
    private HashSet<SenderView> senderViews = null;
    private HashSet<SenderRegency> senderRegencies = null;
    private HashSet<SenderLeader> senderLeaders = null;

    private ReentrantLock lockState = new ReentrantLock();
    private ReentrantLock lockTimer = new ReentrantLock();

    private Timer stateTimer = null;

    private int lastEid;
    private int waitingEid;
    private int replica;
    private byte[] state;

    private ServerViewManager SVManager;
    private TOMLayer tomLayer;
    private DeliveryThread dt;
    private LCManager lcManager;
    private ExecutionManager execManager;
    
    public StateManager(ServerViewManager manager, TOMLayer tomLayer, DeliveryThread dt, LCManager lcManager, ExecutionManager execManager) {

        //******* EDUARDO BEGIN **************//
        this.SVManager = manager;
        int k = this.SVManager.getStaticConf().getCheckpointPeriod();
        //******* EDUARDO END **************//

        this.tomLayer = tomLayer;
        this.dt = dt;
        this.lcManager = lcManager;
        this.execManager = execManager;

        this.log = new StateLog(k);
        senderEids = new HashSet<SenderEid>();
        senderStates = new HashSet<SenderState>();
        senderViews = new HashSet<SenderView>();
        senderRegencies = new HashSet<SenderRegency>();
        senderLeaders = new HashSet<SenderLeader>();

        this.replica = 0;

        if (replica == manager.getStaticConf().getProcessId()) changeReplica();
        this.state = null;
        this.lastEid = -1;
        this.waitingEid = -1;
    }
    
    public int getReplica() {
        return replica;
    }

    public void changeReplica() {

        //******* EDUARDO BEGIN **************//
        int pos = -1;
        do {
            //TODO: Check if still correct
            pos = this.SVManager.getCurrentViewPos(replica);
            replica = this.SVManager.getCurrentViewProcesses()[(pos + 1) % SVManager.getCurrentViewN()];

        //******* EDUARDO END **************//
        } while (replica == SVManager.getStaticConf().getProcessId());
    }

    public void setReplicaState(byte[] state) {
        this.state = state;
    }

    public byte[] getReplicaState() {
        return state;
    }

    public void addEID(int sender, int eid) {
        senderEids.add(new SenderEid(sender, eid));
    }

    public void emptyEIDs() {
        senderEids.clear();
    }

    public void emptyEIDs(int eid) {
        for (SenderEid m : senderEids)
            if (m.eid <= eid) senderEids.remove(m);
    }

    public boolean moreThanF_EIDs(int eid) {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderEid m : senderEids) {
            if (m.eid == eid && !replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        //******* EDUARDO BEGIN **************//
        return count > SVManager.getCurrentViewF();
        //******* EDUARDO END **************//
    }

    public void addRegency(int sender, int regency) {
        senderRegencies.add(new SenderRegency(sender, regency));
    }
    
    public void addLeader(int sender, int leader) {
        senderLeaders.add(new SenderLeader(sender, leader));
    }
    public void addView(int sender, View view) {
        senderViews.add(new SenderView(sender, view));
    }
    public void emptyRegencies() {
        senderRegencies.clear();
    }

    public void emptyRegencies(int regency) {
        for (SenderRegency m : senderRegencies)
            if (m.regency <= regency) senderRegencies.remove(m);
    }
    
    public boolean moreThan2F_Regencies(int regency) {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderRegency m : senderRegencies) {
            if (m.regency == regency && !replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        //******* EDUARDO BEGIN **************//
        return count > SVManager.getQuorum2F();
        //******* EDUARDO END **************//
    }
    
    public boolean moreThan2F_Leaders(int leader) {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderLeader m : senderLeaders) {
            if (m.leader == leader && !replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        //******* EDUARDO BEGIN **************//
        return count > SVManager.getQuorum2F();
        //******* EDUARDO END **************//
    }

    public boolean moreThan2F_Views(View view) {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderView m : senderViews) {
            if (m.view.equals(view) && !replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        //******* EDUARDO BEGIN **************//
        return count > SVManager.getQuorum2F();
        //******* EDUARDO END **************//
    }
    
    public void addState(int sender, TransferableState state) {
        senderStates.add(new SenderState(sender, state));
    }

    public void emptyStates() {
        senderStates.clear();
    }

    public int getWaiting() {
        return waitingEid;
    }

    public void setWaiting(int wait) {
        this.waitingEid = wait;
    }
    public void setLastEID(int eid) {
        lastEid = eid;
    }

    public int getLastEID() {
        return lastEid;
    }


    public boolean moreThanF_Replies() {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderState m : senderStates) {
            if (!replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        //******* EDUARDO BEGIN **************//
        return count > SVManager.getCurrentViewF();
        //******* EDUARDO END **************//
    }

    private TransferableState getValidHash() {

        SenderState[] st = new SenderState[senderStates.size()];
        senderStates.toArray(st);
        int count = 0;

        for (int i = 0; i < st.length; i++) {

            for (int j = i; j < st.length; j++) {

                if (st[i].state.equals(st[j].state) && st[j].state.hasState()) count++;
                //******* EDUARDO BEGIN **************//
                if (count > SVManager.getCurrentViewF()) return st[j].state;
                //******* EDUARDO END **************//
            }
        }

        return null;
    }

    public int getNumValidHashes() {

        SenderState[] st = new SenderState[senderStates.size()];
        senderStates.toArray(st);
        int count = 0;

        for (int i = 0; i < st.length; i++) {

            for (int j = i; j < st.length; j++) {

                if (st[i].state.equals(st[j].state) && st[j].state.hasState()) count++;
 
            }
        }

        return count;
    }

    public int getReplies() {
        return senderStates.size();
    }

    public StateLog getLog() {
        return log;
    }

    public void saveState(byte[] state, int lastEid, int decisionRound, int leader) {

        StateLog thisLog = getLog();

        lockState.lock();

        Logger.println("(TOMLayer.saveState) Saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        thisLog.newCheckpoint(state, tomLayer.computeHash(state));
        thisLog.setLastEid(-1);
        thisLog.setLastCheckpointEid(lastEid);
        thisLog.setLastCheckpointRound(decisionRound);
        thisLog.setLastCheckpointLeader(leader);

        lockState.unlock();

        Logger.println("(TOMLayer.saveState) Finished saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    public void saveBatch(byte[] batch, int lastEid, int decisionRound, int leader) {

        StateLog thisLog = getLog();

        lockState.lock();

        Logger.println("(TOMLayer.saveBatch) Saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        thisLog.addMessageBatch(batch, decisionRound, leader);
        thisLog.setLastEid(lastEid);

        lockState.unlock();

        Logger.println("(TOMLayer.saveBatch) Finished saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    public void analyzeState(int sender, int eid) {

            Logger.println("(TOMLayer.analyzeState) The state transfer protocol is enabled");

            if (getWaiting() == -1) {

                Logger.println("(TOMLayer.analyzeState) I'm not waiting for any state, so I will keep record of this message");
                addEID(sender, eid);

                if (getLastEID() < eid && moreThanF_EIDs(eid)) {

                    Logger.println("(TOMLayer.analyzeState) I have now more than " + SVManager.getCurrentViewF() + " messages for EID " + eid + " which are beyond EID " + getLastEID());
                    
                    setLastEID(eid);
                    setWaiting(eid - 1);
        
                    requestState();
                }
            }

        /************************* TESTE *************************
        System.out.println("[/TOMLayer.requestState]");
        /************************* TESTE *************************/
    }

    private void requestState() {
        if (tomLayer.requestsTimer != null) tomLayer.requestsTimer.clearAll();

        //stateManager.emptyReplicas(eid);// this causes an exception

        SMMessage smsg = new SMMessage(SVManager.getStaticConf().getProcessId(),
                getWaiting(), TOMUtil.SM_REQUEST, getReplica(), null, null, -1, -1);
        
        System.out.println("Requesting state:" + SVManager.getStaticConf().getProcessId() + ", " + getWaiting() + ", " + getReplica());
        int[] otherAcceptors = SVManager.getCurrentViewOtherAcceptors();
        System.out.print("CurrentViewOtherAcceptors(): ");
        for(int i = 0; i < otherAcceptors.length; i++)
        	System.out.print(otherAcceptors[i]);
        tomLayer.getCommunication().send(otherAcceptors, smsg);
        

        Logger.println("(TOMLayer.requestState) I just sent a request to the other replicas for the state up to EID " + getWaiting());

        TimerTask stateTask =  new TimerTask() {
            public void run() {


                int[] myself = new int[1];
                myself[0] = SVManager.getStaticConf().getProcessId();
                                
                tomLayer.getCommunication().send(myself, new SMMessage(-1, getWaiting(), TOMUtil.TRIGGER_SM_LOCALLY, -1, null, null, -1, -1));

                
                
            }
        };

        stateTimer = new Timer("state timer");
        stateTimer.schedule(stateTask, 4500);

    }
    
    public void stateTimeout() {
        lockTimer.lock();
        
        Logger.println("(StateManager.stateTimeout) Timeout for the replica that was supposed to send the complete state. Changing desired replica.");
        System.out.println("Timeout no timer do estado!");


        if (stateTimer != null) stateTimer.cancel();
                
        //setWaiting(-1);
        changeReplica();
        emptyStates();
        setReplicaState(null);
        
        requestState();

        lockTimer.unlock();
    }
    
    public void SMRequestDeliver(SMMessage msg) {

        //******* EDUARDO BEGIN **************//
        if (SVManager.getStaticConf().isStateTransferEnabled()) {
        //******* EDUARDO END **************//

            Logger.println("(TOMLayer.SMRequestDeliver) The state transfer protocol is enabled");

            lockState.lock();

            Logger.println("(TOMLayer.SMRequestDeliver) I received a state request for EID " + msg.getEid() + " from replica " + msg.getSender());

            boolean sendState = msg.getReplica() == SVManager.getStaticConf().getProcessId();
            if (sendState) Logger.println("(TOMLayer.SMRequestDeliver) I should be the one sending the state");

            TransferableState thisState = getLog().getTransferableState(msg.getEid(), sendState);

            lockState.unlock();

            if (thisState == null) {
                Logger.println("(TOMLayer.SMRequestDeliver) I don't have the state requested :-(");

              thisState = new TransferableState();
            }

            int[] targets = { msg.getSender() };
            SMMessage smsg = new SMMessage(SVManager.getStaticConf().getProcessId(),
                    msg.getEid(), TOMUtil.SM_REPLY, -1, thisState, SVManager.getCurrentView(), lcManager.getLastReg(), tomLayer.lm.getCurrentLeader());

            // malicious code, to force the replica not to send the state
            //if (reconfManager.getStaticConf().getProcessId() != 0 || !sendState)
            tomLayer.getCommunication().send(targets, smsg);

            Logger.println("(TOMLayer.SMRequestDeliver) I sent the state for checkpoint " + thisState.getLastCheckpointEid() + " with batches until EID " + thisState.getLastEid());

        }
    }

    public void SMReplyDeliver(SMMessage msg) {

        //******* EDUARDO BEGIN **************//

        lockTimer.lock();
        if (SVManager.getStaticConf().isStateTransferEnabled()) {
        //******* EDUARDO END **************//

            Logger.println("(TOMLayer.SMReplyDeliver) The state transfer protocol is enabled");
            Logger.println("(TOMLayer.SMReplyDeliver) I received a state reply for EID " + msg.getEid() + " from replica " + msg.getSender());

            if (getWaiting() != -1 && msg.getEid() == getWaiting()) {

                int currentRegency = -1;
                int currentLeader = -1;
                View currentView = null;
                addRegency(msg.getSender(), msg.getRegency());
                addLeader(msg.getSender(), msg.getLeader());
                addView(msg.getSender(), msg.getView());
                if (moreThan2F_Regencies(msg.getRegency())) currentRegency = msg.getRegency();
                if (moreThan2F_Leaders(msg.getLeader())) currentLeader = msg.getLeader();
                if (moreThan2F_Views(msg.getView())) {
                    currentView = msg.getView();
                    if (currentView.isMember(SVManager.getStaticConf().getProcessId())) {
                        System.out.println("Not a member anymore!");
                    }
                }
                
                Logger.println("(TOMLayer.SMReplyDeliver) The reply is for the EID that I want!");

                if (msg.getSender() == getReplica() && msg.getState().getState() != null) {
                    Logger.println("(TOMLayer.SMReplyDeliver) I received the state, from the replica that I was expecting");
                    setReplicaState(msg.getState().getState());
                    if (stateTimer != null) stateTimer.cancel();
                }

                addState(msg.getSender(),msg.getState());

                if (moreThanF_Replies()) {

                    Logger.println("(TOMLayer.SMReplyDeliver) I have at least " + SVManager.getCurrentViewF() + " replies!");

                    TransferableState recvState = getValidHash();

                    int haveState = 0;
                    if (getReplicaState() != null) {
                        byte[] hash = null;
                        hash = tomLayer.computeHash(getReplicaState());
                        if (recvState != null) {
                            if (Arrays.equals(hash, recvState.getStateHash())) haveState = 1;
                            else if (getNumValidHashes() > SVManager.getCurrentViewF()) haveState = -1;

                        }
                    }

                    if (recvState != null && haveState == 1 && currentRegency > -1 &&
                            currentLeader > -1 && currentView != null) {
                        
                        Logger.println("(TOMLayer.SMReplyDeliver) The state of those replies is good!");
                        
                        lcManager.setLastReg(currentRegency);
                        lcManager.setNextReg(currentRegency);
                        lcManager.setNewLeader(currentLeader);
                        
                        tomLayer.lm.setNewReg(currentRegency);
                        tomLayer.lm.setNewLeader(currentLeader);
                        
                        recvState.setState(getReplicaState());

                        lockState.lock();

                        getLog().update(recvState);

                        lockState.unlock();

                        dt.deliverLock();

                        //ot.OutOfContextLock();

                        setWaiting(-1);

                        dt.update(recvState);
                        
                        //Deal with stopped messages that may come from synchronization phase
                        if (execManager.stopped()) {
                        
                            Queue<PaxosMessage> stoppedMsgs = execManager.getStoppedMsgs();
                        
                            for (PaxosMessage stopped : stoppedMsgs) {
                                
                                if (stopped.getNumber() > msg.getEid())
                                    execManager.addOutOfContextMessage(stopped);
                            }
                            
                            execManager.clearStopped();
                            execManager.restart();
                        }
                        
                        tomLayer.processOutOfContext();
                        
                        if (SVManager.getCurrentViewId() != currentView.getId()) {
                            System.out.println("Installing current view!");
                            SVManager.reconfigureTo(currentView);
                        }
                        
                        dt.canDeliver();

                        //ot.OutOfContextUnlock();
                        dt.deliverUnlock();

                        emptyStates();
                        setReplicaState(null);

                        System.out.println("I updated the state!");

                        tomLayer.requestsTimer.Enabled(true);
                        tomLayer.requestsTimer.startTimer();
                        
                    //******* EDUARDO BEGIN **************//
                    } else if (recvState == null && (SVManager.getCurrentViewN() / 2) < getReplies()) {
                    //******* EDUARDO END **************//

                        Logger.println("(TOMLayer.SMReplyDeliver) I have more than " +
                                (SVManager.getCurrentViewN() / 2) + " messages that are no good!");

                        setWaiting(-1);
                        emptyStates();
                        setReplicaState(null);
                        //requestState();

                        if (stateTimer != null) stateTimer.cancel();
                    } else if (haveState == -1) {

                        Logger.println("(TOMLayer.SMReplyDeliver) The replica from which I expected the state, sent one which doesn't match the hash of the others, or it never sent it at all");

                        //setWaiting(-1);
                        changeReplica();
                        emptyStates();
                        setReplicaState(null);
                        requestState();

                        if (stateTimer != null) stateTimer.cancel();
                    }
                }
            }
        }
        lockTimer.unlock();
    }

    private class SenderRegency {

        private int sender;
        private int regency;

        SenderRegency(int sender, int regency) {
            this.sender = sender;
            this.regency = regency;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SenderRegency) {
                SenderRegency m = (SenderRegency) obj;
                return (m.regency == this.regency && m.sender == this.sender);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + this.sender;
            hash = hash * 31 + this.regency;
            return hash;
        }
    }
    
    private class SenderLeader {

        private int sender;
        private int leader;

        SenderLeader(int sender, int leader) {
            this.sender = sender;
            this.leader = leader;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SenderLeader) {
                SenderLeader m = (SenderLeader) obj;
                return (m.leader == this.leader && m.sender == this.sender);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + this.sender;
            hash = hash * 31 + this.leader;
            return hash;
        }
    }

    private class SenderEid {

        private int sender;
        private int eid;

        SenderEid(int sender, int eid) {
            this.sender = sender;
            this.eid = eid;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SenderEid) {
                SenderEid m = (SenderEid) obj;
                return (m.eid == this.eid && m.sender == this.sender);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + this.sender;
            hash = hash * 31 + this.eid;
            return hash;
        }
    }
    
    private class SenderState {

        private int sender;
        private TransferableState state;

        SenderState(int sender, TransferableState state) {
            this.sender = sender;
            this.state = state;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SenderState) {
                SenderState m = (SenderState) obj;
                return (this.state.equals(m.state) && m.sender == this.sender);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + this.sender;
            hash = hash * 31 + this.state.hashCode();
            return hash;
        }
    }

    private class SenderView {

        private int sender;
        private View view;

        SenderView(int sender, View view) {
            this.sender = sender;
            this.view = view;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SenderView) {
                SenderView m = (SenderView) obj;
                return (this.view.equals(m.view) && m.sender == this.sender);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + this.sender;
            hash = hash * 31 + this.view.hashCode();
            return hash;
        }
    }
}
