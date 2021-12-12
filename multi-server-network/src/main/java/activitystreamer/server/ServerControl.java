package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;


import activitystreamer.server.models.ClientModel;
import activitystreamer.server.models.ProcessResult;
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

    protected static ServerControl serverControl = null;

    public static ServerControl getInstance() {
        if (serverControl == null) {
            serverControl = new ServerControl();
        }
        return serverControl;
    }

    private ServerControl() {
        // initialize the connections array
        //for demo purpose, use a simple fixed secret
        //Settings.setSecret(Settings.nextSecret());
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
     * A new incoming connection has been established, and a reference is returned to it
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        log.debug("incomming connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        connections.add(c);
        return c;
    }

    /*
     * A new outgoing connection has been established, and a reference is returned to it
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        connections.add(c);
        return c;

    }

    /*
     * Processing incoming messages from the connection.
     * Return true if the connection should close.
     */
    public synchronized boolean process(Connection con, String receivedMsg) {
        JSONObject output = new JSONObject();
        boolean shouldClose = false;
        try {
            JSONObject receivedObj = Helper.JsonParser(receivedMsg);
            if (receivedObj != null && VerifyMessage(receivedObj)) {
                if (receivedObj.get(MsgAttribute.COMMAND).toString().equals(ServerCommands.AUTHENTICATE)) {
                    con.isServer = true;
                }
                if (con.isServer) {
                    String receivedCommand = receivedObj.get(MsgAttribute.COMMAND).toString();
                    JSONObject msgToSender = new JSONObject();
                    switch (receivedCommand) {
                        case ServerCommands.AUTHENTICATE:
                            if (Settings.getSecret().equals(receivedObj.get(MsgAttribute.SECRET).toString())) {
                                log.info("This server connection authenticated successfully");
                                return false;
                            } else {
                                return true;
                            }
                        case ServerCommands.LOCK_REQUEST:
                            String username = receivedObj.get(MsgAttribute.USERNAME).toString();
                            if (serverProcessor.UserNotExist(username, connectedClients)) {
                                msgToSender.put(MsgAttribute.COMMAND, ServerCommands.LOCK_ALLOWED);
                            } else {
                                msgToSender.put(MsgAttribute.COMMAND, ServerCommands.LOCK_DENIED);
                            }
                            con.writeMsg(msgToSender.toString());
                            break;
                        case ServerCommands.SERVER_ANNOUNCE:
                            String id = receivedObj.get(MsgAttribute.ID).toString();
                            String load = receivedObj.get(MsgAttribute.LOAD).toString();
                            String hostname = receivedObj.get(MsgAttribute.HOSTNAME).toString();
                            String port = receivedObj.get(MsgAttribute.PORT).toString();
                            if (!connectedServers.containsKey(id)) {
                                connectedServers.put(id, new ServerModel(id, load, hostname, port));
                            } else {
                                ServerModel server = connectedServers.get(id);
                                server.setLoad(load);
                                server.setHostname(hostname);
                                server.setPort(port);
                            }
                            break;
                        default:
                            return true;
                    }
                } else {
                    String username = null;
                    String secret = null;
                    String receivedCommand = receivedObj.get(MsgAttribute.COMMAND).toString();
                    if (receivedObj.get(MsgAttribute.USERNAME) != null) {
                        username = receivedObj.get(MsgAttribute.USERNAME).toString();
                    }
                    if (receivedObj.get(MsgAttribute.SECRET) != null) {
                        secret = receivedObj.get(MsgAttribute.SECRET).toString();
                    }
                    if (ClientCommands.REGISTER.equals(receivedCommand)) {
                        boolean userCanRegister = false;
                        JSONObject msgToServer = new JSONObject();
                        for (Connection conn : connections) {
                            if (conn.isServer) {
                                msgToServer.put(MsgAttribute.COMMAND, ServerCommands.LOCK_REQUEST);
                                msgToServer.put(MsgAttribute.USERNAME, username);
                                msgToServer.put(MsgAttribute.SECRET, secret);
                                conn.writeMsg(msgToServer.toString());
                            }
                        }
                    }
                    ProcessResult result = clientProcessor.processClientRequest(receivedObj, connectedClients);
                    output = result.getOutput();
                    String command = output.get(MsgAttribute.COMMAND).toString();
                    if (command.equals(ServerCommands.ACTIVITY_BROADCAST)) {
                        serverProcessor.broadcastActivity(receivedObj.get(MsgAttribute.ACTIVITY).toString());
                    }
                    shouldClose = result.getShouldConClose();
                }
            } else {
                output = getInvalidMsgResponse();
                shouldClose = true;
            }
            con.writeMsg(output.toJSONString());
            return shouldClose;
        } catch (Exception e) {
            log.error("Error occurs when process received messages" + e.getMessage());
            output = getInvalidMsgResponse();
            con.writeMsg(output.toJSONString());
            return true;
        }
    }

    private boolean VerifyMessage(JSONObject msg) {
        String command;
        if ((command = msg.get(MsgAttribute.COMMAND).toString()) != null) {
            if (command.equals(ClientCommands.REGISTER)
                    || command.equals(ClientCommands.LOGIN)
                    || command.equals(ServerCommands.LOCK_REQUEST)
                    || command.equals(ServerCommands.LOCK_ALLOWED)
                    || command.equals(ServerCommands.LOCK_DENIED)) {
                return msg.get(MsgAttribute.USERNAME) != null && msg.get(MsgAttribute.SECRET) != null;
            } else if (command.equals(ClientCommands.ACTIVITY_MESSAGE)) {
                return msg.get(MsgAttribute.USERNAME) != null && msg.get(MsgAttribute.SECRET) != null && msg.get(MsgAttribute.ACTIVITY) != null;
            } else if (command.equals(ServerCommands.AUTHENTICATE)) {
                return msg.get(MsgAttribute.SECRET) != null;
            } else if (command.equals(ServerCommands.SERVER_ANNOUNCE)) {
                return msg.get(MsgAttribute.ID) != null
                        && msg.get(MsgAttribute.LOAD) != null
                        && msg.get(MsgAttribute.HOSTNAME) != null
                        && msg.get(MsgAttribute.PORT).toString() != null;
            } else {
                return false;
            }
        }
        return false;
    }

    private JSONObject getInvalidMsgResponse() {
        JSONObject output = new JSONObject();
        output.put(MsgAttribute.COMMAND, ServerCommands.INVALID_MESSAGE);
        output.put(MsgAttribute.INFO, Info.INVALID_MSG_INFO);
        return output;
    }

    private void serverAnnounce(Connection con) {
        JSONObject announcement = new JSONObject();
        announcement.put(MsgAttribute.COMMAND, ServerCommands.SERVER_ANNOUNCE);
        announcement.put(MsgAttribute.ID, Settings.getSecret());
        announcement.put(MsgAttribute.LOAD, connections.size());
        announcement.put(MsgAttribute.HOSTNAME, Settings.getLocalHostname());
        announcement.put(MsgAttribute.PORT, Settings.getLocalPort());
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
                    serverAnnounce(con);
                }
                log.info("Has announced to " + connections.size() + " connections");
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
