package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;


import activitystreamer.server.models.ClientModel;
import activitystreamer.server.models.ServerModel;
import activitystreamer.server.processors.ClientRequestProcessor;
import activitystreamer.server.processors.ServerRequestProcessor;
import activitystreamer.util.Helper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;

import static activitystreamer.util.Constants.ClientCommands;
import static activitystreamer.util.Constants.ServerCommands;
import static activitystreamer.util.Constants.Info;
import static activitystreamer.util.Constants.MsgAttribute;

public class ServerControl extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static ArrayList<Connection> connections;
    private static HashMap<String, ClientModel> connectedClients;
    private static HashMap<String, ServerModel> connectedServers;
    private static ClientRequestProcessor clientProcessor;
    private static ServerRequestProcessor serverProcessor;
    private static boolean term = false;
    private static Listener listener;
    private int conReplyCount = 0;
    private int clientLoad = 0;

    protected static ServerControl serverControl = null;

    public static ServerControl getInstance() {
        if (serverControl == null) {
            serverControl = new ServerControl();
        }
        return serverControl;
    }

    private ServerControl() {
        /*
         *  for demo purpose, use a simple fixed secret
         *  otherwise will use Settings.setSecret(Settings.nextSecret()) to generate a random secret
         */
        connections = new ArrayList<>();
        connectedClients = new HashMap<>();
        connectedServers = new HashMap<>();
        clientProcessor = ClientRequestProcessor.getClientRequestProcessor();
        serverProcessor = ServerRequestProcessor.getServerRequestProcessor();
        initiateConnection();
        // start a listener
        try {
            listener = new Listener();
        } catch (IOException e1) {
            log.fatal("failed to startup a listening thread: " + e1);
            System.exit(-1);
        }
        start();
    }

    public void initiateConnection() {
        // make a connection to another server if remote hostname is supplied
        if (Settings.getRemoteHostname() != null) {
            try {
                outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
            } catch (IOException e) {
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }
        }
    }

    /*
     *  A new incoming connection has been established, and a reference is returned to it
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        log.debug("incomming connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        connections.add(c);
        return c;
    }

    /*
     *  A new outgoing connection has been established, and a reference is returned to it
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        JSONObject auth = new JSONObject();
        auth.put(MsgAttribute.COMMAND, ServerCommands.AUTHENTICATE);
        auth.put(MsgAttribute.SECRET, Settings.getSecret());
        c.writeMsg(auth.toString());
        connections.add(c);
        return c;
    }

    /*
     *  Processing incoming messages from the connection.
     *  Return true if the connection should close.
     */
    @SuppressWarnings("unchecked")
    public synchronized boolean process(Connection con, String receivedMsg) {
        JSONObject output = null;
        boolean shouldClose = false;

        try {
            JSONObject receivedObj = Helper.JsonParser(receivedMsg);
            if (VerifyMessage(receivedObj)) {
                String command = receivedObj.get(MsgAttribute.COMMAND).toString();
                if (command.equals(ServerCommands.AUTHENTICATE) || command.equals(ServerCommands.SERVER_ANNOUNCE)) {
                    con.isServer = true;
                }
                if (con.isServer) {
                    String receivedCommand = receivedObj.get(MsgAttribute.COMMAND).toString();

                    switch (receivedCommand) {
                        case ServerCommands.AUTHENTICATE:
                            shouldClose = !receivedObj.get(MsgAttribute.SECRET).toString().equals(Settings.getSecret());
                            log.info("Authenticate failed: " + shouldClose);
                            break;
                        case ServerCommands.LOCK_REQUEST:
                            JSONObject replyMsg = serverProcessor.processLockRequest(receivedObj.get(MsgAttribute.USERNAME).toString(),
                                    receivedObj.get(MsgAttribute.PASSWORD).toString(), connectedClients);
                            con.writeMsg(replyMsg.toString());
                            break;
                        case ServerCommands.LOCK_ALLOWED:
                            log.info("LOCK_ALLOWED received");
                            conReplyCount--;
                            if (conReplyCount == 0) {
                                clientProcessor.processRegisterUser(receivedObj.get(MsgAttribute.USERNAME).toString(),
                                        receivedObj.get(MsgAttribute.PASSWORD).toString(),
                                        true, connectedClients);
                            }
                            break;
                        case ServerCommands.LOCK_DENIED:
                            log.info("LOCK_DENIED received");
                            clientProcessor.processRegisterUser(null, null, false, null);
                            break;
                        case ServerCommands.SERVER_ANNOUNCE:
                            // Will send AUTHENTICATE if announcement from first received id
                            serverProcessor.processServerAnnounce(receivedObj, connectedServers);
                            break;
                        case ServerCommands.ACTIVITY_BROADCAST:

                            serverProcessor.processServerAnnounce(receivedObj, connectedServers);
                            break;
                        default:
                            output = getJSONObject(ServerCommands.INVALID_MESSAGE);
                            shouldClose = true;
                    }
                } else {
                    String username = receivedObj.get(MsgAttribute.USERNAME).toString();
                    String password = receivedObj.get(MsgAttribute.PASSWORD).toString();
                    String receivedCommand = receivedObj.get(MsgAttribute.COMMAND).toString();

                    switch (receivedCommand) {
                        case ClientCommands.REGISTER:
                            JSONObject msgToServer = new JSONObject();
                            conReplyCount = connectedServers.size();
                            if (conReplyCount == 0) {
                                output = clientProcessor.processRegisterUser(username, password, true, connectedClients);
                            } else {
                                for (Connection conn : connections) {
                                    if (conn.isServer) {
                                        msgToServer.put(MsgAttribute.COMMAND, ServerCommands.LOCK_REQUEST);
                                        msgToServer.put(MsgAttribute.USERNAME, username);
                                        msgToServer.put(MsgAttribute.PASSWORD, password);
                                        conn.writeMsg(msgToServer.toString());
                                        log.info("LOCK_REQUEST sent");
                                    }
                                }
                            }
                            break;
                        case ClientCommands.LOGIN:
                            output = clientProcessor.verifyUserLogin(username, password, clientLoad, connectedClients);
                            break;
                        case ClientCommands.LOGOUT:
                            output = getJSONObject(ClientCommands.LOGOUT);
                            shouldClose = true;
                            break;
                        case ClientCommands.ACTIVITY_MESSAGE:
                            String activity = receivedObj.get(MsgAttribute.ACTIVITY).toString();
                            output = clientProcessor.verifyActivity(username, password, activity, connectedClients);
                            if (output.get(MsgAttribute.COMMAND).toString().equals(ServerCommands.ACTIVITY_BROADCAST)) {
                                broadcastActivity(receivedObj.get(MsgAttribute.ACTIVITY).toString());
                            }
                            break;
                        default:
                            output = getJSONObject(ServerCommands.INVALID_MESSAGE);
                            shouldClose = true;
                    }
                }
            } else {
                output = getJSONObject(ServerCommands.INVALID_MESSAGE);
                shouldClose = true;
            }

            if (output != null) {
                con.writeMsg(output.toString());
            }
            return shouldClose;
        } catch (Exception e) {
            log.error("Error occurs when process received messages" + e.getMessage());
            output = getJSONObject(ServerCommands.INVALID_MESSAGE);
            con.writeMsg(output.toString());
            return true;
        }
    }

    public void broadcastActivity(String activity) {
        JSONObject output = new JSONObject();
        for (Connection con : connections) {
            output.put(MsgAttribute.COMMAND, ServerCommands.ACTIVITY_BROADCAST);
            output.put(MsgAttribute.ACTIVITY, activity);
            con.writeMsg(output.toString());
            log.info("Announced to server");
        }
    }

    private boolean VerifyMessage(JSONObject msg) {
        String command;
        if (msg == null || (command = msg.get(MsgAttribute.COMMAND).toString()) == null) {
            return false;
        }
        switch (command) {
            case ClientCommands.REGISTER:
            case ClientCommands.LOGIN:
            case ServerCommands.LOCK_REQUEST:
            case ServerCommands.LOCK_ALLOWED:
            case ServerCommands.LOCK_DENIED:
                return msg.get(MsgAttribute.USERNAME) != null && msg.get(MsgAttribute.PASSWORD) != null;
            case ClientCommands.ACTIVITY_MESSAGE:
                return msg.get(MsgAttribute.USERNAME) != null && msg.get(MsgAttribute.PASSWORD) != null && msg.get(MsgAttribute.ACTIVITY) != null;
            case ServerCommands.AUTHENTICATE:
                return msg.get(MsgAttribute.SECRET) != null;
            case ServerCommands.SERVER_ANNOUNCE:
                return msg.get(MsgAttribute.ID) != null
                        && msg.get(MsgAttribute.LOAD) != null
                        && msg.get(MsgAttribute.HOSTNAME) != null
                        && msg.get(MsgAttribute.PORT).toString() != null;
            default:
                return false;
        }
    }

    @SuppressWarnings("unchecked")
    private JSONObject getJSONObject(String command) {
        JSONObject output = new JSONObject();
        output.put(MsgAttribute.COMMAND, command);
        switch (command) {
            case ServerCommands.INVALID_MESSAGE:
                output.put(MsgAttribute.INFO, Info.INVALID_MSG_INFO);
                break;
            case ServerCommands.SERVER_ANNOUNCE:
                output.put(MsgAttribute.ID, Settings.getSecret());
                output.put(MsgAttribute.LOAD, connectedClients.size());
                output.put(MsgAttribute.HOSTNAME, Settings.getLocalHostname());
                output.put(MsgAttribute.PORT, Settings.getLocalPort());
                break;
            case ClientCommands.LOGOUT:
                output.put(MsgAttribute.INFO, Info.LOGOUT_INFO);
                break;
        }
        return output;
    }

    private void serverAnnounce(Connection con) {
        JSONObject announcement = getJSONObject(ServerCommands.SERVER_ANNOUNCE);
        con.writeMsg(announcement.toString());
    }

    /*
     * The connection has been closed by the other party.
     */
    public synchronized void connectionClosed(Connection con) {
        if (!term) connections.remove(con);
    }


    @Override
    public void run() {
        log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
        while (!term) {
            // do something with 5 second intervals in between
            try {
                for (Connection con : connections) {
                    if (con.isServer) {
                        serverAnnounce(con);
                        log.info("Announced to server");
                    }
                }
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            if (!term) {
                log.debug("Keep running");
                term = doActivity();
            }

        }
        log.info("closing " + connections.size() + " connections");
        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }
        listener.setTerm(true);
    }

    public boolean doActivity() {
        return false;
    }

    public final void setTerm(boolean t) {
        term = t;
    }

    public final ArrayList<Connection> getConnections() {
        return connections;
    }
}
