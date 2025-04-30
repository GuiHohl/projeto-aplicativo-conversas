import java.io.*;
import java.net.*;

public class Cliente {
    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 12345);
            System.out.println("Conectado ao servidor!");

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

            // Thread para ouvir mensagens do servidor
            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        if (serverMessage.startsWith("/file")) {
                            receberArquivo(serverMessage, dis);
                        } else {
                            System.out.println(serverMessage);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Desconectado do servidor.");
                }
            }).start();

            // Enviar nome de usuário
            System.out.print("Digite seu nome de usuário: ");
            String username = userInput.readLine();
            out.println(username);

            // Loop principal para enviar comandos
            String input;
            while ((input = userInput.readLine()) != null) {
                if (input.startsWith("/send file")) {
                    enviarArquivo(input, out, dos);
                } else {
                    out.println(input);
                    if (input.equals("/sair")) {
                        socket.close();
                        break;
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void enviarArquivo(String comando, PrintWriter out, DataOutputStream dos) {
        try {
            String[] parts = comando.split(" ", 4);
            if (parts.length < 4) {
                System.out.println("Formato inválido. Use /send file <destinatario> <caminho_do_arquivo>");
                return;
            }
            String recipient = parts[2];
            String filePath = parts[3];

            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("Arquivo não encontrado.");
                return;
            }

            out.println("/send file " + recipient + " " + file.getName());

            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;

            dos.writeInt((int) file.length());
            while ((read = fis.read(buffer)) > 0) {
                dos.write(buffer, 0, read);
            }
            fis.close();

            System.out.println("Arquivo enviado!");
        } catch (Exception e) {
            System.out.println("Erro ao enviar arquivo: " + e.getMessage());
        }
    }

    private static void receberArquivo(String serverMessage, DataInputStream dis) {
        try {
            String[] parts = serverMessage.split(" ", 3);
            String sender = parts[1];
            String fileName = parts[2];

            int fileSize = dis.readInt();
            byte[] buffer = new byte[4096];
            int read;
            int remaining = fileSize;

            FileOutputStream fos = new FileOutputStream(fileName);
            while ((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                fos.write(buffer, 0, read);
                remaining -= read;
            }
            fos.close();

            System.out.println("Arquivo " + fileName + " recebido de " + sender + "!");
        } catch (Exception e) {
            System.out.println("Erro ao receber arquivo: " + e.getMessage());
        }
    }
}
