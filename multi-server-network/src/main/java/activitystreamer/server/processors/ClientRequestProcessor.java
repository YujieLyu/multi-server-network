package activitystreamer.server.processors;

import activitystreamer.server.models.ClientModel;
import activitystreamer.util.MsgField;
import activitystreamer.util.Command;
import activitystreamer.util.Info;
import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.HashMap;

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
                output.put(MsgField.COMMAND, Command.REDIRECT);
                output.put(MsgField.HOSTNAME, Settings.getRemoteHostname());
                output.put(MsgField.PORT, Settings.getRemotePort());
            }
            if (!connectedClients.containsKey(username)) {
                output.put(MsgField.COMMAND, Command.LOGIN_FAILED);
                output.put(MsgField.INFO, Info.LOGIN_FAILED_NOT_EXIST_INFO);
            } else if ((connectedClients.get(username).password).equals(password)) {
                connectedClients.get(username).isLogin = true;
                clientLoad++;
                output.put(MsgField.COMMAND, Command.LOGIN_SUCCESS);
                output.put(MsgField.INFO, Info.LOGIN_SUCCESS_INFO + ": " + username);
            } else {
                output.put(MsgField.COMMAND, Command.LOGIN_FAILED);
                output.put(MsgField.INFO, Info.LOGIN_FAILED_INFO);
            }
        } else {
            output.put(MsgField.COMMAND, Command.LOGIN_FAILED);
            output.put(MsgField.INFO, Info.INVALID_MSG_INFO);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    public JSONObject processRegisterUser(String username, String password, boolean canRegister, HashMap<String, ClientModel> connectedClients) {
        JSONObject output = new JSONObject();
        if (username != null && password != null && canRegister) {
            if (connectedClients.containsKey(username)) {
                if (connectedClients.get(username).isLogin) {
                    output.put(MsgField.COMMAND, Command.REGISTER_FAILED);
                    output.put(MsgField.INFO, username + " " + Info.REGISTER_FAILED_LOGIN_INFO);
                } else {
                    output.put(MsgField.COMMAND, Command.REGISTER_FAILED);
                    output.put(MsgField.INFO, username + " " + Info.REGISTER_FAILED_USER_EXIST_INFO);
                }
            } else {
                connectedClients.put(username, new ClientModel(username, password));
                output.put(MsgField.COMMAND, Command.REGISTER_SUCCESS);
                output.put(MsgField.INFO, Info.REGISTER_SUCCESS_INFO + ": " + username);
            }
        } else {
            output.put(MsgField.COMMAND, Command.REGISTER_FAILED);
            output.put(MsgField.INFO, Info.REGISTER_FAILED_MISS_DETAILS_INFO + " OR " + Info.REGISTER_FAILED_USER_EXIST_INFO);
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
                    output.put(MsgField.COMMAND, Command.ACTIVITY_BROADCAST);
                    output.put(MsgField.INFO, Info.ACTIVITY_BROADCAST_INFO + ": " + activity);
                } else {
                    output.put(MsgField.COMMAND, Command.AUTHENTICATION_FAIL);
                    output.put(MsgField.INFO, Info.LOGIN_FAILED_INFO + " OR " + Info.AUTH_FAIL_ANONYMOUS_INFO);
                }
            } else {
                output.put(MsgField.COMMAND, Command.AUTHENTICATION_FAIL);
                output.put(MsgField.INFO, Info.AUTH_FAIL_ANONYMOUS_INFO);
            }
        } catch (Exception e) {
            log.error("Invalid message" + e.getMessage());
            output.put(MsgField.COMMAND, Command.INVALID_MESSAGE);
            output.put(MsgField.INFO, Info.INVALID_MSG_INFO);
        }
        return output;
    }


}
