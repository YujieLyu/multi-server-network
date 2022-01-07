package activitystreamer.server.processors;

import activitystreamer.server.models.ClientModel;
import activitystreamer.server.models.ServerModel;
import activitystreamer.util.Command;
import activitystreamer.util.MsgField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.HashMap;

@SuppressWarnings("unchecked")
public class ServerRequestProcessor {
    private static final Logger log = LogManager.getLogger();
    private static ServerRequestProcessor processor;

    public static ServerRequestProcessor getServerRequestProcessor() {
        if (processor == null) {
            processor = new ServerRequestProcessor();
        }
        return processor;
    }


    public JSONObject processLockRequest(String username, String password, HashMap<String, ClientModel> connectedClients) {
        JSONObject output = new JSONObject();
        log.debug("Current connected clients " + connectedClients);
        if (!connectedClients.containsKey(username)) {
            output.put(MsgField.COMMAND, Command.USER_ALLOWED);
        } else {
            output.put(MsgField.COMMAND, Command.USER_DENIED);
        }
        output.put(MsgField.USERNAME, username);
        output.put(MsgField.PASSWORD, password);
        return output;
    }

    public void processServerAnnounce(JSONObject receivedObj, HashMap<String, ServerModel> connectedServers) {
        String id = receivedObj.get(MsgField.ID).toString();
        String load = receivedObj.get(MsgField.LOAD).toString();
        String hostname = receivedObj.get(MsgField.HOSTNAME).toString();
        String port = receivedObj.get(MsgField.PORT).toString();

        if (!connectedServers.containsKey(id)) {
            connectedServers.put(id, new ServerModel(id, load, hostname, port));

        } else {
            ServerModel server = connectedServers.get(id);
            server.setLoad(load);
            server.setHostname(hostname);
            server.setPort(port);
        }
    }


}
