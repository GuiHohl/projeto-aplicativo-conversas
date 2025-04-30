import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Servidor {

    private static Map<String, ClientInfo> clientes = Collections.synchronizedMap(new HashMap<>());
    private static final String LOG_FILE = "server_log.txt";

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Servidor aguardando conexões...");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ThreadCliente(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientInfo {
        Socket socket;
        PrintWriter out;

        ClientInfo(Socket socket, PrintWriter out) {
            this.socket = socket;
            this.out = out;
        }
    }

    private static class ThreadCliente implements Runnable {
        private Socket socket;
        private String username;

        ThreadCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                out.println("Digite seu nome de usuário:");
                username = in.readLine();
                clientes.put(username, new ClientInfo(socket, out));

                logConnection(socket);
                System.out.println(username + " conectado: " + socket.getInetAddress());

                String input;
                while ((input = in.readLine()) != null) {
                    if (input.startsWith("/users")) {
                        listUsers(out);
                    } else if (input.startsWith("/send message")) {
                        sendMessage(input, username);
                    } else if (input.startsWith("/send file")) {
                        receiveFile(in, input, username);
                    } else if (input.startsWith("/sair")) {
                        sair();
                        break;
                    } else {
                        out.println("Comando inválido.");
                    }
                }
            } catch (IOException e) {
                System.out.println("Conexão com " + username + " perdida.");
            } finally {
                clientes.remove(username);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void sair() throws IOException {
            PrintWriter out = clientes.get(username).out;
            out.println("Desconectando...");
            clientes.remove(username);
            socket.close();
        }

        private void listUsers(PrintWriter out) {
            out.println("Usuários conectados:");
            for (String user : clientes.keySet()) {
                out.println("- " + user);
            }
        }

        private void sendMessage(String input, String sender) {
            try {
                String[] parts = input.split(" ", 4);
                String recipient = parts[2];
                String message = parts[3];

                ClientInfo dest = clientes.get(recipient);
                if (dest != null) {
                    dest.out.println(sender + ": " + message);
                } else {
                    clientes.get(sender).out.println("Usuário não encontrado.");
                }
            } catch (Exception e) {
                clientes.get(sender).out.println("Formato inválido. Use /send message <destinatario> <mensagem>");
            }
        }

        private void receiveFile(BufferedReader in, String input, String sender) {
            try {
                String[] parts = input.split(" ", 4);
                String recipient = parts[2];
                String fileName = parts[3];

                ClientInfo dest = clientes.get(recipient);
                if (dest != null) {
                    dest.out.println("/file " + sender + " " + fileName);

                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(dest.socket.getOutputStream());

                    int fileSize = dis.readInt();
                    byte[] buffer = new byte[4096];
                    int read;
                    int remaining = fileSize;

                    dos.writeInt(fileSize);
                    while ((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                        dos.write(buffer, 0, read);
                        remaining -= read;
                    }
                } else {
                    clientes.get(sender).out.println("Usuário não encontrado para envio de arquivo.");
                }
            } catch (Exception e) {
                clientes.get(sender).out.println("Erro ao enviar arquivo.");
            }
        }

        private void logConnection(Socket socket) {
            try (PrintWriter logWriter = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                logWriter.println(socket.getInetAddress().getHostAddress() + " conectado em " + timeStamp);
            } catch (IOException e) {
                System.out.println("Erro ao escrever no log.");
            }
        }
    }
}
