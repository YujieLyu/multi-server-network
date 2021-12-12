package activitystreamer.util;

import activitystreamer.client.ClientControl;
import jdk.nashorn.internal.runtime.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.UUID;

public class Helper {
    private static final Logger log = LogManager.getLogger();

    public static JSONObject JsonParser(String jsonStr) {
        JSONParser parser = new JSONParser();
        try {
            return (JSONObject) parser.parse(jsonStr);
        } catch (ParseException e) {
            log.error("invalid JSON object entered into input text field, data not sent");
            return null;
        }
    }

    public static String generateString() {
        String uuid = UUID.randomUUID().toString();
        return "uuid = " + uuid;
    }
}
