package activitystreamer.server.models;

import org.json.simple.JSONObject;

public class ProcessResult {
    JSONObject output;
    boolean shouldConClose;

    public ProcessResult(JSONObject output, boolean shouldConClose) {
        this.output = output;
        this.shouldConClose = shouldConClose;
    }

    public JSONObject getOutput() {
        return output;
    }

    public boolean getShouldConClose() {
        return shouldConClose;
    }
}
