import java.rmi.*;

public interface ClientInterface extends Remote {
    public void receiveMessage(String message) throws RemoteException;
}