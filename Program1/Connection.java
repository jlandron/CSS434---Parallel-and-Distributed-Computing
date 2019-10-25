
/**
 * Connection.java
 * COnnection Object that maintains a TCP connection between a client and the chat server
 *
 * @author  Joshua Landron (CSS434 Spring 2019, University of Washington, Bothell)
 * @since   4/12/19
 * @version 4/14/19
 */
import java.io.*;
import java.net.*;

class Connection {
    public DataInputStream in;
    public DataOutputStream out;
    public Socket clientSocket;
    public boolean disconnect = false;

    /**
     * Creates a TCP connection between client and server. Connections should be
     * maintained and used by ChatServer
     * 
     * @param aClientSocket a socket built by the ChatServer to be maintained in
     *                      this Connection object
     */
    public Connection(Socket aClientSocket) {
        try {
            clientSocket = aClientSocket;
            in = new DataInputStream(clientSocket.getInputStream());
            out = new DataOutputStream(clientSocket.getOutputStream());
            out.writeUTF("-----Connection to chat server established-----");
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Usage: reads a message that was sent from a client.
     *
     * @return message to be sent to ChatServer
     */
    protected String readMessage() {
        String msg = "";
        try {
            if (in.available() > 0) {
                msg = in.readUTF();
            }
        } catch (Exception e) {
            disconnect = true; // error occured, tell server to disconnect
        }
        return msg;
    }

    /**
     * Usage: sends message to connected client
     *
     * @param msg String to be send to client
     */
    protected void writeMessage(String msg) {
        try {
            out.writeUTF(msg);
            out.flush();
        } catch (Exception e) {
            disconnect = true; // error occured, tell server to disconnect
        }
    }

    /**
     * Usage: disconnects TCP connection and cleans up streams if error is detected
     */
    protected void disconnect() {
        try {
            out.close();
            in.close();
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}