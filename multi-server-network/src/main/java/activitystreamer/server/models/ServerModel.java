package activitystreamer.server.models;

public class ServerModel {
    public String id;
    public String load;
    public String hostname;
    public String port;

    public ServerModel(String id, String load, String hostname, String port) {
        this.port = port;
        this.hostname = hostname;
        this.load = load;
        this.id = id;
    }

    public void setLoad(String load) {
        this.load = load;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setPort(String port) {
        this.port = port;
    }
}
