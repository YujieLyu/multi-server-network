package activitystreamer.server.processors;

import activitystreamer.server.models.ClientModel;
import activitystreamer.server.models.ServerModel;
import activitystreamer.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import static activitystreamer.util.Constants.ServerCommands;
import static activitystreamer.util.Constants.MsgAttribute;

import java.util.HashMap;

public class ServerRequestProcessor {
    private static final Logger log = LogManager.getLogger();
    private static ServerRequestProcessor processor;

    public static ServerRequestProcessor getServerRequestProcessor() {
        if (processor == null) {
            processor = new ServerRequestProcessor();
        }
        return processor;
    }

    @SuppressWarnings("unchecked")
    public JSONObject processLockRequest(String username, String password, HashMap<String, ClientModel> connectedClients) {
        JSONObject output = new JSONObject();
        log.debug("Current connected clients " + connectedClients);
        if (!connectedClients.containsKey(username)) {
            output.put(Constants.MsgAttribute.COMMAND, Constants.ServerCommands.LOCK_ALLOWED);
        } else {
            output.put(Constants.MsgAttribute.COMMAND, Constants.ServerCommands.LOCK_DENIED);
        }
        output.put(MsgAttribute.USERNAME, username);
        output.put(MsgAttribute.PASSWORD, password);
        return output;
    }

    @SuppressWarnings("unchecked")
    public void processServerAnnounce(JSONObject receivedObj, HashMap<String, ServerModel> connectedServers) {
        String id = receivedObj.get(Constants.MsgAttribute.ID).toString();
        String load = receivedObj.get(Constants.MsgAttribute.LOAD).toString();
        String hostname = receivedObj.get(Constants.MsgAttribute.HOSTNAME).toString();
        String port = receivedObj.get(Constants.MsgAttribute.PORT).toString();

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
