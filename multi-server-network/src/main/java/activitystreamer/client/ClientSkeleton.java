package activitystreamer.client;

import java.io.*;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import activitystreamer.util.Settings;

public class ClientSkeleton extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static ClientSkeleton clientSolution;
    private TextFrame textFrame;
    private int portnum;
    Socket socket = null;
    private InputStreamReader in;
    private OutputStreamWriter out;
    private BufferedReader inReader;
    private PrintWriter outWriter;

    public static ClientSkeleton getInstance() {
        if (clientSolution == null) {
            clientSolution = new ClientSkeleton();
        }
        return clientSolution;
    }

    public ClientSkeleton() {
        textFrame = new TextFrame();
        start();
    }


    @SuppressWarnings("unchecked")
    public void sendActivityObject(JSONObject activityObj) {
        String msgToSend = activityObj.get("command").toString();
            switch (msgToSend) {
                case "LOGIN":
                    outWriter.write(msgToSend);

            }
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

            while ( inReader.readLine() != null) {
                JSONObject output=new JSONObject();
                output.put("command","LOGIN_SUCCESS");
                textFrame.setOutputText(output);
            }

            log.info("Client has sent LOGIN");


        } catch (IOException e) {
            log.error("Exception occurs" + e);
        }
    }


}
