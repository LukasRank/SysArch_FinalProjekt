package de.htwg.sysarch.mqtt;

import com.google.gson.Gson;

/**
 * Tiny JSON facade for the MQTT message contract, shared by both sides so the
 * status/event/command DTOs serialize identically on the control system and the HMI.
 */
public final class JsonCodec {

    private static final Gson GSON = new Gson();

    private JsonCodec() {
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }
}
