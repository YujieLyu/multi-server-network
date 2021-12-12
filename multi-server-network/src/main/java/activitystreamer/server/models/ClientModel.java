package activitystreamer.server.models;

public class ClientModel {
    public String username;
    public String secret;
    public boolean isLogin;

    public ClientModel(String username, String secret) {
        this.username = username;
        this.secret = secret;
        this.isLogin = false;
    }
}
