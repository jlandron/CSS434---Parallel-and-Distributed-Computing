
/**
 *#############################################################################
 *#-------------------------- ServerInterface-----------------------------------
 *#  
 *#  @author 	Joshua Landron
 *#  @date 	    10May2019
 *#  @version	17May2019
 *#
 *#  Built as part of CSS434 with Dr. Munehiro Fukuda, Spring 2019
 *#
 *#############################################################################
 *
 *
 * Implementation and assumptions:
 *  This interface defines the methods that will be available to clients that 
 *  would like to connect to it for RMI.
 * ------------------------------------------------------------------------------
 **/
import java.rmi.*;
import java.util.*;

public interface ServerInterface extends Remote {
    public Vector execute(String command) throws RemoteException;
}
