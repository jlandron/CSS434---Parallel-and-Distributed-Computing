
/**################################################################################
 * @Description		A distributed computing program that calculates the Heat moving
 * through a 2d plane using forward Euler's method.
 * 
 * This program will work on a single computing node as well as 
 * multiple MPI connected nodes.
 * 
 * @author	Joshua Landron
 * @version	3May2019
 * Derived for Program 2, (parallelization for scientific computing using java MPI)
 * Professor Fukuda, CSS 434 Spring 2019
 *##################################################################################
 */
import java.util.Date;
import mpi.*;

class Heat2D_mpi {

	private static double a = 1.0; // heat speed
	private static double dt = 1.0; // time quantum
	private static double dd = 2.0; // change in system

	public static int index(int x, int y, int size) {
		return (x * size + y); // 2d array index into 1d index mapping, column by column
	}
	public static void main(String[] args) throws MPIException {
		// verify arguments
		if (args.length != 4) {
			System.out.println("usage: " + "java Heat2D size max_time heat_time interval");
			System.exit(-1);
		}
		// initialize MPI
		MPI.Init(args);
		// get constants from MPI.COMM_WORLD
		int myRank = MPI.COMM_WORLD.Rank();
		int mpi_size = MPI.COMM_WORLD.Size();

		int size = Integer.parseInt(args[0]);
		int max_time = Integer.parseInt(args[1]);
		int heat_time = Integer.parseInt(args[2]);
		int interval = Integer.parseInt(args[3]);
		double r = a * dt / (dd * dd);

		// calculate ranges
		int stripe = size / mpi_size;
		int remainder = size % mpi_size;
		int[] stripes = new int[mpi_size];
		int[] stripe_begins = new int[mpi_size];
		int[] stripe_ends = new int[mpi_size];

		for (int rank = 0; rank < mpi_size; rank++) {
			stripes[rank] = stripe + ((rank < remainder) ? 1 : 0);
			stripe_begins[rank] = (rank < remainder) ? stripe * rank + rank : stripe * rank + remainder;
			stripe_ends[rank] = stripe_begins[rank] + stripes[rank] - 1;
		}
		// create a space
		double[][][] z = new double[2][size][size];
		for (int p = 0; p < 2; p++) {
			for (int x = 0; x < size; x++) {
				for (int y = 0; y < size; y++) {
					z[p][x][y] = 0.0; // no heat or cold
				}
			}
		}
		// start a timer
		Date startTime = new Date();
		// each stripe prints its range
		// System.out.println("Rank[" + myRank + "]'s range = " +
		// stripe_begins[myRank] + " ~ " + stripe_ends[myRank]);

		// simulate heat diffusion
		for (int t = 0; t < max_time; t++) {
			// ------------------first three calculations---------
			int p = t % 2; // p = 0 or 1: indicates the phase

			// two left-most and two right-most columns are identical
			for (int y = 0; y < size; y++) {
				z[p][0][y] = z[p][1][y];
				z[p][size - 1][y] = z[p][size - 2][y];
			}

			// two upper and lower rows are identical
			for (int x = 0; x < size; x++) {
				z[p][x][0] = z[p][x][1];
				z[p][x][size - 1] = z[p][x][size - 2];
			}

			// keep heating the bottom until t < heat_time
			if (t < heat_time) {
				for (int x = size / 3; x < size / 3 * 2; x++) {
					z[p][x][0] = 19.0; // heat
				}
			}
			// --------------information sharing and calculation--------------
			// only share information if there is more than one computing node
			if (mpi_size > 1) {
				// send current (p) edge information to neighbors
				double[] rightEdgeSend = new double[size];// create and fill arrays to send and receive messages
				double[] leftEdgeSend = new double[size];
				for (int y = 0; y < size; y++) { // copy left column
					rightEdgeSend[y] = z[p][stripe_ends[myRank]][y];
					leftEdgeSend[y] = z[p][stripe_begins[myRank]][y];
				}
				// they will be the height of the matrix

				double[] outsideLeft = new double[size];
				double[] outsideRight = new double[size];
				// even nodes send first, receive second
				if (myRank % 2 == 0) {
					if (myRank != mpi_size - 1) { // not right edge stripe
						MPI.COMM_WORLD.Send(rightEdgeSend, 0, size, MPI.DOUBLE, myRank + 1, 0);
					}
					if (myRank != 0) { // not left edge stripe
						MPI.COMM_WORLD.Send(leftEdgeSend, 0, size, MPI.DOUBLE, myRank - 1, 0);

						MPI.COMM_WORLD.Recv(outsideLeft, 0, size, MPI.DOUBLE, myRank - 1, 0);
						for (int y = 0; y < size; y++) {
							z[p][stripe_begins[myRank] - 1][y] = outsideLeft[y];
						}
					}
					if (myRank != mpi_size - 1) {
						MPI.COMM_WORLD.Recv(outsideRight, 0, size, MPI.DOUBLE, myRank + 1, 0);
						for (int y = 0; y < size; y++) {
							z[p][stripe_ends[myRank] + 1][y] = outsideRight[y];
						}
					}
				} else { // odd nodes receive first, and send second
					MPI.COMM_WORLD.Recv(outsideLeft, 0, size, MPI.DOUBLE, myRank - 1, 0); // receive next left
					for (int y = 0; y < size; y++) {
						z[p][stripe_begins[myRank] - 1][y] = outsideLeft[y];
					}
					if (myRank != mpi_size - 1) { // if not rightmost stripe
						// receive right edge from neighbor
						MPI.COMM_WORLD.Recv(outsideRight, 0, size, MPI.DOUBLE, myRank + 1, 0);
						for (int y = 0; y < size; y++) {
							z[p][(stripe_ends[myRank] + 1)][y] = outsideRight[y];// fill in outside of my stripe
						}
						MPI.COMM_WORLD.Send(rightEdgeSend, 0, size, MPI.DOUBLE, myRank + 1, 0);
					}
					// all odds receive left edge from neighbor
					// now odds send
					MPI.COMM_WORLD.Send(leftEdgeSend, 0, size, MPI.DOUBLE, myRank - 1, 0);
				}
			}
			// Intermediate results printed here
			if (interval != 0 && (t % interval == 0 || t == max_time - 1)) {
				if (myRank != 0) {
					// send only the pertinent results
					double[] myResults = new double[stripes[myRank] * size];
					// copy current state of myRank's information to myResults
					for (int x = stripe_begins[myRank]; x <= stripe_ends[myRank]; x++) {
						for (int y = 0; y < size; y++) {
							myResults[index((x - stripe_begins[myRank]), y, size)] = z[p][x][y];
						}
					}
					MPI.COMM_WORLD.Send(myResults, 0, stripes[myRank] * size, MPI.DOUBLE, 0, 0);
				} else {// rank 0 receives all information and prints the full active grid
					for (int rank = 1; rank < mpi_size; rank++) {
						double[] myResults = new double[stripes[rank] * size];
						MPI.COMM_WORLD.Recv(myResults, 0, stripes[rank] * size, MPI.DOUBLE, rank, 0);
						// distribute results to main grid
						for (int x = 0; x < stripes[rank]; x++) { // left to right
							for (int y = 0; y < size; y++) { // top to bottom
								z[p][x + stripe_begins[rank]][y] = myResults[index(x, y, size)];
							}
						}
					}

					System.out.println("time = " + t);
					for (int y = 0; y < size; y++) {
						for (int x = 0; x < size; x++) {
							System.out.print((int) (Math.floor(z[p][x][y] / 2)) + " ");
						}
						System.out.println();
					}
					System.out.println();

				}
			}
			// perform forward Euler method
			int p2 = (p + 1) % 2;
			// separate euler calc for leftmost, rightmost, and internal
			if (myRank == 0) { // leftmost stripe, don't go over left edge
				int safeRight = stripe_ends[0] + (mpi_size < 2 ? 0 : 1); // check if there is only 1 Computing stripe
				for (int x = 1; x < safeRight; x++) { // if so, safeRight will be size - 1
					for (int y = 1; y < size - 1; y++) {
						z[p2][x][y] = z[p][x][y] + r * (z[p][x + 1][y] - 2 * z[p][x][y] + z[p][x - 1][y])
								+ r * (z[p][x][y + 1] - 2 * z[p][x][y] + z[p][x][y - 1]);
					}
				}
			} else if (myRank == (mpi_size - 1)) { // rightmost stripe, don't go over right edge
				for (int x = stripe_begins[myRank]; x < stripe_ends[myRank]; x++) {
					for (int y = 1; y < size - 1; y++) {
						z[p2][x][y] = z[p][x][y] + r * (z[p][x + 1][y] - 2 * z[p][x][y] + z[p][x - 1][y])
								+ r * (z[p][x][y + 1] - 2 * z[p][x][y] + z[p][x][y - 1]);
					}
				}
			} else { // internal stripes calculate over entire range inclusive
				for (int x = stripe_begins[myRank]; x < stripe_ends[myRank] + 1; x++) {
					for (int y = 1; y < size - 1; y++) {
						z[p2][x][y] = z[p][x][y] + r * (z[p][x + 1][y] - 2 * z[p][x][y] + z[p][x - 1][y])
								+ r * (z[p][x][y + 1] - 2 * z[p][x][y] + z[p][x][y - 1]);
					}
				}
			}
			
		} // end of simulation
			// finish the timer
		if (myRank == 0) {
			Date endTime = new Date();
			System.out.println("Elapsed time = " + (endTime.getTime() - startTime.getTime()));
		}
		// close MPI connections
		MPI.Finalize();
	}

}
