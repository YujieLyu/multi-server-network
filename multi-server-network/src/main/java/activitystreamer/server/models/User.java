package activitystreamer.server.models;

public class User {
    public String username;
    public String secret;
    public boolean isLogin;

    public User(String username, String secret) {
        this.username = username;
        this.secret = secret;
        this.isLogin = false;
    }
}
