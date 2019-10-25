import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

class ClientImplementation extends UnicastRemoteObject implements ClientInterface {
    ClientImplementation() throws RemoteException{}
    public void receiveMessage(String message) {
        System.out.println("Echo: " + message);
    }
}