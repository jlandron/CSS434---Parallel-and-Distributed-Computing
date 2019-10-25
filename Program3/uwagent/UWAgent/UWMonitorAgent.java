package UWAgent;

/**
 * UWMonitorAgent receives monitoring optionis as its arguments and
 * interacts with the UWPlace daemon where it was injected, so as to
 * control other agents:
 *
 * <pre>
 * java UWInject -cp UWAgent.jar UWInject -p port# desthost UWMonitorAgent
 * [as][kill agentId][suspend agentId] [resume agentId][help]
 * </pre>
 *
 * where
 * <ul>
 * <li> as: show agent status
 * <li> kill: kill a given agent
 * <li> suspend: suspend a given agent
 * <li> resume: resume a given agent
 * <li> help: help

 * @author    Duncan Smith (CSS, University of Washington, Bothell)
 * @since     12/10/05
 * @version   7/31/06  
 * </ul>
 */
import java.io.*;
import java.util.Map;
import java.util.List;

public class UWMonitorAgent extends UWAgent implements Serializable {

    UWPlace uwp;             // a reference to the local UWPlace
    String[] commandArgs;    // a monitoring command and its arguments

    /**
     * Is the default constructor that does nothing.
     */
    public UWMonitorAgent( ) {}
    
    /**
     * Is the constructor that accepts a monitoring command.
     * @param args a monitoring command and its arguments.
     */
    public UWMonitorAgent( String[] args ) {
	commandArgs = args;
    }
    
    public void init( ) {
	try {
	    uwp = getPlace( );
	    if ( commandArgs[0].equals( "as" ) ) {
		// show agent status at the local place.
		uwp.agentStatus();
	    } else if ( commandArgs[0].equals( "kill" ) ) {
		// kill a given agent
		uwp.killAgent(Integer.parseInt( commandArgs[1] ) );
	    } else if ( commandArgs[0].equals( "suspend" ) ) {
		// suspend a given agent
		uwp.suspendAgent( Integer.parseInt( commandArgs[1] ) );
	    } else if ( commandArgs[0].equals( "resume" ) ) {
		// resume a given agent
		uwp.resumeAgent( Integer.parseInt( commandArgs[1] ) );
	    } else if ( commandArgs[0].equals( "help" ) ) {
		// show usage
		displayUsage( );
	    }
	}
	catch (Exception e) {
	    UWUtility.Log( "UWMonitorAgent.init: " + e );
	    e.printStackTrace( );
	    displayUsage( );
	}
    }
    
    /**
     * Shows the usage.
     */
    private static void displayUsage( ) {
	System.err.println( "Usage: $ java -cp UWAgent.jar UWAgent.UWInject" +
			    "-p port# " +
			    "desthost UWAgent.UWMonitor " +
			    "[as] [kill agentId] " +
			    "[suspend agentId] [resume agentId] " +
			    "[help]");
    }
}
