package activitystreamer.server;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;


public class Connection extends Thread {
    private static final Logger log = LogManager.getLogger();
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader inReader;
    private PrintWriter outWriter;
    private boolean open = false;
    private Socket socket;
    private boolean term = false;
    public boolean isServer = false;

    Connection(Socket socket) throws IOException {
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        inReader = new BufferedReader(new InputStreamReader(in));
        outWriter = new PrintWriter(out, true);
        this.socket = socket;
        open = true;
        start();
    }

    /*
     * returns true if the message was written, otherwise false
     */
    public boolean writeMsg(String msg) {
        if (open) {
            outWriter.println(msg);
            outWriter.flush();
            return true;
        }
        return false;
    }

    public void closeCon() {
        if (open) {
            log.info("closing connection " + Settings.socketAddress(socket));
            try {
                term = true;
                inReader.close();
                out.close();
            } catch (IOException e) {
                // already closed?
                log.error("received exception closing the connection " + Settings.socketAddress(socket) + ": " + e);
            }
        }
    }


    public void run() {
        try {
            String data = null;

            while (!term && (data = inReader.readLine()) != null) {
                log.debug("Get data " + data);
                term = ServerControl.getInstance().process(this, data);
            }
            log.debug("connection closed to " + Settings.socketAddress(socket));
            ServerControl.getInstance().connectionClosed(this);
            in.close();
        } catch (IOException e) {
            log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);
            ServerControl.getInstance().connectionClosed(this);
        }
        open = false;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isServer() {
        return isServer;
    }

    public void setIsServer() {
        isServer = true;
    }

}
