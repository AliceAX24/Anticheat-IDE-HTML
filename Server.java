package main;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Server extends JFrame {
    private JTextArea logTextArea;
    private JTextArea cheaterInfoTextArea;
    private int port = 8080;
    private ServerSocket serverSocket;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private Map<String, Long> logonTimes = new HashMap<>();
    private Map<String, Long> bannedIPs = new HashMap<>();
    private Map<String, Long> bannedUsers = new HashMap<>();
    private String username;
    private ClientHandler clientHandler;

    public Server() {
        setTitle("Server GUI");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logTextArea);

        cheaterInfoTextArea = new JTextArea();
        cheaterInfoTextArea.setEditable(false);
        JScrollPane cheaterInfoScrollPane = new JScrollPane(cheaterInfoTextArea);

        JButton startButton = new JButton("Iniciar Servidor");
        startButton.addActionListener(e -> startServer());

        JButton banIPButton = new JButton("Banir IP");
        banIPButton.addActionListener(e -> {
            String ipToBan = JOptionPane.showInputDialog("Digite o IP para banir:");
            if (ipToBan != null && !ipToBan.isEmpty()) {
                clientHandler.banIP(ipToBan);
            }
        });

        JButton banUserButton = new JButton("Banir Usuário");
        banUserButton.addActionListener(e -> {
            String userToBan = JOptionPane.showInputDialog("Digite o usuário para banir:");
            if (userToBan != null && !userToBan.isEmpty()) {
                clientHandler.banUser(userToBan);
            }
        });

        JButton showConnectedUsersButton = new JButton("Mostrar Conectados");
        showConnectedUsersButton.addActionListener(e -> clientHandler.showConnectedUsers());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(banIPButton);
        buttonPanel.add(banUserButton);
        buttonPanel.add(showConnectedUsersButton);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.add(logScrollPane);
        infoPanel.add(cheaterInfoScrollPane);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(infoPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
    }

    private void startServer() {
        clientHandler = new ClientHandler();
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                log("Servidor esperando por conexões na porta " + port);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    log("Conexão estabelecida com " + getClientInfo(clientSocket));

                    clientHandler.setSocket(clientSocket);
                    new Thread(clientHandler).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        serverThread.start();
    }

    private String getClientInfo(Socket socket) {
        String username = "";
        if (username != null && !username.isEmpty()) {
            username = "(" + username + " ";
        }
        return username + "IP: " + socket.getInetAddress().getHostAddress() + ")";
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logTextArea.append("[" + dateFormat.format(new Date()) + "] " + message + "\n"));
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;

        public void setSocket(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Trapaça detectada por:")) {
                        // Corrigir a divisão correta da mensagem
                        String[] parts = line.split(" \\(IP: ");
                        String username = parts[0].substring("Trapaça detectada por: ".length());
                        String ipAddress = parts[1].substring(0, parts[1].length() - 1);
                        displayCheaterInfo(line, username, ipAddress);
                    } else if (line.startsWith("Conteúdo do index.html:")) {
                        System.out.println("Recebendo conteúdo do index.html:");
                        StringBuilder indexHtmlContent = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            indexHtmlContent.append(line).append("\n");
                            System.out.println(line);
                        }
                        saveIndexHtml(username, indexHtmlContent.toString());
                        sendConfirmation();
                    } else if (line.startsWith("Nome de Usuário: ")) {
                        username = line.substring("Nome de Usuário: ".length()).trim();
                    } else {
                        System.out.println("Mensagem do Cliente " + getClientInfo(clientSocket) + ": " + line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendConfirmation() {
            try {
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                writer.println("Arquivo HTML recebido com sucesso!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void banIP(String ipToBan) {
            bannedIPs.put(ipToBan, System.currentTimeMillis());
            displayCheaterInfo("IP " + ipToBan + " foi banido.", "", ipToBan);
            log("IP " + ipToBan + " banido.");
        }

        private void banUser(String userToBan) {
            bannedUsers.put(userToBan, System.currentTimeMillis());
            displayCheaterInfo("Usuário " + userToBan + " foi banido.", userToBan, "");
            log("Usuário " + userToBan + " banido.");
        }

        private void showConnectedUsers() {
            StringBuilder connectedUsers = new StringBuilder("Usuários Conectados:\n");
            for (String username : logonTimes.keySet()) {
                connectedUsers.append(username).append("\n");
            }
            JOptionPane.showMessageDialog(null, connectedUsers.toString(), "Usuários Conectados", JOptionPane.INFORMATION_MESSAGE);
        }

        private void displayCheaterInfo(String message, String username, String ipAddress) {
            SwingUtilities.invokeLater(() -> cheaterInfoTextArea.append("[" + dateFormat.format(new Date()) + "] " + message + " (Usuário: " + username + ", IP: " + ipAddress + ")\n"));
        }

        private void saveIndexHtml(String username, String content) {
            if (!content.trim().isEmpty()) {
                String clientIP = clientSocket.getInetAddress().getHostAddress();
                if (!isBannedIP(clientIP) && !isBannedUser(username)) {
                    try {
                        String fileName = System.getProperty("user.home") + "/Desktop/" + username + "_index.html";
                        File file = new File(fileName);
                        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                        writer.write(content);
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    log("Arquivo não salvo. IP ou usuário banido.");
                }
            }
        }

        private boolean isBannedIP(String ip) {
            return bannedIPs.containsKey(ip);
        }

        private boolean isBannedUser(String user) {
            return bannedUsers.containsKey(user);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Server server = new Server();
            server.setVisible(true);
        });
    }
}
