import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;

public class UnixServer extends UnicastRemoteObject implements ServerInterface {
	private boolean print = false;

	public UnixServer(String print) throws RemoteException {
		this.print = print.startsWith("P");
	}

	public static void main(String args[]) {
		if (args.length != 2) {
			System.err.println("usage: java UnixServer P/S port#"); // print or silence
			System.exit(-1);
		}
		try {
			UnixServer unixserver = new UnixServer(args[0]);
			Naming.rebind("rmi://localhost:" + args[1] + "/unixserver", unixserver);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public Vector<String> execute(String command) {
		Vector<String> output = new Vector<String>();
		String line;
		try {
			Runtime runtime = Runtime.getRuntime();
			Process process = runtime.exec(command);
			InputStream input = process.getInputStream();
			BufferedReader bufferedInput = new BufferedReader(new InputStreamReader(input));
			while ((line = bufferedInput.readLine()) != null) {
				if (print)
					System.out.println(line);
				output.addElement(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return output;
		}
		return output;
	}
}
