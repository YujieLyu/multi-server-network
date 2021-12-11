package activitystreamer.server;

public class User {
    String username;
    String secret;
    boolean isLogin;

    public User(String username, String secret) {
        this.username = username;
        this.secret = secret;
        this.isLogin = false;
    }
}
