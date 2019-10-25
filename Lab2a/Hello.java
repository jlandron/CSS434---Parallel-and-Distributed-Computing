
/*
 * Author of revised version: Franklyn Pinedo
 * Author of new revised version: David Walker
 *
 * Adapted from Source Code in C of Tutorial/User's Guide for MPI by
 * Peter Pacheco.
 */

import mpi.*;

class Hello {
  static public void main(String[] args) throws MPIException {

    MPI.Init(args);

    int my_rank; // Rank of process
    int source; // Rank of sender
    int dest; // Rank of receiver
    int tag = 50; // Tag for messages
    int myrank = MPI.COMM_WORLD.Rank();
    int p = MPI.COMM_WORLD.Size();

    if (myrank != 0) {
      dest = 0;
      String myhost = MPI.Get_processor_name();
      char[] message = ("Greetings from process " + myrank + " on " + myhost).toCharArray();
      MPI.COMM_WORLD.Send(message, 0, message.length, MPI.CHAR, dest, tag);
    } else { // my_rank == 0
      for (source = 1; source < p; source++) {
        char[] message = new char[60];
        Status s = MPI.COMM_WORLD.Recv(message, 0, 60, MPI.CHAR, MPI.ANY_SOURCE, tag);
        int nrecv = s.Get_count(MPI.CHAR);
        String s1 = new String(message);
        System.out.println("received: " + s1.substring(0, nrecv) + " : ");
      }
    }

    MPI.Finalize();
  }
}
