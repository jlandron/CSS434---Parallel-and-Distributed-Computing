import mpi.*; // for mpiJava
import java.util.Date;

public class MyProgram {
	private final static int aSize = 100; // the size of dArray
	private final static int master = 0; // the master rank
	private final static int tag = 0; // Send/Recv's tag is always 0.

	public static void main(String[] args) throws MPIException {
		// Start the MPI library.
		MPI.Init(args);
		int myRank = MPI.COMM_WORLD.Rank();
		int connections = MPI.COMM_WORLD.Size();
		// compute my own stripe // each portion of the array
		int stripe = aSize / connections + ((aSize % connections != 0 && myRank == connections - 1) ? 1 : 0);
		System.out.println("I am rank: " + myRank + ", I will process " + stripe + " data members");
		double[] dArray = null;
		Date startTime = new Date(); // initialize to ensure no errors
		Date endTime = new Date();

		if (myRank == master) { // master

			// initialize dArray[100].
			dArray = new double[aSize];
			for (int i = 0; i < aSize; i++) {
				dArray[i] = i;
			}
			startTime = new Date();
			// copy proper elements to each array to prepare to send, send in the loop
			// this will not do anything for the master, i.e. rank 0
			for (int rank = 1; rank < connections; rank++) {
				double[] dataSection;
				// send a portion of dArray[100] to each slave
				// check if there is leftover data due to division
				if (aSize % connections != 0 && rank == connections - 1) { // there is a remainder in the division
					dataSection = new double[stripe + 1];
					for (int j = 0; j < stripe + 1; j++) {
						dataSection[j] = dArray[j + rank * stripe];// offset the data set for each rank
					}
					MPI.COMM_WORLD.Send(dataSection, 0, stripe + 1, MPI.DOUBLE, rank, tag);
				} else {
					dataSection = new double[stripe];
					for (int j = 0; j < stripe; j++) {
						dataSection[j] = dArray[j + rank * stripe];// offset the data set for each rank
					}
					MPI.COMM_WORLD.Send(dataSection, 0, stripe, MPI.DOUBLE, rank, tag);
				}
			}

		} else { // slaves: rank 1 to rank n - 1
			dArray = new double[stripe];
			MPI.COMM_WORLD.Recv(dArray, 0, stripe, MPI.DOUBLE, 0, tag);
			// receive a portion of dArray[100] from the master
		}

		// compute the square root of each array element
		for (int i = 0; i < stripe; i++) {
			dArray[i] = Math.sqrt(dArray[i]);
		}

		if (myRank == master) { // master
			// for each connected computer, receive its data
			for (int rank = 1; rank < connections; rank++) {
				if (aSize % connections != 0 && rank == connections - 1) { // there is a remainder in the division
					double[] temp = new double[stripe + 1];
					MPI.COMM_WORLD.Recv(temp, 0, stripe + 1, MPI.DOUBLE, rank, tag);
					for (int j = 0; j < stripe + 1; j++) {
						dArray[j + rank * stripe] = temp[j];// offset the data set from each rank
					}
				} else {
					double[] temp = new double[stripe];
					MPI.COMM_WORLD.Recv(temp, 0, stripe, MPI.DOUBLE, rank, tag);
					for (int j = 0; j < stripe; j++) {
						dArray[j + rank * stripe] = temp[j];// offset the data set from each rank
					}
				}
			}
			// receive answers from each slave
			endTime = new Date();
			// print out the results
			for (int i = 0; i < aSize; i++) {
				System.out.println("dArray[ " + i + " ] = " + dArray[i]);
			}
			System.out.println("time elapsed = " + (endTime.getTime() - startTime.getTime()) + " msec");
		} else { // slaves: rank 0 to rank n - 1
			MPI.COMM_WORLD.Send(dArray, 0, stripe, MPI.DOUBLE, 0, tag);
			// send the results back to the master
		}

		// Terminate the MPI library.
		MPI.Finalize();
	}
}
