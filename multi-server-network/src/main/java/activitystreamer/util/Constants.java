package activitystreamer.util;

public class Constants {
    public static class ClientCommands {
        public static final String ACTIVITY_MESSAGE = "ACTIVITY_MESSAGE";
        public static final String LOGIN = "LOGIN";
        public static final String LOGOUT = "LOGOUT";
        public static final String REGISTER = "REGISTER";
        public static final String INVALID_MESSAGE = "INVALID_MESSAGE";
    }

    public static class ServerCommands {
        public static final String AUTHENTICATE = "AUTHENTICATE";
        public static final String AUTHENTICATION_FAIL = "AUTHENTICATION_FAIL";
        public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
        public static final String LOGIN_FAILED = "LOGIN_FAILED";
        public static final String REDIRECT = "REDIRECT";
        public static final String INVALID_MESSAGE = "INVALID_MESSAGE";
        public static final String SERVER_ANNOUNCE = "SERVER_ANNOUNCE";
        public static final String ACTIVITY_BROADCAST = "ACTIVITY_BROADCAST";
        public static final String REGISTER_FAILED = "REGISTER_FAILED";
        public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
        public static final String LOCK_REQUEST = "LOCK_REQUEST";
        public static final String LOCK_DENIED = "LOCK_DENIED";
        public static final String LOCK_ALLOWED = "LOCK_ALLOWED";
    }

    public static class Info {
        public static final String INVALID_MSG_INFO = "The received message is invalid";
        public static final String AUTH_FAIL_SECRET_INCORRECT_INFO = "The supplied secret is incorrect:";
        public static final String AUTH_FAIL_ANONYMOUS_INFO = "User is not existingÎ";
        public static final String LOGIN_SUCCESS_INFO = "Logged in as user";
        public static final String LOGIN_FAILED_INFO = "Attempt to login with wrong password";
        public static final String LOGIN_FAILED_NOT_EXIST_INFO = "Username is not existing";
        public static final String LOGOUT_INFO = "Current user has logged out, connection is closed";
        public static final String REGISTER_FAILED_USER_EXIST_INFO = "is already registered with the system";
        public static final String REGISTER_FAILED_LOGIN_INFO = "The current login user cannot be registered again";
        public static final String REGISTER_FAILED_MISS_DETAILS_INFO = "Username/secrets missed";
        public static final String REGISTER_SUCCESS_INFO = "Register successfully for";
        public static final String ACTIVITY_BROADCAST_INFO = "Broadcasting activity";
    }

    public static class MsgAttribute {
        public static final String COMMAND = "command";
        public static final String USERNAME = "username";
        public static final String SECRET = "secret";
        public static final String PASSWORD = "password";
        public static final String INFO = "info";
        public static final String ACTIVITY = "activity";
        public static final String HOSTNAME = "hostname";
        public static final String PORT = "port";
        public static final String ID = "id";
        public static final String LOAD = "load";
    }
}
