package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import activitystreamer.util.Helper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;

import static activitystreamer.util.Constants.ClientCommands;
import static activitystreamer.util.Constants.ServerCommands;
import static activitystreamer.util.Constants.Info;
import static activitystreamer.util.Constants.MsgAttribute;

public class Control extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static ArrayList<Connection> connections;
    private static HashMap<String, User> users;
    private static boolean term = false;
    private static Listener listener;

    protected static Control control = null;

    public static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    private Control() {
        // initialize the connections array
        connections = new ArrayList<Connection>();
        users = new HashMap<String, User>();
        // start a listener
        try {
            listener = new Listener();
        } catch (IOException e1) {
            log.fatal("failed to startup a listening thread: " + e1);
            System.exit(-1);
        }
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
     * Processing incoming messages from the connection.
     * Return true if the connection should close.
     */
    public synchronized boolean process(Connection con, String receivedMsg) {
        JSONObject output = new JSONObject();
        String username = null;
        String secret = null;
        String activity = null;
        try {
            JSONObject receivedObj = Helper.JsonParser(receivedMsg);
            switch (receivedObj.get(MsgAttribute.COMMAND).toString()) {
                case ClientCommands.LOGIN:
                    if (VerifyUserLogin(username, secret)) {
                        output.put(MsgAttribute.COMMAND, ServerCommands.LOGIN_SUCCESS);
                        output.put(MsgAttribute.INFO, Info.LOGIN_SUCCESS_INFO);
                    } else {
                        output.put(MsgAttribute.COMMAND, ServerCommands.LOGIN_FAILED);
                        output.put(MsgAttribute.INFO, Info.LOGIN_FAILED_INFO);
                        connectionClosed(con);
                        return true;
                    }
                    break;
                case ClientCommands.REGISTER:
                    processRegisterUser(username, secret, con);
                    break;
                case ClientCommands.ACTIVITY_MESSAGE:
                    if (VerifyActivity(username, secret)) {
                        BroadcastActivity(activity);
                    } else {
                        output.put(MsgAttribute.COMMAND, ServerCommands.INVALID_MESSAGE);
                        output.put(MsgAttribute.INFO, Info.INVALID_MSG_INFO);
                        connectionClosed(con);
                        return true;
                    }
                    break;
                case ClientCommands.LOGOUT:
                    connectionClosed(con);
                    return true;
                default:
                    output.put(MsgAttribute.COMMAND, ServerCommands.INVALID_MESSAGE);
                    output.put(MsgAttribute.INFO, Info.INVALID_MSG_INFO);
                    connectionClosed(con);
                    return true;
            }
            con.writeMsg(output.toJSONString());
            return false;
        } catch (Exception e) {
            log.error("Error occurs when process received messages" + e.getMessage());
            return true;
        }
    }

    private boolean VerifyUserLogin(String username, String secret) {

        return true;
    }

    private JSONObject processRegisterUser(String username, String secret, Connection con) {
        JSONObject output = new JSONObject();
        if (users.containsKey(username)) {
            if (users.get(username).isLogin) {
                output.put(MsgAttribute.COMMAND, ServerCommands.REGISTER_FAILED);
                output.put(MsgAttribute.INFO, Info.REGISTER_FAILED_INFO);
                connectionClosed(con);
            }
            output.put(MsgAttribute.COMMAND, ServerCommands.INVALID_MESSAGE);
            output.put(MsgAttribute.INFO, Info.INVALID_MSG_INFO);
            connectionClosed(con);
        } else {
            users.put(username, new User(username, secret));
            output.put(MsgAttribute.COMMAND, ServerCommands.REGISTER_SUCCESS);
            output.put(MsgAttribute.INFO, Info.REGISTER_SUCCESS_INFO);
        }
        return output;
    }

    private boolean VerifyActivity(String username, String secret) {
        return true;
    }

    private void BroadcastActivity(String activity) {

    }

    /*
     * The connection has been closed by the other party.
     */
    public synchronized void connectionClosed(Connection con) {
        if (!term) connections.remove(con);
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
        c.setIsServer();
        connections.add(c);
        return c;

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
                log.debug("doing activity");
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
