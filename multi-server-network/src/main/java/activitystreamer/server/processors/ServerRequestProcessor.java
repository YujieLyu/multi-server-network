package activitystreamer.server.processors;

import activitystreamer.server.models.ClientModel;

import java.util.HashMap;

public class ServerRequestProcessor {
    private static ServerRequestProcessor processor;

    public static ServerRequestProcessor getServerRequestProcessor() {
        if (processor == null) {
            processor = new ServerRequestProcessor();
        }
        return processor;
    }

    public void broadcastActivity(String activity) {

    }

    public boolean userCanRegister() {
        return true;
    }

    public boolean UserNotExist(String username, HashMap<String, ClientModel> users) {
        return users.containsKey(username);
    }
}
