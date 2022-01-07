package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;


import activitystreamer.server.processors.ClientRequestProcessor;
import activitystreamer.server.processors.ServerRequestProcessor;
import activitystreamer.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.simple.JSONObject;

import static activitystreamer.util.Helper.getString;

@SuppressWarnings("unchecked")
public class ServerControl extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static ArrayList<Connection> connections;
    private static ArrayList<String> loggedInClients;
    private static HashMap<String, Integer> allowedMap;
    private static HashMap<String, String> registeredClients;
    private static HashMap<String, Connection> reqServers;
    private static HashMap<String, Connection> reqClients;
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
        /*
         *  for demo purpose, use a simple fixed secret
         *  otherwise will use Settings.setSecret(Settings.nextSecret()) to generate a random secret
         */
        connections = new ArrayList<>();
        loggedInClients = new ArrayList<>();
        allowedMap = new HashMap<>();
        registeredClients = new HashMap<>();
        reqServers = new HashMap<>();
        reqClients = new HashMap<>();
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
                Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
                JSONObject auth = new JSONObject();
                auth.put(MsgField.COMMAND, Command.AUTHENTICATE);
                auth.put(MsgField.SECRET, Settings.getSecret());
                c.writeMsg(auth.toString());
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

        try {
            JSONObject receivedObj = Helper.JsonParser(receivedMsg);
            if (!VerifyMessage(receivedObj)) {
                con.reply(Command.INVALID_MESSAGE, Info.INVALID_MSG_INFO);
                return true;
            }
            String cmd = getString(receivedObj, MsgField.COMMAND);

            switch (cmd) {
                case Command.AUTHENTICATE:
                    con.setServer(true);
                    return !processAuth(receivedObj, con);
                case Command.USER_CHECK:
                    return !processUserCheck(receivedObj, con);
                case Command.USER_ALLOWED:
                    return !processUserAllowed(receivedObj, con);
                case Command.USER_DENIED:
                    return !processUserDenied(receivedObj, con);
                case Command.SERVER_ANNOUNCE:
                    // Will send AUTHENTICATE if announcement from first received id
                    return !processServerAnnounce(receivedObj, con);
                case Command.ACTIVITY_BROADCAST:
                    return !processActivityBroadcast(receivedObj, con);
                case Command.REGISTER:
                    return !processRegister(receivedObj, con);
                case Command.LOGIN:
                    return !processLogin(receivedObj, con);
                case Command.LOGOUT:
                    return !processLogout(con);
                case Command.ACTIVITY_MESSAGE:
                    return !processActivityMsg(receivedMsg, con);
                default:
                    con.reply(Command.INVALID_MESSAGE, "Command is invalid");
                    return true;
            }
        } catch (Exception e) {
            log.error("Error occurs when process received messages" + e.getMessage());
            con.reply(Command.ERRORS_OCCUR, e.getMessage());
            return true;
        }
    }

    private boolean processActivityMsg(String receivedMsg, Connection con) {
        return false;
    }

    private boolean processLogout(Connection con) {
        return false;
    }

    private boolean processLogin(JSONObject receivedObj, Connection con) {
        return false;
    }

    private boolean processRegister(JSONObject receivedObj, Connection con) {
        String username = getString(receivedObj, MsgField.USERNAME);
        String secret = getString(receivedObj, MsgField.SECRET);
        //invalid message if client has logged in
        if (getClientStatus(username)) {
            con.reply(Command.INVALID_MESSAGE, Info.REGISTER_FAILED_LOGIN_INFO);
            return true;
        }
        //register failed because username is known by local
        if (registeredClients.containsKey(username)) {
            con.reply(Command.REGISTER_FAILED, Info.REGISTER_FAILED_USER_EXIST_INFO);
            return true;
        }
        //send user check request
        registeredClients.put(username, secret);
        allowedMap.put(username, 0);
        for (Connection c : connections) {
            if (c.isServer) {
                c.writeMsg(getReigsterProcessReq(username, secret, Command.USER_CHECK));
            }
        }
        return false;
    }


    private boolean processAuth(JSONObject receivedObj, Connection con) {
        String secret = getString(receivedObj, MsgField.SECRET);
        if (secret.equals(Settings.getSecret())) {
            log.info("Authenticated successfully");
            return true;
        }
        con.reply(Command.AUTHENTICATION_FAIL, Info.AUTH_FAIL_SECRET_INCORRECT_INFO);
        return false;
    }

    private boolean processUserCheck(JSONObject receivedObj, Connection con) {
        String username = getString(receivedObj, MsgField.USERNAME);
        String secret = getString(receivedObj, MsgField.SECRET);
        if (registeredClients.containsKey(username)) {
            con.writeMsg(getReigsterProcessReq(username, secret, Command.USER_DENIED));
        } else if (countConnectedServers() - 1 <= 0) {
            con.writeMsg(getReigsterProcessReq(username, secret, Command.USER_ALLOWED));
        } else {
            for (Connection c : connections) {
                if (c.isServer && !c.equals(con)) {
                    c.writeMsg(getReigsterProcessReq(username, secret, Command.USER_CHECK));
                }
            }
        }
        return false;
    }

    private int countConnectedServers() {
        int count = 0;
        for (Connection c : connections) {
            if (c.isServer) {
                count++;
            }
        }
        return count;
    }

    private boolean processUserAllowed(JSONObject receivedObj, Connection con) {
        VerifyServer(con);
        String username = getString(receivedObj, MsgField.USERNAME);
        if (!allowedMap.containsKey(username)) {
            return false;
        }
        int allowedCount = allowedMap.get(username);

        return false;
    }

    private boolean processUserDenied(JSONObject receivedObj, Connection con) {
        return false;
    }

    private boolean processServerAnnounce(JSONObject receivedObj, Connection con) {
        return false;
    }

    private boolean processActivityBroadcast(JSONObject receivedObj, Connection con) {
        return false;
    }

    public void broadcastActivity(String activity) {
        JSONObject output = new JSONObject();
        for (Connection con : connections) {
            output.put(MsgField.COMMAND, Command.ACTIVITY_BROADCAST);
            output.put(MsgField.ACTIVITY, activity);
            con.writeMsg(output.toString());
            log.info("Announced to server");
        }
    }

    private String getReigsterProcessReq(String username, String secret, String command) {
        JSONObject checkReq = new JSONObject();
        checkReq.put(MsgField.COMMAND, command);
        checkReq.put(MsgField.USERNAME, username);
        checkReq.put(MsgField.SECRET, secret);
        return checkReq.toString();
    }

    private boolean getClientStatus(String username) {
        return loggedInClients.contains(username);
    }

    private void VerifyServer(Connection con) {
        if (!con.isServer) {
            throw new IllegalArgumentException("Not a valid server");
        }
    }

    private boolean VerifyMessage(JSONObject msg) {
        String command;
        if (msg == null || (command = msg.get(MsgField.COMMAND).toString()) == null) {
            return false;
        }
        switch (command) {
            case Command.REGISTER:
            case Command.LOGIN:
            case Command.USER_CHECK:
            case Command.USER_ALLOWED:
            case Command.USER_DENIED:
                return msg.get(MsgField.USERNAME) != null && msg.get(MsgField.PASSWORD) != null;
            case Command.ACTIVITY_MESSAGE:
                return msg.get(MsgField.USERNAME) != null && msg.get(MsgField.PASSWORD) != null && msg.get(MsgField.ACTIVITY) != null;
            case Command.AUTHENTICATE:
                return msg.get(MsgField.SECRET) != null;
            case Command.SERVER_ANNOUNCE:
                return msg.get(MsgField.ID) != null
                        && msg.get(MsgField.LOAD) != null
                        && msg.get(MsgField.HOSTNAME) != null
                        && msg.get(MsgField.PORT).toString() != null;
            default:
                return false;
        }
    }

    private void broadcast(Connection sender, JSONObject msg, boolean sendToServer) {
        for (Connection con : connections) {
            if (con == sender) continue;
            if (!con.isServer) {
                con.writeMsg(msg.toJSONString());
            }
            if (con.isServer() && sendToServer) {
                con.writeMsg(msg.toString());
            }
        }
    }

    private boolean serverAnnounce() {
        JSONObject state = new JSONObject();
        state.put(MsgField.ID, Settings.getSecret());
        state.put(MsgField.LOAD, loggedInClients.size());
        state.put(MsgField.HOSTNAME, Settings.getLocalHostname());
        state.put(MsgField.PORT, Settings.getLocalPort());
        broadcast(null, state, true);
        return false;
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
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            if (!term) {
                log.debug("Announcing");
                term = serverAnnounce();
            }
        }
        log.info("closing " + connections.size() + " connections");
        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }
        listener.setTerm(true);
    }

    public final void setTerm(boolean t) {
        term = t;
    }

    public final ArrayList<Connection> getConnections() {
        return connections;
    }
}
