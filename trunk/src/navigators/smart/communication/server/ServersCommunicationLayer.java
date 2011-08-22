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
package navigators.smart.communication.server;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.tom.ServiceReplica;
import navigators.smart.communication.SystemMessage;

/**
 *
 * @author alysson
 */
public class ServersCommunicationLayer extends Thread {

    private ReconfigurationManager manager;
    private LinkedBlockingQueue<SystemMessage> inQueue;
    private Hashtable<Integer, ServerConnection> connections = new Hashtable<Integer, ServerConnection>();
    private ServerSocket serverSocket;
    private int me;
    private boolean doWork = true;
    private Lock connectionsLock = new ReentrantLock();
    private ReentrantLock waitViewLock = new ReentrantLock();
    //private Condition canConnect = waitViewLock.newCondition();
    private List<PendingConnection> pendingConn = new LinkedList<PendingConnection>();
    private ServiceReplica replica;


    public ServersCommunicationLayer(ReconfigurationManager manager,
            LinkedBlockingQueue<SystemMessage> inQueue, ServiceReplica replica) throws Exception {

        //******* EDUARDO BEGIN **************//
        this.manager = manager;
        this.inQueue = inQueue;
        this.me = manager.getStaticConf().getProcessId();
        this.replica = replica;

        //Tenta se conectar caso seja um membro da visão inicial. Caso contrario, espera pelo processamento do join!
        if (manager.isInInitView()) {
            int[] initialV = manager.getCurrentViewAcceptors();
            for (int i = 0; i < initialV.length; i++) {
                if (initialV[i] != me) {
                    //connections.put(initialV[i], new ServerConnection(manager, null, initialV[i], inQueue));
                    getConnection(initialV[i]);
                }
            }
        }

        serverSocket = new ServerSocket(manager.getStaticConf().getServerToServerPort(
                manager.getStaticConf().getProcessId()));
        //******* EDUARDO END **************//

        serverSocket.setSoTimeout(10000);
        serverSocket.setReuseAddress(true);

        start();
    }


    //******* EDUARDO BEGIN **************//
    public void updateConnections() {
        connectionsLock.lock();

        if (this.manager.isInCurrentView()) {

            Iterator<Integer> it = this.connections.keySet().iterator();
            List<Integer> toRemove = new LinkedList<Integer>();
            while (it.hasNext()) {
                int rm = it.next();
                if (!this.manager.isCurrentViewMember(rm)) {
                    toRemove.add(rm);
                }
            }
            for (int i = 0; i < toRemove.size(); i++) {
                this.connections.remove(toRemove.get(i)).shutdown();
            }

            int[] newV = manager.getCurrentViewAcceptors();
            for (int i = 0; i < newV.length; i++) {
                if (newV[i] != me) {
                    getConnection(newV[i]);
                }
            }
        } else {

            Iterator<Integer> it = this.connections.keySet().iterator();
            while (it.hasNext()) {
                this.connections.get(it.next()).shutdown();
            }
        }

        connectionsLock.unlock();
    }

    private ServerConnection getConnection(int remoteId) {
        connectionsLock.lock();
        ServerConnection ret = this.connections.get(remoteId);
        if (ret == null) {
            ret = new ServerConnection(manager, null, remoteId, this.inQueue, this.replica);
            this.connections.put(remoteId, ret);
        }
        connectionsLock.unlock();
        return ret;
    }
    //******* EDUARDO END **************//


    public final void send(int[] targets, SystemMessage sm) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);

        try {
            new ObjectOutputStream(bOut).writeObject(sm);
        } catch (IOException ex) {
            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
        }

        byte[] data = bOut.toByteArray();

        for (int i : targets) {
            try {
                if (i == me) {
                    inQueue.put(sm);
                } else {
                    //System.out.println("Vai enviar msg para: "+i);
                    //******* EDUARDO BEGIN **************//
                    //connections[i].send(data);
                    getConnection(i).send(data);
                    //******* EDUARDO END **************//
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void shutdown() {
        doWork = false;

        //******* EDUARDO BEGIN **************//
        int[] activeServers = manager.getCurrentViewAcceptors();

        for (int i = 0; i < activeServers.length; i++) {
            //if (connections[i] != null) {
            //  connections[i].shutdown();
            //}
            if (me != activeServers[i]) {
                getConnection(activeServers[i]).shutdown();
            }
        }
        //******* EDUARDO END **************//
    }

    //******* EDUARDO BEGIN **************//
    public void joinViewReceived() {
        waitViewLock.lock();
        for (int i = 0; i < pendingConn.size(); i++) {
            PendingConnection pc = pendingConn.get(i);
            try {
                establishConnection(pc.s, pc.remoteId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        pendingConn.clear();

        waitViewLock.unlock();
    }
    //******* EDUARDO END **************//

    @Override
    public void run() {
        while (doWork) {
            try {

                //System.out.println("Esperando por servers conexoes");

                Socket newSocket = serverSocket.accept();

                ServersCommunicationLayer.setSocketOptions(newSocket);
                int remoteId = new DataInputStream(newSocket.getInputStream()).readInt();

                //******* EDUARDO BEGIN **************//
                if (!this.manager.isInInitView() &&
                     !this.manager.isInCurrentView() &&
                     (this.manager.getStaticConf().getTTPId() != remoteId)) {
                    waitViewLock.lock();
                    pendingConn.add(new PendingConnection(newSocket, remoteId));
                    waitViewLock.unlock();
                } else {
                    establishConnection(newSocket, remoteId);
                }
                //******* EDUARDO END **************//

            } catch (SocketTimeoutException ex) {
            //timeout on the accept... do nothing
            } catch (IOException ex) {
                Logger.getLogger(ServersCommunicationLayer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        try {
            serverSocket.close();
        } catch (IOException ex) {
            Logger.getLogger(ServersCommunicationLayer.class.getName()).log(Level.SEVERE, null, ex);
        }

        Logger.getLogger(ServersCommunicationLayer.class.getName()).log(Level.INFO, "Server communication layer stoped.");
    }

    //******* EDUARDO BEGIN **************//
    private void establishConnection(Socket newSocket, int remoteId) throws IOException {
        if ((this.manager.getStaticConf().getTTPId() == remoteId) || this.manager.isCurrentViewMember(remoteId)) {
            connectionsLock.lock();
            //System.out.println("Vai se conectar com: "+remoteId);
            if (this.connections.get(remoteId) == null) { //Isso nunca pode acontecer!!!
                //first time that this connection is being established
                //System.out.println("ISSO NUNCA ACONTECE....."+remoteId);
                this.connections.put(remoteId, new ServerConnection(manager, newSocket, remoteId, inQueue, replica));
            } else {
                //reconnection
                this.connections.get(remoteId).reconnect(newSocket);
            }
            connectionsLock.unlock();

        } else {
            //System.out.println("Vai fechar a conexão de: "+remoteId);
            newSocket.close();
        }
    }
    //******* EDUARDO END **************//

    public static void setSocketOptions(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
        } catch (SocketException ex) {
            Logger.getLogger(ServersCommunicationLayer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public String toString() {
        String str = "inQueue=" + inQueue.toString();

        int[] activeServers = manager.getCurrentViewAcceptors();

        for (int i = 0; i < activeServers.length; i++) {

            //for(int i=0; i<connections.length; i++) {
            // if(connections[i] != null) {
            if (me != activeServers[i]) {
                str += ", connections[" + activeServers[i] + "]: outQueue=" + getConnection(activeServers[i]).outQueue;
            }
        }

        return str;
    }


    //******* EDUARDO BEGIN: Entry da lista que guarda as conexoes pendentes, pois
    //um servidor apenas pode aceitar conexoes depois de conhecer a visao corrente, i.e.,
    //depois da receber a resposta do join **************//
    //Isso é para que um servidor nao aceite conexoes de todo mundo!
    public class PendingConnection {

        public Socket s;
        public int remoteId;

        public PendingConnection(Socket s, int remoteId) {
            this.s = s;
            this.remoteId = remoteId;
        }
    }

    //******* EDUARDO END **************//
}
