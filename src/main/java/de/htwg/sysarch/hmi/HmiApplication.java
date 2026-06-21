package de.htwg.sysarch.hmi;

import de.htwg.sysarch.mqtt.CommandMessage;
import de.htwg.sysarch.mqtt.EventMessage;
import de.htwg.sysarch.mqtt.JsonCodec;
import de.htwg.sysarch.mqtt.MqttTopics;
import de.htwg.sysarch.mqtt.StatusMessage;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Standalone reference HMI for the elevator — the partner group's side of the system.
 *
 * <p><strong>Deliberately separate from the control system:</strong> it depends only on
 * the shared {@code de.htwg.sysarch.mqtt} contract and the MQTT client, and never on any
 * {@code de.htwg.sysarch.elevator.*} class. It subscribes to the retained state + event
 * topics (display) and publishes commands on the command topic (operate). Swap this
 * console UI for a GUI/web HMI without touching the control system.
 *
 * <p>Run it next to a control system started with {@code --mqtt}:
 * <pre>mvn exec:java -Dexec.mainClass=de.htwg.sysarch.hmi.HmiApplication</pre>
 * Broker/topic via system properties {@code -Dmqtt.host -Dmqtt.port -Dmqtt.baseTopic}.
 */
public final class HmiApplication implements MqttCallback {

    private final String serverUri;
    private final MqttTopics topics;
    private MqttClient client;

    public HmiApplication(String serverUri, String baseTopic) {
        this.serverUri = serverUri;
        this.topics = new MqttTopics(baseTopic);
    }

    public static void main(String[] args) throws Exception {
        String host = System.getProperty("mqtt.host", "localhost");
        int port = Integer.parseInt(System.getProperty("mqtt.port", "1883"));
        String base = System.getProperty("mqtt.baseTopic", "elevator/e");
        new HmiApplication("tcp://" + host + ":" + port, base).run();
    }

    private void run() throws Exception {
        client = new MqttClient(serverUri, "elevator-hmi-" + System.nanoTime(), new MemoryPersistence());
        client.setCallback(this);
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        opts.setConnectionTimeout(5);
        client.connect(opts);
        client.subscribe(topics.status());
        client.subscribe(topics.event());

        System.out.println("HMI connected to " + serverUri + " (topics under " + topics.base() + ")");
        printHelp();
        readCommands();
    }

    private void readCommands() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                CommandMessage cmd = parse(line.trim());
                if (cmd != null) {
                    publish(cmd);
                }
            }
        }
    }

    private CommandMessage parse(String line) {
        if (line.isEmpty()) {
            return null;
        }
        String[] p = line.split("\\s+");
        try {
            return switch (p[0].toLowerCase()) {
                case "c" -> CommandMessage.cabin(Integer.parseInt(p[1]));
                case "u" -> CommandMessage.hall(Integer.parseInt(p[1]), "UP");
                case "d" -> CommandMessage.hall(Integer.parseInt(p[1]), "DOWN");
                case "e" -> CommandMessage.emergencyEngage();
                case "r" -> CommandMessage.emergencyClear();
                case "x" -> CommandMessage.reset();
                case "h", "?" -> {
                    printHelp();
                    yield null;
                }
                case "q" -> {
                    try {
                        client.disconnect();
                    } catch (MqttException ignored) {
                        // shutting down anyway
                    }
                    System.exit(0);
                    yield null;
                }
                default -> {
                    System.out.println("unknown command: '" + line + "'  (h for help)");
                    yield null;
                }
            };
        } catch (RuntimeException ex) {
            System.out.println("invalid command: '" + line + "'  (" + ex.getMessage() + ")");
            return null;
        }
    }

    private void publish(CommandMessage cmd) throws MqttException {
        MqttMessage message = new MqttMessage(JsonCodec.toJson(cmd).getBytes(StandardCharsets.UTF_8));
        message.setQos(0);
        client.publish(topics.command(), message);
    }

    private void printHelp() {
        System.out.println("""
                HMI commands (published to the control system over MQTT):
                  c <1-4>  cabin call to level
                  u <1-4>  hall call UP at level
                  d <1-4>  hall call DOWN at level
                  e        emergency stop
                  r        reset emergency
                  x        reset simulation
                  h        help
                  q        quit""");
    }

    // ---------------------------------------------------------- MqttCallback (control → HMI)

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("[HMI] connection lost: " + cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        if (topic.equals(topics.status())) {
            StatusMessage s = JsonCodec.fromJson(payload, StatusMessage.class);
            System.out.printf(
                    "[STATUS] level=%d dir=%s phase=%s door=%s v=%dcm/s cabin=%s up=%s down=%s%s%n",
                    s.level(), s.direction(), s.phase(), s.door(), s.velocity(),
                    s.cabinCalls(), s.hallUpCalls(), s.hallDownCalls(),
                    s.emergencyActive() ? "  [EMERGENCY]" : (s.motorError() ? "  [ERROR]" : ""));
        } else if (topic.equals(topics.event())) {
            EventMessage e = JsonCodec.fromJson(payload, EventMessage.class);
            System.out.printf("[EVENT]  %s @level%d%s%n",
                    e.type(), e.level(), e.detail().isEmpty() ? "" : " (" + e.detail() + ")");
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no-op (QoS 0)
    }
}
