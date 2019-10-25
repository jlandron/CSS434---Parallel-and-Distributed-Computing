import java.net.*;
import java.io.*;

public class TcpServerDouble { // Unchanged from byte code
	public static void main(String args[]) {
		if (args.length != 2) {
			System.err.println("usage: java TcpServer port size");
			return;
		}
		try {
			ServerSocket server = new ServerSocket(Integer.parseInt(args[0]));
			byte multiplier = 1;
			int size = Integer.parseInt(args[1]);
			System.out.println("Server started on port: " + args[0]);
			while (true) {
				// establslih a connection
				Socket socket = server.accept();
				System.out.println("Client connected");
				ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
				ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

				double[] data = new double[size]; // receive data
				data = (double[]) in.readObject();
				for (int i = 0; i < size; i++) // modify data
					data[i] *= multiplier;
				out.writeObject(data); // send back data

				socket.close(); // close the connection
				System.out.println("Client disconnected");
				multiplier *= 2;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
