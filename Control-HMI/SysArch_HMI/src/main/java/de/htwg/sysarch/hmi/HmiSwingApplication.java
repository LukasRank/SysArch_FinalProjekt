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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Graphical reference HMI for the elevator — the partner group's side of the system,
 * a richer alternative to the console {@link HmiApplication}.
 *
 * <p><strong>Still strictly separated from the control system:</strong> it depends only
 * on the shared {@code de.htwg.sysarch.mqtt} contract and the MQTT client — never on any
 * {@code de.htwg.sysarch.elevator.*} class. It subscribes to the retained state + event
 * topics (the {@link ShaftView} animation, status read-outs and event log) and publishes
 * commands when the operator clicks a button.
 *
 * <pre>mvn exec:java -Dexec.mainClass=de.htwg.sysarch.hmi.HmiSwingApplication</pre>
 * Broker/topic via {@code -Dmqtt.host -Dmqtt.port -Dmqtt.baseTopic}.
 */
public final class HmiSwingApplication implements MqttCallback {

    private static final int LEVELS = 4;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String serverUri;
    private final MqttTopics topics;
    private final String username;
    private final String password;
    private MqttClient client;

    // UI
    private final ShaftView shaft = new ShaftView();
    private final JLabel connection = new JLabel("○  connecting…");
    private final JLabel valLevel = value("–");
    private final JLabel valDirection = value("–");
    private final JLabel valPhase = value("–");
    private final JLabel valDoor = value("–");
    private final JLabel valVelocity = value("–");
    private final JLabel banner = new JLabel(" ", JLabel.CENTER);
    private final JTextArea logArea = new JTextArea(10, 28);
    private final JButton[] cabinButtons = new JButton[LEVELS + 1];
    private final JButton[] upButtons = new JButton[LEVELS + 1];
    private final JButton[] downButtons = new JButton[LEVELS + 1];

    public HmiSwingApplication(String serverUri, String baseTopic, String username, String password) {
        this.serverUri = serverUri;
        this.topics = new MqttTopics(baseTopic);
        this.username = username;
        this.password = password;
    }

    public static void main(String[] args) {
        String host = resolve("mqtt.host", "MQTT_HOST", "localhost");
        int port = Integer.parseInt(resolve("mqtt.port", "MQTT_PORT", "1883"));
        String base = resolve("mqtt.baseTopic", "MQTT_BASE_TOPIC", "/SysArch/E");
        String user = resolve("mqtt.username", "MQTT_USERNAME", "");
        String pass = resolve("mqtt.password", "MQTT_PASSWORD", "");
        HmiSwingApplication app = new HmiSwingApplication("tcp://" + host + ":" + port, base, user, pass);
        SwingUtilities.invokeLater(app::buildAndShow);
        app.connect();
    }

    /** Config override precedence: {@code -Dkey=...} system property &gt; env var &gt; default. */
    private static String resolve(String key, String envKey, String def) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isEmpty()) {
            return sys;
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return def;
    }

    // --------------------------------------------------------------- UI build

    private void buildAndShow() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // default L&F is fine
        }

        JFrame frame = new JFrame("Elevator HMI — " + topics.base());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.setBackground(Color.WHITE);

        root.add(shaft, BorderLayout.WEST);
        root.add(buildRightPanel(), BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.pack();
        frame.setMinimumSize(new Dimension(760, 620));
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private JPanel buildRightPanel() {
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBackground(Color.WHITE);

        connection.setFont(connection.getFont().deriveFont(Font.BOLD, 13f));
        connection.setForeground(new Color(0x9E9E9E));
        connection.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(connection);
        right.add(Box.createVerticalStrut(8));

        banner.setOpaque(true);
        banner.setBackground(Color.WHITE);
        banner.setForeground(Color.WHITE);
        banner.setFont(banner.getFont().deriveFont(Font.BOLD, 14f));
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(banner);
        right.add(Box.createVerticalStrut(8));

        right.add(section("Status"));
        right.add(buildStatusGrid());
        right.add(Box.createVerticalStrut(14));

        right.add(section("Calls"));
        right.add(buildCallGrid());
        right.add(Box.createVerticalStrut(14));

        right.add(section("Operation"));
        right.add(buildOperationRow());
        right.add(Box.createVerticalStrut(14));

        right.add(section("Events"));
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        right.add(scroll);

        return right;
    }

    private JPanel buildStatusGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 6));
        grid.setBackground(Color.WHITE);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        grid.add(key("Level"));
        grid.add(valLevel);
        grid.add(key("Direction"));
        grid.add(valDirection);
        grid.add(key("Phase"));
        grid.add(valPhase);
        grid.add(key("Door"));
        grid.add(valDoor);
        grid.add(key("Velocity"));
        grid.add(valVelocity);
        return grid;
    }

    /** One row per floor (4 at top → 1 at bottom): hall up/down + cabin call. */
    private JPanel buildCallGrid() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        for (int l = LEVELS; l >= 1; l--) {
            final int level = l;
            c.gridy = row;

            c.gridx = 0;
            JLabel floor = new JLabel("Floor " + l);
            floor.setFont(floor.getFont().deriveFont(Font.BOLD));
            panel.add(floor, c);

            c.gridx = 1;
            JButton up = new JButton("▲ up");
            up.setEnabled(l < LEVELS);
            up.addActionListener(e -> publish(CommandMessage.hall(level, "UP")));
            upButtons[l] = up;
            panel.add(up, c);

            c.gridx = 2;
            JButton down = new JButton("▼ down");
            down.setEnabled(l > 1);
            down.addActionListener(e -> publish(CommandMessage.hall(level, "DOWN")));
            downButtons[l] = down;
            panel.add(down, c);

            c.gridx = 3;
            JButton cabin = new JButton("Cabin → " + l);
            cabin.addActionListener(e -> publish(CommandMessage.cabin(level)));
            cabinButtons[l] = cabin;
            panel.add(cabin, c);

            row++;
        }
        return panel;
    }

    private JPanel buildOperationRow() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 8, 0));
        panel.setBackground(Color.WHITE);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        JButton emergency = new JButton("⚠ EMERGENCY");
        emergency.setBackground(new Color(0xD32F2F));
        emergency.setForeground(Color.RED.darker());
        emergency.setFont(emergency.getFont().deriveFont(Font.BOLD));
        emergency.addActionListener(e -> publish(CommandMessage.emergencyEngage()));

        JButton clear = new JButton("Reset emergency");
        clear.addActionListener(e -> publish(CommandMessage.emergencyClear()));

        JButton reset = new JButton("Reset sim");
        reset.addActionListener(e -> publish(CommandMessage.reset()));

        panel.add(emergency);
        panel.add(clear);
        panel.add(reset);
        return panel;
    }

    // --------------------------------------------------------------- MQTT

    private void connect() {
        try {
            client = new MqttClient(serverUri, "elevator-hmi-gui-" + System.nanoTime(), new MemoryPersistence());
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
            client.subscribe(topics.status());
            client.subscribe(topics.event());
            setConnection(true);
            log("connected to " + serverUri + " (" + topics.base() + ")");
        } catch (MqttException e) {
            setConnection(false);
            log("MQTT connect failed: " + e.getMessage() + " — is the broker running?");
        }
    }

    private void publish(CommandMessage cmd) {
        try {
            if (client != null && client.isConnected()) {
                MqttMessage message = new MqttMessage(JsonCodec.toJson(cmd).getBytes(StandardCharsets.UTF_8));
                message.setQos(0);
                client.publish(topics.command(), message);
            } else {
                log("not connected — command dropped");
            }
        } catch (MqttException e) {
            log("publish failed: " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        setConnection(false);
        log("connection lost: " + cause.getMessage() + " (reconnecting…)");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        if (topic.equals(topics.status())) {
            StatusMessage s = JsonCodec.fromJson(payload, StatusMessage.class);
            SwingUtilities.invokeLater(() -> applyStatus(s));
        } else if (topic.equals(topics.event())) {
            EventMessage e = JsonCodec.fromJson(payload, EventMessage.class);
            log("EVENT " + e.type() + " @E" + e.level()
                    + (e.detail() == null || e.detail().isEmpty() ? "" : " (" + e.detail() + ")"));
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no-op (QoS 0)
    }

    // --------------------------------------------------------------- view updates

    private void applyStatus(StatusMessage s) {
        valLevel.setText("E" + s.level());
        valDirection.setText(s.direction());
        valPhase.setText(s.phase());
        valDoor.setText(s.door());
        valVelocity.setText(s.velocity() + " cm/s");

        Set<Integer> cabin = set(s.cabinCalls());
        Set<Integer> up = set(s.hallUpCalls());
        Set<Integer> down = set(s.hallDownCalls());

        shaft.update(s.level(), s.direction(), s.phase(), s.door(), s.velocity(),
                s.emergencyActive(), cabin, up, down);

        for (int l = 1; l <= LEVELS; l++) {
            highlight(cabinButtons[l], cabin.contains(l), new Color(0x1565C0));
            highlight(upButtons[l], up.contains(l), new Color(0x2E7D32));
            highlight(downButtons[l], down.contains(l), new Color(0xEF6C00));
        }

        if (s.emergencyActive()) {
            setBanner("EMERGENCY STOP ACTIVE", new Color(0xD32F2F));
        } else if (s.motorError()) {
            setBanner("MOTOR ERROR", new Color(0xC62828));
        } else {
            setBanner(" ", Color.WHITE);
        }
    }

    private static void highlight(JButton button, boolean on, Color color) {
        if (button == null) {
            return;
        }
        button.setBackground(on ? color : null);
        button.setForeground(on ? Color.WHITE : null);
        button.setOpaque(on);
        button.setContentAreaFilled(!on || button.isContentAreaFilled());
    }

    private void setBanner(String text, Color bg) {
        banner.setText(text);
        banner.setBackground(bg);
    }

    private void setConnection(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            connection.setText(connected ? "●  connected" : "○  disconnected");
            connection.setForeground(connected ? new Color(0x2E7D32) : new Color(0xC62828));
        });
    }

    private void log(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(LocalTime.now().format(CLOCK) + "  " + line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // --------------------------------------------------------------- helpers

    private static Set<Integer> set(List<Integer> list) {
        return list == null ? Set.of() : new LinkedHashSet<>(list);
    }

    private static JLabel section(String title) {
        JLabel l = new JLabel(title.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(new Color(0x90A4AE));
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel key(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(0x607D8B));
        return l;
    }

    private static JLabel value(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 14f));
        l.setForeground(new Color(0x263238));
        return l;
    }
}
