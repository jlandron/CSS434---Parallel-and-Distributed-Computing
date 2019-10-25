import java.rmi.*;

public interface ServerInterface extends Remote {
    public void echo(ClientInterface client, String message) throws RemoteException;
}
