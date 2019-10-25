import java.net.*;
import java.io.*;

public class TcpClientDouble { // Unchanged from byte code
	public static void main(String args[]) {
		if (args.length != 3) {
			System.err.println("usage: java TcpClient port size server_ip");
			return;
		}
		try {
			// establish a connection
			Socket socket = new Socket(args[2], Integer.parseInt(args[0]));
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

			int size = Integer.parseInt(args[1]);
			double[] data = new double[size]; // initialize data
			for (int i = 0; i < size; i++)
				data[i] = (double) (i % 128);

			out.writeObject(data); // send data
			data = (double[]) in.readObject(); // receive data
			for (int i = 0; i < size; i++) { // print results
				System.out.println(data[i]);
			}
			socket.close(); // close the connection
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
