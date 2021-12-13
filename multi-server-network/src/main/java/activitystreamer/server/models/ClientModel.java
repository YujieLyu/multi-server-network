package activitystreamer.server.models;

public class ClientModel {
    public String username;
    public String password;
    public boolean isLogin;

    public ClientModel(String username, String secret) {
        this.username = username;
        this.password = secret;
        this.isLogin = false;
    }
}
