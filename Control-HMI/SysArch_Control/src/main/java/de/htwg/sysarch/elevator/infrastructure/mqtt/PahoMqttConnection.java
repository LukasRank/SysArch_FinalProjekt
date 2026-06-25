package de.htwg.sysarch.elevator.infrastructure.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * {@link MqttConnection} implementation over the Eclipse Paho synchronous client.
 * Auto-reconnect is enabled so a broker hiccup does not take the control link down
 * permanently; publish failures are surfaced to the caller (the gateway logs and
 * continues, the control loop is never blocked).
 */
public final class PahoMqttConnection implements MqttConnection, MqttCallback {

    private static final Logger LOG = Logger.getLogger(PahoMqttConnection.class.getName());

    private final String serverUri;
    private final String clientId;
    private final String username;
    private final String password;
    private final Map<String, MessageHandler> handlers = new ConcurrentHashMap<>();
    private MqttClient client;

    /** Anonymous connection (e.g. a local dev broker). */
    public PahoMqttConnection(String serverUri, String clientId) {
        this(serverUri, clientId, null, null);
    }

    /** Authenticated connection; pass {@code null}/empty credentials for anonymous brokers. */
    public PahoMqttConnection(String serverUri, String clientId, String username, String password) {
        this.serverUri = serverUri;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
    }

    @Override
    public void connect() throws MqttException {
        client = new MqttClient(serverUri, clientId, new MemoryPersistence());
        client.setCallback(this);
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        opts.setConnectionTimeout(5);
        if (username != null && !username.isEmpty()) {
            opts.setUserName(username);
            opts.setPassword(password == null ? new char[0] : password.toCharArray());
        }
        client.connect(opts);
        LOG.info(() -> "MQTT connected to " + serverUri + " as " + clientId);
    }

    @Override
    public void publish(String topic, String payload, boolean retained) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(0);
        message.setRetained(retained);
        client.publish(topic, message);
    }

    @Override
    public void subscribe(String topicFilter, MessageHandler handler) throws MqttException {
        handlers.put(topicFilter, handler);
        client.subscribe(topicFilter);
        LOG.info(() -> "MQTT subscribed to " + topicFilter);
    }

    @Override
    public void close() {
        try {
            if (client != null) {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            }
        } catch (MqttException e) {
            LOG.warning("MQTT close failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------- MqttCallback

    @Override
    public void connectionLost(Throwable cause) {
        LOG.warning("MQTT connection lost: " + cause.getMessage() + " (auto-reconnect enabled)");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        MessageHandler handler = handlers.get(topic);
        if (handler == null) {
            for (Map.Entry<String, MessageHandler> e : handlers.entrySet()) {
                if (matches(e.getKey(), topic)) {
                    handler = e.getValue();
                    break;
                }
            }
        }
        if (handler != null) {
            try {
                handler.handle(topic, payload);
            } catch (RuntimeException ex) {
                LOG.warning("MQTT message handler failed: " + ex.getMessage());
            }
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no-op (QoS 0)
    }

    /** Minimal MQTT topic match supporting the {@code +} (single level) and {@code #} wildcards. */
    private static boolean matches(String filter, String topic) {
        String[] f = filter.split("/", -1);
        String[] t = topic.split("/", -1);
        for (int i = 0; i < f.length; i++) {
            if (f[i].equals("#")) {
                return true;
            }
            if (i >= t.length) {
                return false;
            }
            if (!f[i].equals("+") && !f[i].equals(t[i])) {
                return false;
            }
        }
        return f.length == t.length;
    }
}
