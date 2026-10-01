package fr.universalserverloader.gui;

import fr.universalserverloader.Main;
import fr.universalserverloader.config.ConfigManager;
import fr.universalserverloader.config.LoaderConfig;
import fr.universalserverloader.launcher.InstanceControl;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public final class ServerDashboard extends JFrame {
    private static final Color BACKGROUND = new Color(9, 13, 18);
    private static final Color PANEL = new Color(16, 23, 32);
    private static final Color PANEL_ALT = new Color(20, 29, 40);
    private static final Color TEXT = new Color(232, 239, 247);
    private static final Color MUTED = new Color(139, 155, 174);
    private static final Color BORDER = new Color(39, 52, 68);
    private static final Color GREEN = new Color(34, 197, 94);
    private static final Color ORANGE = new Color(245, 158, 11);
    private static final Color RED = new Color(239, 68, 68);
    private static final Color BLUE = new Color(59, 130, 246);
    private static final Color PURPLE = new Color(139, 92, 246);
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*[ -/]*[@-~]");

    private final Path root;
    private LoaderConfig config;
    private final JTextArea console = new JTextArea();
    private final JTextArea specs = new JTextArea();
    private final JTextField command = new JTextField();
    private final JLabel state = new JLabel("ARRÊTÉ");
    private final JLabel uptime = new JLabel("Uptime : 00:00:00");
    private final JLabel serverAddress = new JLabel("détection...");
    private final JCheckBox eula = new JCheckBox("J’ai lu et j’accepte l’EULA Minecraft");
    private final JTextField memory = new JTextField("3");
    private final JButton applyMemory = button("Appliquer la RAM", BLUE);
    private final JButton start = button("Démarrer", GREEN);
    private final JButton stop = button("Arrêter", new Color(220, 80, 80));
    private final JButton restart = button("Redémarrer", ORANGE);
    private final MetricGraphPanel ramGraph = new MetricGraphPanel("RAM PAPER", GREEN);
    private final MetricGraphPanel cpuGraph = new MetricGraphPanel("CPU PAPER", BLUE);
    private volatile Process process;
    private volatile BufferedWriter processInput;
    private long startedAt;
    private final FileChannel guiLockChannel;
    private final FileLock guiLock;
    private volatile boolean metricsMonitorRunning = true;
    private volatile long metricsPid = -1L;
    private volatile JMXConnector metricsConnector;
    private volatile MBeanServerConnection metricsConnection;

    public static void open(final Path root, final LoaderConfig config) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                    UIManager.put("Button.font", new Font("Segoe UI", Font.BOLD, 13));
                    UIManager.put("Label.font", new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                    UIManager.put("CheckBox.font", new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                    UIManager.put("TextField.font", new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                    new ServerDashboard(root, config).setVisible(true);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "Impossible d’ouvrir l’interface : " + e.getMessage(),
                            "UniversalServerLoader", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private ServerDashboard(Path root, LoaderConfig config) throws Exception {
        super("UniversalServerLoader — Paper 1.21.11");
        this.root = root;
        this.config = config;
        Files.createDirectories(root.resolve("runtime"));
        guiLockChannel = FileChannel.open(root.resolve("runtime/dashboard.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = guiLockChannel.tryLock(); }
        catch (OverlappingFileLockException e) { acquired = null; }
        if (acquired == null) {
            guiLockChannel.close();
            throw new IllegalStateException("Le tableau de bord est déjà ouvert.");
        }
        guiLock = acquired;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 700));
        setSize(1320, 820);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BACKGROUND);
        getContentPane().setLayout(new BorderLayout(12, 12));
        ((javax.swing.JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        buildHeader();
        buildControls();
        buildCenter();
        eula.setSelected(config.agreeToEula);
        memory.setText(Integer.toString(memoryGib(config.maxMemory)));
        refreshSpecs();
        ramGraph.addSample(0, "serveur arrêté");
        cpuGraph.addSample(0, "serveur arrêté");
        startMetricsMonitor();
        append("UniversalServerLoader " + Main.VERSION);
        append("Dossier serveur : " + root);
        append("Le monde sera généré automatiquement avec une graine aléatoire au premier démarrage complet.");
        if (Files.exists(root.resolve("eula.txt")) && !config.agreeToEula)
            append("EULA non acceptée : cochez la case uniquement après l’avoir lue.");
        new Timer(1000, new ActionListener() {
            public void actionPerformed(ActionEvent event) { refreshRuntimeLabels(); }
        }).start();
        new Timer(15000, new ActionListener() {
            public void actionPerformed(ActionEvent event) { refreshSpecs(); refreshServerAddress(); }
        }).start();
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event) { closeWindow(); }
            public void windowClosed(WindowEvent event) { releaseGuiLock(); }
        });
    }

    private void buildHeader() {
        JPanel header = panel(new BorderLayout(20, 0));
        header.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 12, 1),
                BorderFactory.createEmptyBorder(13, 16, 13, 16)));
        JPanel branding = panel(new BorderLayout(0, 3));
        JLabel title = new JLabel("UNIVERSAL SERVER");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22)); title.setForeground(TEXT);
        JLabel subtitle = new JLabel("Paper 1.21.11   /   panneau de contrôle local");
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); subtitle.setForeground(MUTED);
        branding.add(title, BorderLayout.NORTH); branding.add(subtitle, BorderLayout.SOUTH);
        JPanel indicators = panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        styleMetric(state, "ÉTAT"); styleMetric(uptime, "UPTIME");
        state.setForeground(ORANGE);
        indicators.add(metricCard("État", state)); indicators.add(metricCard("Uptime", uptime));
        header.add(branding, BorderLayout.WEST); header.add(indicators, BorderLayout.EAST);
        getContentPane().add(header, BorderLayout.NORTH);
    }

    private void buildCenter() {
        console.setEditable(false); console.setBackground(new Color(5, 9, 14)); console.setForeground(new Color(210, 225, 216));
        console.setCaretColor(Color.WHITE); console.setFont(new Font("Consolas", Font.PLAIN, 13));
        console.setLineWrap(true); console.setWrapStyleWord(false);
        console.setMargin(new Insets(12, 12, 12, 12));
        specs.setEditable(false); specs.setBackground(PANEL_ALT); specs.setForeground(TEXT); specs.setFont(new Font("Consolas", Font.PLAIN, 13));
        specs.setLineWrap(true); specs.setWrapStyleWord(false);
        specs.setMargin(new Insets(12, 12, 12, 12));
        JScrollPane consoleScroll = new JScrollPane(console); consoleScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        JScrollPane specsScroll = new JScrollPane(specs); specsScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        styleScrollPane(consoleScroll, new Color(5, 9, 14)); styleScrollPane(specsScroll, PANEL_ALT);
        JPanel consolePanel = section("CONSOLE", "Sortie Paper en direct", consoleScroll);

        JPanel charts = new JPanel(new GridLayout(2, 1, 0, 10)); charts.setOpaque(false);
        charts.add(ramGraph); charts.add(cpuGraph);
        JPanel monitoring = panel(new BorderLayout(0, 10));
        monitoring.add(charts, BorderLayout.NORTH); monitoring.add(specsScroll, BorderLayout.CENTER);
        JPanel specsPanel = section("MONITORING", "RAM, CPU, serveur et monde", monitoring);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, consolePanel, specsPanel);
        split.setUI(new BasicSplitPaneUI() {
            public BasicSplitPaneDivider createDefaultDivider() {
                BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this);
                divider.setBackground(BACKGROUND);
                divider.setBorder(null);
                return divider;
            }
        });
        split.setResizeWeight(0.74); split.setDividerLocation(720); split.setDividerSize(10); split.setBorder(null);
        split.setBackground(BACKGROUND);
        getContentPane().add(split, BorderLayout.CENTER);
    }

    private void buildControls() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(PANEL);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(215, 0));
        sidebar.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 12, 1),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        JLabel actionsTitle = new JLabel("CONTRÔLES"); actionsTitle.setForeground(MUTED);
        actionsTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        actionsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel buttons = panel(new GridLayout(0, 1, 0, 6));
        JButton folder = button("Ouvrir le dossier", new Color(85, 98, 115));
        buttons.add(start); buttons.add(stop); buttons.add(restart); buttons.add(folder);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setPreferredSize(new Dimension(187, 154));
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 154));
        start.setPreferredSize(new Dimension(176, 34)); stop.setPreferredSize(new Dimension(176, 34));
        restart.setPreferredSize(new Dimension(176, 34)); folder.setPreferredSize(new Dimension(176, 34));
        eula.setOpaque(false); eula.setForeground(TEXT);
        stop.setEnabled(false); restart.setEnabled(false);
        start.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { startServer(); }});
        stop.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { sendCommand("stop"); }});
        restart.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { sendCommand("restart"); }});
        folder.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { openFolder(); }});
        applyMemory.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { applyMemory(true); }});
        JPanel input = panel(new BorderLayout(10, 0));
        input.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 12, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        command.setBackground(new Color(7, 12, 18)); command.setForeground(TEXT); command.setCaretColor(TEXT);
        command.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 10, 1),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        JButton send = button("Envoyer", GREEN);
        ActionListener submit = new ActionListener() { public void actionPerformed(ActionEvent e) { submitCommand(); }};
        command.addActionListener(submit); send.addActionListener(submit);
        JLabel commandLabel = new JLabel("COMMANDE"); commandLabel.setForeground(MUTED); commandLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        input.add(commandLabel, BorderLayout.WEST); input.add(command, BorderLayout.CENTER); input.add(send, BorderLayout.EAST);

        JPanel settings = panel(new BorderLayout(0, 4));
        settings.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.setPreferredSize(new Dimension(187, 50));
        settings.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        JPanel ram = panel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel ramLabel = new JLabel("RAM PAPER"); ramLabel.setForeground(MUTED); ramLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        JLabel unit = new JLabel("Gio"); unit.setForeground(MUTED);
        memory.setPreferredSize(new Dimension(48, 32));
        memory.setHorizontalAlignment(JTextField.CENTER);
        memory.setBackground(new Color(7, 12, 18)); memory.setForeground(TEXT); memory.setCaretColor(TEXT);
        memory.setBorder(new RoundedBorder(BORDER, 10, 1));
        eula.setText("<html>J’ai lu et j’accepte<br>l’EULA Minecraft</html>");
        ram.add(memory); ram.add(unit);
        applyMemory.setText("Appliquer");
        applyMemory.setPreferredSize(new Dimension(84, 32));
        eula.setOpaque(false); eula.setForeground(TEXT);
        JPanel ramLine = panel(new BorderLayout(6, 0));
        ramLine.add(ram, BorderLayout.WEST); ramLine.add(applyMemory, BorderLayout.CENTER);
        settings.add(ramLabel, BorderLayout.NORTH); settings.add(ramLine, BorderLayout.CENTER);

        JPanel addressCard = panel(new BorderLayout(0, 4));
        addressCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressCard.setPreferredSize(new Dimension(187, 83));
        addressCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 83));
        addressCard.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 10, 1),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)));
        JLabel addressTitle = new JLabel("IP DU SERVEUR");
        addressTitle.setForeground(MUTED); addressTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        serverAddress.setForeground(TEXT); serverAddress.setFont(new Font("Consolas", Font.BOLD, 13));
        JButton copyAddress = button("Copier l’adresse", new Color(85, 98, 115));
        copyAddress.setPreferredSize(new Dimension(176, 32));
        copyAddress.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { copyServerAddress(); }});
        addressCard.add(addressTitle, BorderLayout.NORTH); addressCard.add(serverAddress, BorderLayout.CENTER);
        addressCard.add(copyAddress, BorderLayout.SOUTH);
        refreshServerAddress();

        eula.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(actionsTitle);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(buttons);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(settings);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(addressCard);
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(eula);
        getContentPane().add(sidebar, BorderLayout.WEST);
        getContentPane().add(input, BorderLayout.SOUTH);
    }

    private void refreshServerAddress() {
        serverAddress.setText(findLanAddress() + ":" + serverPort());
        serverAddress.setToolTipText("Adresse à saisir dans Minecraft sur ce PC ou le même réseau local");
    }

    private void copyServerAddress() {
        try {
            String value = serverAddress.getText();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
            append("Adresse du serveur copiée : " + value);
        } catch (Exception e) {
            append("Impossible de copier l’adresse : " + e.getMessage());
        }
    }

    private int serverPort() {
        Path propertiesFile = root.resolve("server.properties");
        if (!Files.isRegularFile(propertiesFile)) return 25565;
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return Integer.parseInt(properties.getProperty("server-port", "25565").trim());
        } catch (Exception ignored) { return 25565; }
    }

    private static String findLanAddress() {
        String fallback = "127.0.0.1";
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                String description = (network.getName() + " " + network.getDisplayName()).toLowerCase();
                if (description.contains("virtualbox") || description.contains("host-only")
                        || description.contains("vmware") || description.contains("hyper-v")
                        || description.contains("bluetooth") || description.contains("docker")
                        || description.contains("wsl")) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        if (address.isSiteLocalAddress()) return address.getHostAddress();
                        fallback = address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    private void startServer() {
        if (process != null && process.isAlive()) return;
        try {
            applyMemory(false);
            if (!eula.isSelected()) {
                JOptionPane.showMessageDialog(this, "Vous devez lire et accepter explicitement l’EULA Minecraft avant le premier démarrage.",
                        "EULA requise", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!config.agreeToEula) {
                int answer = JOptionPane.showConfirmDialog(this,
                        "Confirmez-vous avoir lu et accepter l’EULA Minecraft (https://aka.ms/MinecraftEULA) ?",
                        "Confirmation EULA", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) { eula.setSelected(false); return; }
                new ConfigManager().setEulaConsent(root.resolve("config/loader.json"), true);
                config = new ConfigManager().load(root.resolve("config/loader.json"));
            }
            Path jar = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(jar)) throw new IllegalStateException("L’interface doit être lancée depuis universal-loader.jar");
            ProcessBuilder builder = new ProcessBuilder(javaExecutable(), "-jar", jar.toString(), "start");
            builder.directory(root.toFile()).redirectErrorStream(true);
            process = builder.start();
            processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            startedAt = System.currentTimeMillis();
            setRunningUi(true); setState("DÉMARRAGE", ORANGE);
            Thread reader = new Thread(new Runnable() { public void run() { readProcess(); }}, "dashboard-console-reader");
            reader.setDaemon(true); reader.start();
        } catch (Exception ex) {
            append("ERREUR DE DÉMARRAGE : " + ex.getMessage());
            setRunningUi(false); setState("ERREUR", Color.RED);
        }
    }

    private void applyMemory(boolean notify) {
        try {
            int gib = Integer.parseInt(memory.getText().trim());
            if (gib < 1 || gib > 12) throw new IllegalArgumentException("Choisissez entre 1 et 12 Gio.");
            String expected = gib + "G";
            if (!"512M".equalsIgnoreCase(config.minMemory) || !expected.equalsIgnoreCase(config.maxMemory)) {
                new ConfigManager().setMemoryGib(root.resolve("config/loader.json"), gib);
                config = new ConfigManager().load(root.resolve("config/loader.json"));
                append("Mémoire Paper optimisée : démarrage à 512 Mio, maximum " + gib + " Gio.");
                refreshSpecs();
            }
            if (notify) JOptionPane.showMessageDialog(this,
                    "Paper démarrera avec 512 Mio et pourra monter jusqu’à " + gib + " Gio selon la charge.",
                    "Mémoire enregistrée", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            append("Impossible d’enregistrer la mémoire : " + e.getMessage());
            if (notify) JOptionPane.showMessageDialog(this, e.getMessage(), "Erreur mémoire", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void readProcess() {
        int exit = -1;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) processLine(line);
            exit = process.waitFor();
        } catch (Exception e) { append("Lecture du processus interrompue : " + e.getMessage()); }
        final int code = exit;
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            processInput = null; setRunningUi(false);
            setState(code == 0 ? "ARRÊTÉ" : "ERREUR " + code, code == 0 ? ORANGE : Color.RED);
            append("Processus terminé avec le code " + code + "."); refreshSpecs();
        }});
    }

    private void processLine(String raw) {
        String line = ANSI.matcher(raw).replaceAll("");
        append(line);
        if (line.contains("Done (") || line.contains("For help, type")) {
            setState("EN LIGNE", GREEN);
        }
    }

    private void submitCommand() {
        String value = command.getText().trim();
        if (value.length() == 0) return;
        sendCommand(value); command.setText("");
    }

    private synchronized void sendCommand(String value) {
        try {
            if ("/plm".equalsIgnoreCase(value.trim())) value = "plm";
            if (processInput != null && process != null && process.isAlive()) {
                processInput.write(value); processInput.newLine(); processInput.flush();
                append("> " + value);
            } else if (InstanceControl.isRunning(root)) {
                InstanceControl.send(root, value); append("> " + value + " (instance externe)");
            } else append("Aucun serveur actif pour la commande : " + value);
        } catch (Exception e) { append("Commande impossible : " + e.getMessage()); }
    }

    private void refreshRuntimeLabels() {
        Process current = process;
        if (current != null && current.isAlive()) {
            long seconds = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000);
            uptime.setText(String.format("Uptime : %02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60));
        } else uptime.setText("Uptime : 00:00:00");
    }

    private void startMetricsMonitor() {
        Thread monitor = new Thread(new Runnable() {
            public void run() {
                while (metricsMonitorRunning) {
                    pollServerMetrics();
                    try { Thread.sleep(2000L); }
                    catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
                }
                closeMetricsConnection();
            }
        }, "paper-metrics-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    private void pollServerMetrics() {
        try {
            Path pidFile = root.resolve("runtime/server.pid");
            if (!Files.isRegularFile(pidFile)) { showStoppedMetrics(); closeMetricsConnection(); return; }
            long pid = Long.parseLong(new String(Files.readAllBytes(pidFile), StandardCharsets.UTF_8).trim());
            if (metricsConnection == null || metricsPid != pid) connectMetrics(pid);
            MemoryMXBean memoryBean = ManagementFactory.newPlatformMXBeanProxy(metricsConnection,
                    ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
            MemoryUsage heap = memoryBean.getHeapMemoryUsage();
            final long used = Math.max(0L, heap.getUsed());
            final long max = heap.getMax() > 0 ? heap.getMax() : heap.getCommitted();
            Object cpuValue = metricsConnection.getAttribute(
                    new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME), "ProcessCpuLoad");
            final double cpu = cpuValue instanceof Number ? ((Number) cpuValue).doubleValue() : -1.0;
            SwingUtilities.invokeLater(new Runnable() { public void run() {
                double memoryPercent = max > 0 ? used * 100.0 / max : 0.0;
                ramGraph.addSample(memoryPercent, String.format("%.2f / %.1f Gio", used / 1073741824.0, max / 1073741824.0));
                cpuGraph.addSample(cpu < 0 ? 0 : cpu * 100.0,
                        cpu < 0 ? "initialisation" : String.format("%.1f %%", cpu * 100.0));
            }});
        } catch (Exception e) {
            closeMetricsConnection();
            showStoppedMetrics();
        }
    }

    private void connectMetrics(long pid) throws Exception {
        closeMetricsConnection();
        Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        Object vm = vmClass.getMethod("attach", String.class).invoke(null, Long.toString(pid));
        String address;
        try { address = (String) vmClass.getMethod("startLocalManagementAgent").invoke(vm); }
        finally { vmClass.getMethod("detach").invoke(vm); }
        metricsConnector = JMXConnectorFactory.connect(new JMXServiceURL(address));
        metricsConnection = metricsConnector.getMBeanServerConnection();
        metricsPid = pid;
    }

    private void showStoppedMetrics() {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            ramGraph.addSample(0, "serveur arrêté");
            cpuGraph.addSample(0, "serveur arrêté");
        }});
    }

    private void closeMetricsConnection() {
        JMXConnector connector = metricsConnector;
        metricsConnector = null; metricsConnection = null; metricsPid = -1L;
        try { if (connector != null) connector.close(); } catch (Exception ignored) { }
    }

    private void refreshSpecs() {
        try {
            long freeDisk = Files.getFileStore(root).getUsableSpace() / 1024 / 1024 / 1024;
            long totalRam = physicalMemory() / 1024 / 1024 / 1024;
            boolean world = Files.isRegularFile(root.resolve("world/level.dat"));
            String seed = readSeed();
            StringBuilder text = new StringBuilder();
            text.append("SERVEUR\n");
            text.append("  Plateforme : Paper\n  Minecraft  : 1.21.11\n  Mémoire    : ").append(config.minMemory).append(" / ").append(config.maxMemory).append("\n");
            text.append("  Spark      : intégré à Paper\n  Plugins    : Chunky, LuckPerms\n\n");
            text.append("MACHINE\n  Java       : ").append(System.getProperty("java.version")).append("\n  OS         : ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append("\n");
            text.append("  CPU        : ").append(Runtime.getRuntime().availableProcessors()).append(" threads logiques\n  RAM totale : ").append(totalRam > 0 ? totalRam + " Gio" : "indisponible").append("\n  Disque libre: ").append(freeDisk).append(" Gio\n\n");
            text.append("MONDE\n  État       : ").append(world ? "généré" : "sera généré au démarrage").append("\n  Nom        : world\n  Graine     : ").append(seed).append("\n");
            specs.setText(text.toString());
        } catch (Exception e) { specs.setText("Diagnostic indisponible : " + e.getMessage()); }
    }

    private String readSeed() {
        Path properties = root.resolve("server.properties");
        if (!Files.isRegularFile(properties)) return "aléatoire (fichier à générer)";
        try {
            Properties values = new Properties();
            java.io.InputStream in = Files.newInputStream(properties); values.load(in); in.close();
            String seed = values.getProperty("level-seed", "").trim();
            return seed.length() == 0 ? "aléatoire" : seed;
        } catch (Exception e) { return "indisponible"; }
    }

    private static long physicalMemory() {
        try {
            Object bean = ManagementFactory.getOperatingSystemMXBean();
            Class<?> type = Class.forName("com.sun.management.OperatingSystemMXBean");
            java.lang.reflect.Method method = type.getMethod("getTotalPhysicalMemorySize");
            return ((Number) method.invoke(bean)).longValue();
        } catch (Exception ignored) { return 0L; }
    }

    private static int memoryGib(String value) {
        try {
            String normalized = value.trim().toUpperCase();
            if (normalized.endsWith("G"))
                return Math.max(1, Math.min(12, Integer.parseInt(normalized.substring(0, normalized.length() - 1))));
            if (normalized.endsWith("M"))
                return Math.max(1, Math.min(12, (Integer.parseInt(normalized.substring(0, normalized.length() - 1)) + 1023) / 1024));
        } catch (Exception ignored) { }
        return 3;
    }

    private void closeWindow() {
        if (process != null && process.isAlive()) {
            int answer = JOptionPane.showConfirmDialog(this, "Arrêter proprement le serveur avant de fermer ?",
                    "Serveur actif", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) return;
            if (answer == JOptionPane.YES_OPTION) sendCommand("stop");
        }
        metricsMonitorRunning = false;
        closeMetricsConnection();
        dispose();
    }

    private void releaseGuiLock() {
        try { if (guiLock.isValid()) guiLock.release(); } catch (Exception ignored) { }
        try { if (guiLockChannel.isOpen()) guiLockChannel.close(); } catch (Exception ignored) { }
    }

    private void openFolder() {
        try { java.awt.Desktop.getDesktop().open(root.toFile()); }
        catch (Exception e) { append("Impossible d’ouvrir le dossier : " + e.getMessage()); }
    }

    private void setRunningUi(final boolean running) {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            start.setEnabled(!running); stop.setEnabled(running); restart.setEnabled(running); eula.setEnabled(!running);
            memory.setEnabled(!running); applyMemory.setEnabled(!running);
        }});
    }

    private void setState(final String value, final Color color) {
        SwingUtilities.invokeLater(new Runnable() { public void run() { state.setText(value); state.setForeground(color); }});
    }

    private void append(final String line) {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            console.append(line + System.lineSeparator()); console.setCaretPosition(console.getDocument().getLength());
        }});
    }

    private static JPanel section(String titleText, String subtitleText, java.awt.Component content) {
        JPanel result = panel(new BorderLayout(0, 10));
        result.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 12, 1),
                BorderFactory.createEmptyBorder(13, 13, 13, 13)));
        JPanel heading = panel(new BorderLayout(0, 2));
        JLabel title = new JLabel(titleText); title.setForeground(TEXT); title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        JLabel subtitle = new JLabel(subtitleText); subtitle.setForeground(MUTED); subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        heading.add(title, BorderLayout.NORTH); heading.add(subtitle, BorderLayout.SOUTH);
        result.add(heading, BorderLayout.NORTH); result.add(content, BorderLayout.CENTER);
        return result;
    }

    private static JPanel metricCard(String titleText, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 2)); card.setBackground(PANEL_ALT);
        card.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 10, 1),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        JLabel title = new JLabel(titleText.toUpperCase()); title.setForeground(MUTED);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        card.add(title, BorderLayout.NORTH); card.add(value, BorderLayout.SOUTH); return card;
    }

    private static void styleMetric(JLabel label, String ignored) {
        label.setForeground(TEXT); label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
    }

    private static JPanel panel(java.awt.LayoutManager layout) { JPanel p = new JPanel(layout); p.setBackground(PANEL); return p; }
    private static JButton button(String label, Color ignored) { return new ModernButton(label); }

    private static void styleScrollPane(JScrollPane pane, Color background) {
        pane.setOpaque(true);
        pane.setBackground(background);
        pane.setViewportBorder(null);
        pane.getViewport().setOpaque(true);
        pane.getViewport().setBackground(background);
        pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setWheelScrollingEnabled(true);
        pane.getVerticalScrollBar().setUnitIncrement(18);
        pane.getHorizontalScrollBar().setUnitIncrement(18);
    }

    private static final class ModernButton extends JButton {
        private boolean hovered;

        ModernButton(String text) {
            super(text);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setForeground(TEXT); setFocusPainted(false); setOpaque(false);
            setContentAreaFilled(false); setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(Math.max(116, text.length() * 9 + 30), 40));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent event) { hovered = true; repaint(); }
                public void mouseExited(MouseEvent event) { hovered = false; repaint(); }
            });
        }

        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill;
            if (!isEnabled()) fill = new Color(25, 32, 42);
            else if (getModel().isPressed()) fill = new Color(43, 53, 65);
            else if (hovered) fill = new Color(58, 70, 85);
            else fill = new Color(45, 55, 68);
            g.setColor(fill); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.setColor(hovered && isEnabled() ? new Color(108, 125, 145) : new Color(65, 78, 94));
            g.drawRoundRect(0, 0, getWidth() - 2, getHeight() - 2, 12, 12);
            g.setFont(getFont());
            String text = getText();
            int x = (getWidth() - g.getFontMetrics().stringWidth(text)) / 2;
            int y = (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
            g.setColor(isEnabled() ? new Color(238, 242, 247) : new Color(96, 109, 125));
            g.drawString(text, x, y); g.dispose();
        }
    }

    private static final class RoundedBorder extends AbstractBorder {
        private final Color color; private final int radius; private final int thickness;
        RoundedBorder(Color color, int radius, int thickness) { this.color = color; this.radius = radius; this.thickness = thickness; }
        public Insets getBorderInsets(Component component) { return new Insets(7, 9, 7, 9); }
        public Insets getBorderInsets(Component component, Insets insets) {
            insets.top = insets.bottom = 7; insets.left = insets.right = 9; return insets;
        }
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color); g.setStroke(new BasicStroke(thickness));
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius); g.dispose();
        }
    }

    private static final class ModernScrollBarUI extends BasicScrollBarUI {
        protected void configureScrollBarColors() {
            thumbColor = new Color(61, 73, 88); trackColor = new Color(11, 17, 24);
        }
        protected JButton createDecreaseButton(int orientation) { return invisibleButton(); }
        protected JButton createIncreaseButton(int orientation) { return invisibleButton(); }
        private JButton invisibleButton() {
            JButton button = new JButton(); button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0)); button.setMaximumSize(new Dimension(0, 0)); return button;
        }
        protected void paintTrack(Graphics graphics, javax.swing.JComponent component, Rectangle bounds) {
            graphics.setColor(trackColor); graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        protected void paintThumb(Graphics graphics, javax.swing.JComponent component, Rectangle bounds) {
            if (!scrollbar.isEnabled() || bounds.isEmpty()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(thumbColor); g.fillRoundRect(bounds.x + 2, bounds.y + 2,
                    Math.max(2, bounds.width - 4), Math.max(2, bounds.height - 4), 8, 8); g.dispose();
        }
    }

    private static final class MetricGraphPanel extends JPanel {
        private final String title;
        private final Color accent;
        private final Deque<Double> samples = new ArrayDeque<Double>();
        private String value = "initialisation";

        MetricGraphPanel(String title, Color accent) {
            this.title = title; this.accent = accent;
            setBackground(PANEL_ALT); setPreferredSize(new Dimension(315, 132));
            setBorder(new RoundedBorder(BORDER, 10, 1));
        }

        void addSample(double sample, String displayValue) {
            samples.addLast(Double.valueOf(Math.max(0, Math.min(100, sample))));
            while (samples.size() > 90) samples.removeFirst();
            value = displayValue; repaint();
        }

        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth(), height = getHeight();
            g.setColor(MUTED); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10)); g.drawString(title, 12, 18);
            g.setColor(TEXT); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            int valueWidth = g.getFontMetrics().stringWidth(value); g.drawString(value, Math.max(12, width - valueWidth - 12), 19);
            int left = 12, top = 34, graphWidth = Math.max(1, width - 24), graphHeight = Math.max(1, height - 46);
            g.setColor(BORDER);
            for (int i = 0; i <= 4; i++) { int y = top + graphHeight * i / 4; g.drawLine(left, y, left + graphWidth, y); }
            if (samples.size() > 1) {
                int count = samples.size(), index = 0, previousX = left, previousY = top + graphHeight;
                g.setColor(accent); g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (Double sample : samples) {
                    int x = left + (count == 1 ? 0 : index * graphWidth / (count - 1));
                    int y = top + graphHeight - (int) Math.round(sample.doubleValue() * graphHeight / 100.0);
                    if (index > 0) g.drawLine(previousX, previousY, x, y);
                    previousX = x; previousY = y; index++;
                }
            }
            g.dispose();
        }
    }

    private static String javaExecutable() {
        String suffix = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Paths.get(System.getProperty("java.home"), "bin", suffix).toString();
    }
}
