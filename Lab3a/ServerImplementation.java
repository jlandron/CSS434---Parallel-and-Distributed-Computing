import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

class ServerImplementation extends UnicastRemoteObject implements ServerInterface {
    ServerImplementation() throws RemoteException{}
    public void echo(ClientInterface client, String message) throws RemoteException {
        client.receiveMessage(message);
    }
}