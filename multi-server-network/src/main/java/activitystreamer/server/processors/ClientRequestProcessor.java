package activitystreamer.server.processors;

import activitystreamer.server.models.ClientModel;
import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.HashMap;

import static activitystreamer.util.Constants.ServerCommands;
import static activitystreamer.util.Constants.Info;
import static activitystreamer.util.Constants.MsgAttribute;

public class ClientRequestProcessor {
    private static final Logger log = LogManager.getLogger();
    private static ClientRequestProcessor processor;

    public static ClientRequestProcessor getClientRequestProcessor() {
        if (processor == null) {
            processor = new ClientRequestProcessor();
        }
        return processor;
    }

    @SuppressWarnings("unchecked")
    public JSONObject verifyUserLogin(String username, String password, int clientLoad, HashMap<String, ClientModel> connectedClients) {
        JSONObject output = new JSONObject();
        if (username != null && password != null) {
            if(clientLoad>1){
                output.put(MsgAttribute.COMMAND, ServerCommands.REDIRECT);
                output.put(MsgAttribute.HOSTNAME, Settings.getRemoteHostname());
                output.put(MsgAttribute.PORT, Settings.getRemotePort());
            }
            if (!connectedClients.containsKey(username)) {
                output.put(MsgAttribute.COMMAND, ServerCommands.LOGIN_FAILED);
                output.put(MsgAttribute.INFO, Info.LOGIN_FAILED_NOT_EXIST_INFO);
            } else if ((connectedClients.get(username).password).equals(password)) {
                connectedClients.get(username).isLogin = true;
                clientLoad++;
                output.put(MsgAttribute.COMMAND, ServerCommands.LOGIN_SUCCESS);
                output.put(MsgAttribute.INFO, Info.LOGIN_SUCCESS_INFO + ": " + username);
            } else {
                output.put(MsgAttribute.COMMAND, ServerCommands.LOGIN_FAILED);
                output.put(MsgAttribute.INFO, Info.LOGIN_FAILED_INFO);
            }
        } else {
            output.put(MsgAttribute.COMMAND, ServerCommands.LOGIN_FAILED);
            output.put(MsgAttribute.INFO, Info.INVALID_MSG_INFO);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    public JSONObject processRegisterUser(String username, String password, boolean canRegister, HashMap<String, ClientModel> connectedClients) {
        JSONObject output = new JSONObject();
        if (username != null && password != null && canRegister) {
            if (connectedClients.containsKey(username)) {
                if (connectedClients.get(username).isLogin) {
                    output.put(MsgAttribute.COMMAND, ServerCommands.REGISTER_FAILED);
                    output.put(MsgAttribute.INFO, username + " " + Info.REGISTER_FAILED_LOGIN_INFO);
                } else {
                    output.put(MsgAttribute.COMMAND, ServerCommands.REGISTER_FAILED);
                    output.put(MsgAttribute.INFO, username + " " + Info.REGISTER_FAILED_USER_EXIST_INFO);
                }
            } else {
                connectedClients.put(username, new ClientModel(username, password));
                output.put(MsgAttribute.COMMAND, ServerCommands.REGISTER_SUCCESS);
                output.put(MsgAttribute.INFO, Info.REGISTER_SUCCESS_INFO + ": " + username);
            }
        } else {
            output.put(MsgAttribute.COMMAND, ServerCommands.REGISTER_FAILED);
            output.put(MsgAttribute.INFO, Info.REGISTER_FAILED_MISS_DETAILS_INFO + " OR " + Info.REGISTER_FAILED_USER_EXIST_INFO);
        }

        return output;
    }

    @SuppressWarnings("unchecked")
    public JSONObject verifyActivity(String username, String password, String activity, HashMap<String, ClientModel> connectedClients) {
        JSONObject output = new JSONObject();
        try {
            if (connectedClients.containsKey(username)) {
                ClientModel client = connectedClients.get(username);
                if (client.password.equals(password) && client.isLogin) {
                    output.put(MsgAttribute.COMMAND, ServerCommands.ACTIVITY_BROADCAST);
                    output.put(MsgAttribute.INFO, Info.ACTIVITY_BROADCAST_INFO + ": " + activity);
                } else {
                    output.put(MsgAttribute.COMMAND, ServerCommands.AUTHENTICATION_FAIL);
                    output.put(MsgAttribute.INFO, Info.LOGIN_FAILED_INFO + " OR " + Info.AUTH_FAIL_ANONYMOUS_INFO);
                }
            } else {
                output.put(MsgAttribute.COMMAND, ServerCommands.AUTHENTICATION_FAIL);
                output.put(MsgAttribute.INFO, Info.AUTH_FAIL_ANONYMOUS_INFO);
            }
        } catch (Exception e) {
            log.error("Invalid message" + e.getMessage());
            output.put(MsgAttribute.COMMAND, ServerCommands.INVALID_MESSAGE);
            output.put(MsgAttribute.INFO, Info.INVALID_MSG_INFO);
        }
        return output;
    }


}
