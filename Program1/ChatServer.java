
/**
 * ChatServer.java
 * central chat server for many clients, maintains their connections and distributes messages.
 *
 * @author  Joshua Landron (CSS434 Spring 2019, University of Washington, Bothell)
 * @since   4/12/19
 * @version 4/14/19
 */
import java.net.*; // for Socket
import java.util.ArrayList;
import java.io.*; // for IOException

public class ChatServer {
    // maintain a list of connections to this server
    private ArrayList<Connection> connections = new ArrayList<>(); // connection to i

    /**
     * Creates a socket, contacts to the server with a given server ip name and a
     * port, sends a given calling user name, and goes into a "while" loop in that:
     * <p>
     *
     * <ol>
     * <li>forward a message from the standard input to the server
     * <li>forward a message from the server to the standard output
     * </ol>
     *
     * @param name   the calling user name
     * @param server a server ip name
     * @param port   a server port
     */
    public ChatServer(int port) {
        // set up server
        ServerSocket server;
        try {
            server = new ServerSocket(port);
            System.out.println("-------Server Started on port " + port + "-------");
            server.setSoTimeout(500);

            while (true) {// start server loop

                Socket client = null;
                // server checks if there is a new connection request
                // if there is, it connects the client, and adds the client to the array
                // of clients connected to the server.
                // this uses a timeout so as to be non blocking.
                try {
                    client = server.accept();
                    Connection c = new Connection(client);
                    connections.add(c);
                    System.out.println(
                            "New client connected, The number of active connections is: " + connections.size());
                } catch (SocketTimeoutException e) {
                    /* move on */}
                // check for new messages from clients
                String msg = "";
                for (int i = 0; i < connections.size(); i++) {
                    msg = connections.get(i).readMessage();
                    if (msg.length() > 0) {
                        sendMessages(msg, i);
                    }
                    msg = "";
                }
                // check clients for errors, disconnect as needed
                for (int i = 0; i < connections.size(); i++) {
                    if (connections.get(i).disconnect) { // checks the boolean value in each connection
                        // attempt to send client disconnect message
                        connections.get(i).writeMessage("Error detected. Please reconnect to server. Goodbye.");
                        connections.get(i).disconnect(); // cloeses streams and connection
                        connections.remove(i); // remove connection
                        System.out.println(
                                "Client disconnected, The number of active connections is: " + connections.size());
                        i--; // decrememnt so as not to skip connections.
                    }
                }

            } // end server loop
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Usage: sends message to every client currently connected.
     *
     * @param msg    String to be ditributed to all clients.
     * @param sender int representing the clients location in the vector, used to
     *               keep the server from sending messages to sender
     */
    public void sendMessages(String msg, int sender) {
        for (int client = 0; client < connections.size(); client++) {
            if (client != sender) {
                connections.get(client).writeMessage(msg);
            } // do not send message to original sender
        }
    }

    /**
     * Usage: java ChatServer <port>
     *
     * @param args Receives the port for clients to connect to in args[0].
     */
    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Syntax: java ChatClient <port>");
            System.exit(1);
        }

        // convert args[2] into an integer that will be used as port.
        int port = Integer.parseInt(args[0]);

        // instantiate the main body of ChatClient application.
        new ChatServer(port);
    }
}