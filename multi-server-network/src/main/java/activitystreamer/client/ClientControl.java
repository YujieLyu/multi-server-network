package activitystreamer.client;

import java.io.*;
import java.net.Socket;

import activitystreamer.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

public class ClientControl extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static ClientControl clientSolution;
    private CommunicationFrame communicationFrame;
    private int portnum;
    Socket socket = null;
    private InputStreamReader in;
    private OutputStreamWriter out;
    private BufferedReader inReader;
    private PrintWriter outWriter;

    public static ClientControl getInstance() {
        if (clientSolution == null) {
            clientSolution = new ClientControl();
        }
        return clientSolution;
    }

    public ClientControl() {
        communicationFrame = new CommunicationFrame();
        start();
    }


    @SuppressWarnings("unchecked")
    public void sendActivityObject(JSONObject activityObj) {
        outWriter.println(activityObj);
        outWriter.flush();
    }

    @SuppressWarnings("unchecked")
    public void processReceivedMsg(String receivedMsg) {
        JSONObject output = new JSONObject();
        JSONObject receivedObj = Helper.JsonParser(receivedMsg);
        try {
            output.put("command", receivedObj.get(MsgField.COMMAND).toString());
            output.put("info", receivedObj.get(MsgField.INFO).toString());
        } catch (Exception e) {
            log.error("Cannot process received message" + e.getMessage());
            output.put("command", Command.INVALID_MESSAGE);
            output.put("info", Info.INVALID_MSG_INFO);
        }
        communicationFrame.setOutputText(output);
    }


    public void disconnect() {

    }


    public void run() {
        log.info("Client is running");

        try {
            portnum = Settings.getLocalPort();
            socket = new Socket(Settings.getLocalHostname(), portnum);
            in = new InputStreamReader(socket.getInputStream());
            out = new OutputStreamWriter(socket.getOutputStream());
            inReader = new BufferedReader(in);
            outWriter = new PrintWriter(out, true);
            String data;

            while ((data = inReader.readLine()) != null) {
                processReceivedMsg(data);
            }

            log.info("Client has sent request");

        } catch (IOException e) {
            log.error("Exception occurs" + e);
        }
    }


}
