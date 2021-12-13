package activitystreamer.server.processors;

import activitystreamer.server.models.ClientModel;
import activitystreamer.server.models.ProcessResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.HashMap;

import static activitystreamer.util.Constants.ClientCommands;
import static activitystreamer.util.Constants.ServerCommands;
import static activitystreamer.util.Constants.Info;
import static activitystreamer.util.Constants.MsgAttribute;

public class ClientRequestProcessor {
    private static final Logger log = LogManager.getLogger();
    private static ClientRequestProcessor processor;
    private HashMap<String, ClientModel> users;

    public static ClientRequestProcessor getClientRequestProcessor() {
        if (processor == null) {
            processor = new ClientRequestProcessor();
        }
        return processor;
    }

    public ProcessResult processClientRequest(JSONObject receivedObj, HashMap<String, ClientModel> users) {
        this.users = users;
        JSONObject output = new JSONObject();
        boolean shouldClose = false;
        String username = null;
        String password = null;
        if (receivedObj.get(MsgAttribute.USERNAME) != null) {
            username = receivedObj.get(MsgAttribute.USERNAME).toString();
        }
        if (receivedObj.get(MsgAttribute.PASSWORD) != null) {
            password = receivedObj.get(MsgAttribute.PASSWORD).toString();
        }
        switch (receivedObj.get(MsgAttribute.COMMAND).toString()) {
            case ClientCommands.LOGIN:
                output = VerifyUserLogin(username, password);
                break;
            case ClientCommands.ACTIVITY_MESSAGE:
                String activity = receivedObj.get(MsgAttribute.ACTIVITY).toString();
                output = VerifyActivity(username, password, activity);
                break;
            case ClientCommands.LOGOUT:
                output.put(MsgAttribute.INFO, Info.LOGOUT_INFO);
                shouldClose = true;
                break;
            default:
                output.put(MsgAttribute.COMMAND, ServerCommands.INVALID_MESSAGE);
                output.put(MsgAttribute.INFO, Info.INVALID_MSG_INFO);
                shouldClose = true;
        }
        return new ProcessResult(output, shouldClose);
    }

    private JSONObject VerifyUserLogin(String username, String password) {
        JSONObject output = new JSONObject();
        if (username != null && password != null) {
            if (!users.containsKey(username)) {
                output.put(MsgAttribute.COMMAND, ServerCommands.LOGIN_FAILED);
                output.put(MsgAttribute.INFO, Info.LOGIN_FAILED_NOT_EXIST_INFO);
            } else if ((users.get(username).password).equals(password)) {
                users.get(username).isLogin = true;
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

    public JSONObject processRegisterUser(String username, String password, boolean canRegister, HashMap<String, ClientModel> users) {
        JSONObject output = new JSONObject();
        if (username != null && password != null && canRegister) {
            if (users.containsKey(username)) {
                if (users.get(username).isLogin) {
                    output.put(MsgAttribute.COMMAND, ServerCommands.REGISTER_FAILED);
                    output.put(MsgAttribute.INFO, username + " " + Info.REGISTER_FAILED_LOGIN_INFO);
                } else {
                    output.put(MsgAttribute.COMMAND, ServerCommands.REGISTER_FAILED);
                    output.put(MsgAttribute.INFO, username + " " + Info.REGISTER_FAILED_USER_EXIST_INFO);
                }
            } else {
                users.put(username, new ClientModel(username, password));
                output.put(MsgAttribute.COMMAND, ServerCommands.REGISTER_SUCCESS);
                output.put(MsgAttribute.INFO, Info.REGISTER_SUCCESS_INFO + ": " + username);
            }
        } else {
            output.put(MsgAttribute.COMMAND, ServerCommands.REGISTER_FAILED);
            output.put(MsgAttribute.INFO, Info.REGISTER_FAILED_MISS_DETAILS_INFO + " OR " + Info.REGISTER_FAILED_USER_EXIST_INFO);
        }

        return output;
    }

    private JSONObject VerifyActivity(String username, String password, String activity) {
        JSONObject output = new JSONObject();
        try {
            if (users.containsKey(username)) {
                ClientModel client = users.get(username);
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
