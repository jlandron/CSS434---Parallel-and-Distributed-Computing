import java.net.*;
import java.io.*;

public class EchoServer {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Enter server port number");
            return;
        }
        ServerSocket s = null;
        try {
            int serverPort = Integer.parseInt(args[0]);

            if (serverPort <= 5000 || serverPort > 65535) {
                System.out.println("Enter a non reserved port between 5001 and 65535");
            }
            s = new ServerSocket(serverPort);
            System.out.println("Opened server port on port: " + serverPort);

            Socket incomingSocket = s.accept();

            PrintWriter out = new PrintWriter(new OutputStreamWriter(incomingSocket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(incomingSocket.getInputStream()));

            String userInput;
            // do{
            userInput = in.readLine();
            out.println("Echo: " + userInput);
            System.out.println(userInput);
            out.flush();
            // }while (userInput != null);

            System.out.println("Connection closed by client");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                s.close();
            } catch (Exception e) {
                System.out.println("IO:" + e.getMessage());
            }
        }
    }
}