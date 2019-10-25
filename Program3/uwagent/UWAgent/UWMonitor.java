package UWAgent;

import java.io.*;

/**
 * Class Name: UWMonitor
 * Purpose: Implements monitor commands, utilities for controlling
 * 		    the UWAgents running on the local UWPlace.
 * Used by: UWMonitor
 *
 * @author    Duncan Smith (CSS, University of Washington, Bothell)
 * @since     12/10/05
 * @version   7/31/06
 */
public class UWMonitor extends UWPlace {
    
    /**
     * Is the constructor. It calls the UWPlace constructor.
     */
    public UWMonitor( ) {
    	super( );	// Call UWPlace constructor
    }
    
    /**
     * Accepts a monitor command and injects a UWMonitorAgent.java that
     * executes the command at the local UWPlace.
     *
     * @param args contains a monitor command preceded with port#. Acceptable
     *             commands include:
     *             <sl>
     *	           <li> as: agent status
     *             <li>	kill agentId: kill a specific agent
     *		   <li> suspend agentId: suspend a specific agent
     *		   <li> resume agentId: resume a specific agent
     *		   <li> help: help
     *             </sl>
     */
    public static void main( String[] args ) {
	try {
	    // Validate arguments/print usage
	    if ( args.length < 2 || args[0].equals( "help" ) ) {
		displayUsage( );
		System.exit( 1 );
	    }

	    // Check UWAgent.jar in the current working directory
	    try {
		new FileInputStream( "UWAgent.jar" );
	    } catch ( Exception e ) {
		displayUsage( );
		System.exit( 1 );
	    }

	    // Form a series of arguments passed to UWMonitorAgent
	    String[] injectArgs = new String[6 + args.length - 1];
	    injectArgs[0] = "localhost";
	    injectArgs[1] = "-j";
	    injectArgs[2] = "UWAgent.jar";
	    injectArgs[3] = "-u";
	    injectArgs[4] = "UWAgent";
	    injectArgs[5] = "UWAgent.UWMonitorAgent";
	    for (int i=1; i<args.length; i++) {
		injectArgs[i+5] = args[i];
	    }
	    
	    // Inject a UWMonitorAgent
	    UWInject inject = new UWInject();
	    inject.setPortNumber( args[0] );
	    inject.main( injectArgs );
	    
	    UWUtility.LogInfo("End of UWMonitor (main)");
	    System.exit(0);
	}
	catch (Exception e) {
	    UWUtility.Log( "UWMonitor.main: " + e.toString( ) );
	}
    }

    private static void displayUsage() {
	System.err.println("Usage: $ java -cp UWAgent.jar UWAgent.UWMonitor " +
			   "port# " +               // port# used by UWPlace
			   "[as] " +               // agent status
			   "[kill agentId] " +     // kill a specific agent
			   "[suspend agentId] " +  // suspend a specific agent
			   "[resume agentId] " +   // resume a specific agent
			   "[h]");                 // help
	System.err.println("Note: " + 
			   "UWAgent.jar must exist in the same directory");
    }
}
