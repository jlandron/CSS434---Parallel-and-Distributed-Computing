package UWAgent;

/**
 * Is a mailbox allocated to each agent so as to exchange inter-agent messages.
 * 
 * @author Eric Nelson and Duncan Smith (CSS, UWBothell)
 * @since  10/1/04
 * @since  8/4/06
 */

// UWAgentMailbox ... The engine which manages the mobility of UWMessages
// throughout the UWMessagingSystem.
import java.util.*;
import java.net.*;
import java.lang.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.lang.reflect.*;
import java.net.InetAddress;

public class UWAgentMailbox {
    // Variables //////////////////////////////////////////////////////////////
    // This UWAgentMailbox is initialized with a corresponding UWAgent 
    // and access list (i.e., a directory owned by the UWAgent). The access 
    //  list will be updated as necessary by the UWAgent.
    private UWAgent agent;

    private static String mailboxName = "UWAgentMailbox";     // mailbox name
    /**
     * IP port number used for inter-agent communication.
     */
    public static String portnumber = UWUtility.DEFAULT_PORT; // ip port

    // Constructors ///////////////////////////////////////////////////////////
    /**
     * Is the default constructor. It is not used as of 8/4/06.
     */
    public UWAgentMailbox( ) {
	super( );
    	UWUtility.LogEnter( );
    	UWUtility.LogExit( );
    }

    /**
     * Creates a new mailbox and add it to a given agent.
     * @param ag the agent to associate with a new mailbox
     */
    public UWAgentMailbox( UWAgent ag ) {
	super( );
	this.agent = ag;
    	UWUtility.LogEnter( "agent name = " + ag.getName( ) );
    	UWUtility.LogExit( );
    }
    
    // Get and put utilities //////////////////////////////////////////////////
    /**
     * Gives a name to this mailbox.
     * @param name the name to give to this mailbox.
     */
    public void setMailboxName( String name ) {
    	UWUtility.LogEnter( "name = " + name );
    	mailboxName = name;
    	UWUtility.LogExit( );
    }

    /**
     * Retrieves this mailbox name.
     * @return the mailbox name.
     */
    public String getMailboxName( ) {
    	UWUtility.LogEnter( "name = " + mailboxName );
    	UWUtility.LogExit( );
    	return mailboxName;
    }

    /**
     * Sets the IP port to this mailbox.
     * @param port IP port number in String.
     */
    public void setPortNumber( String port ) {
    	UWUtility.LogEnter( "p = " + port );
    	UWUtility.LogExit( );
    	portnumber = port;
    }

    /**
     * Retrieves this mailbox IP port.
     * @return the IP port assigned to this mailbox in String.
     */
    public String getPortNumber( ) {
    	UWUtility.LogEnter( "portnumber = " + portnumber );
    	UWUtility.LogExit( );
    	return portnumber;
    }

    // Message-sending functions //////////////////////////////////////////////
    /**
     * Notifies a given agent of the local agent's new location. This function
     * should be used if a recipient agent is not behind a gateway, 
     *
     * @param senderId sending agent id
     * @param senderInet sending agent inet address
     * @param receiverId recipient agent id
     * @param recieverInet recipient agent inet address 
     * @param senderIp  sending agent's ip address
     * @param senderTime sending agent's time stamp
     * @return true if a notification has been completed successfully.
     */
    public boolean notifyAgentLocation( int senderId, 
					InetAddress senderInet, 
					int receiverId, 
					InetAddress receiverInet, 
					InetAddress senderIp, 
					long senderTime ) {
    	return notifyAgentLocation( senderId, senderInet, 
				    receiverId, receiverInet, 
				    senderIp, senderTime, null );
    }	
    
    /**
     * Notifies a given agent of the local agent's new location. This function
     * should be used if a recipient agent is behind a gateway, 
     *
     * @param senderId sending agent id
     * @param senderInet sending agent inet address
     * @param receiverId recipient agent id
     * @param recieverInet recipient agent inet address 
     * @param senderIp  sending agent's ip address
     * @param senderTime sending agent's time stamp
     * @param gateway all gateways re route to the recipient
     * @return true if a notification has been completed successfully.
     */
    public boolean notifyAgentLocation( int senderId, 
					InetAddress senderInet, 
					int receiverId, 
					InetAddress receiverInet, 
					InetAddress senderIp, // not used now
					long senderTime,      // not used now
					String[] gateway ) {
	
	boolean success = false;
	UWPSocket socket = null;
	OutputStream out = null;
	try {
	    UWUtility.LogEnter( "sending agent id = " + senderId + 
				", sending agent address = " + senderInet +
				", recipient agent id = " + receiverId + 
				", recipient agent address = " + receiverInet +
				", sending ip = " + senderIp );
	    
	    // Encode the sender agent's address and agent id
	    if ( senderInet.getHostAddress( ).equals( "127.0.0.1" ) ) {
		// get a public ip.
		String addr = getIpAddress( senderInet.getCanonicalHostName( ) );
		senderInet = InetAddress.getByName( addr );
	    }
	    byte[] bytRawAddress = senderInet.getAddress( );	
	    ByteBuffer recvIdBuff = ByteBuffer.allocate( UWUtility.INT_SIZE );
	    recvIdBuff.putInt( receiverId ); // recipient agent id

	    if ( gateway == null ) {	
		// Call registerAgentIp at the remote resource
		socket = agent.getPlace( ).getUWPSocket( );
		out =
		    socket.initUWPSocket( UWUtility.MSG_TYPE_FUNC,     // type
					  receiverInet.getCanonicalHostName( ), // dest
					  getPortNumber(),   // port
					  "registerAgentIp", // func
					  senderId, // param 1: sender id
					  0,        // param 2: gateway = 0
					  agent.getIsSSL( ) );	
		// write the byte[] representation of the sender InetAddress
		out.write( bytRawAddress ); 
		// write recipient agent id
		out.write( recvIdBuff.array( ) ); 

		byte[] gateways = new byte[1];
		// no gateway names to write, but need to do a final write 
		// operation so the recipient doesn't block
		out.write( gateways );					
		socket.returnOutputStream( out );

	    } else {				
		// Call registerAgentIpGateway at the remote resource
		UWUtility.LogInfo("notifyAgentLocation, gateway != null");
		// Encode gateway array
		String gatewayNames = "";
		for ( int i=0; i < gateway.length; i++ ) {
		    // add trailing space as a delimiter
		    gatewayNames = gatewayNames + gateway[i] + " ";	
		}
		byte[] bytGatewayNames = gatewayNames.getBytes( "UTF8" );
		UWUtility.LogInfo( "gateway.length = " + gateway.length );
		UWUtility.LogInfo( "gatewayNames = " + gatewayNames );
		
		// Send gateway list position (starting at the end of the list)
		ByteBuffer gwPosBuff 
		    = ByteBuffer.allocate( UWUtility.INT_SIZE );
		int gwPos = gateway.length - 1;
		gwPosBuff.putInt( gwPos );	
		UWUtility.LogInfo( "gwPos = " + gwPos );
		
		// Destination host name
		byte[] bytHostName = new byte[UWUtility.HOSTNAME_SIZE];
		byte[] bytGetHostName 
		    = receiverInet.getCanonicalHostName( ).getBytes( "UTF8" );
		for ( int i=0; i < bytGetHostName.length; i++ ) {
		    bytHostName[i] = bytGetHostName[i];
		}
		for ( int i = bytGetHostName.length; 
		      i < UWUtility.HOSTNAME_SIZE; i++ ) {
		    bytHostName[i] = ( byte )' '; // pad with spaces
		}

		// for logging
		UWUtility.LogInfo( "bytRawAddress.length = " + 
				   bytRawAddress.length );
		UWUtility.LogInfo( "bytGetHostName.length = " + 
				   bytGetHostName.length );
		UWUtility.LogInfo( "bytHostName.length = " + 
				   bytHostName.length );
		UWUtility.LogInfo( "gateway = " + gateway );
		UWUtility.LogInfo( "gateway[gwPos] = " + gateway[gwPos] );

		socket = agent.getPlace( ).getUWPSocket( );
		out =
		    socket.initUWPSocket( UWUtility.MSG_TYPE_FUNC,  // type
					  gateway[gwPos],           // dest
					  getPortNumber( ),         // port
					  "registerAgentIpGateway", // func
					  senderId,               // param 1
					  bytGatewayNames.length, // param 2
					  agent.getIsSSL( ) );
		
		out.write( bytRawAddress );       // write sender's inet addr
		out.write( bytGatewayNames );     // write gateway array
		out.write( bytHostName) ;	  // write dest's inet addr
		out.write( gwPosBuff.array( ) );  // write gateway position
		out.write( recvIdBuff.array( ) ); // write recv's agent id
		socket.returnOutputStream( out );
	    }
	    success = true;
	}
	catch ( Exception e ) {
	    UWUtility.Log( e.toString( ) );
	    UWUtility.Log( "Cause: " + e.getCause( ) );
	    success = false;
	    if ( socket != null && out != null )
		socket.returnOutputStream( out );
	}
    	UWUtility.LogExit( );
	return success;
    }

    /**
     * Has a given agent cache the local agent's new location.
     *
     * @param senderId sending agent id
     * @param senderInet sending agent inet address
     * @param receiverId recipient agent id
     * @param recieverInet recipient agent inet address 
     * @return true if caching has been completed successfully.
     */
    public boolean cacheAgentLocation( int senderId, 
				       InetAddress senderInet, 
				       int receiverId, 
				       InetAddress receiverInet ) {

	boolean success = false;
	UWPSocket socket = null;
	OutputStream out = null;
	try {
	    UWUtility.LogEnter( "sending agent id = " + senderId + 
				", sending agent address = " + senderInet +
				", recipient agent id = " + receiverId + 
				", recipient agent address = " + receiverInet 
				);
	    
	    // Encode the sender agent's address and agent id
	    if ( senderInet.getHostAddress( ).equals( "127.0.0.1" ) ) {
		// get a public ip.
		String addr = getIpAddress( senderInet.getCanonicalHostName( ) );
		senderInet = InetAddress.getByName( addr );
	    }
	    byte[] bytRawAddress = ( senderInet != null ) ? 
		senderInet.getAddress( ) : new byte[32];
	    ByteBuffer recvIdBuff = ByteBuffer.allocate( UWUtility.INT_SIZE );
	    recvIdBuff.putInt( receiverId ); // recipient agent id

	    // Call cacheAgentIp at the remote resource
	    socket = agent.getPlace( ).getUWPSocket( );
	    out =
		socket.initUWPSocket( UWUtility.MSG_TYPE_FUNC,     // type
				      receiverInet.getCanonicalHostName( ), // dest
				      getPortNumber(),             // port
				      "cacheAgentIp",              // func
				      senderId,      // param 1: sender id
				      ( senderInet != null ) ? 0 : -1,
				       // param 2: 0 cache, -1 flush
				      agent.getIsSSL( ) );	
	    // write the byte[] representation of the sender InetAddress
	    out.write( bytRawAddress ); 
	    // write recipient agent id
	    out.write( recvIdBuff.array( ) ); 

	    // close the socket
	    socket.returnOutputStream( out );

	    success = true;
	}
	catch ( Exception e ) {
	    UWUtility.LogInfo( e.toString( ) );
		    success = false;
	    if ( socket != null )
		socket.returnOutputStream( out );
	}
    	UWUtility.LogExit( );
	return success;
    }

    // User-level message exchanging functions ////////////////////////////////
    /**
     * Puts an incoming message into this local agent's message queue.
     * @param message an incoming message to enqueue.
     */
    public void enqueueMessage( UWMessage message ) {
    	UWUtility.LogEnter( );
    	UWUtility.LogExit( );
	agent.enqueueMessage( message );
    }

    /**
     * Send a specified UWMessage to the destination UWAgentMailbox specified 
     * within this UWMessage. Note that receivingIpAddr and receivingAgentId 
     * may not specify the ultimate message destination.
     * Information about the ultimate destination is stored in the message 
     * object. This function is called by UWAgent.java.
     *
     * @param message          a message to send
     * @param receivingIpAddr  the inet address of the final destination, the
     *                         next gateway, or the next hop agent
     * @param receivingAgentId the id of final destination agent or the next
                               hop agent
     * @param time             the source agent's timestamp
     * @return true if a message was forwarded in success.
     */
    public boolean sendMessage( UWMessage message, 
				InetAddress receivingIpAddr, 
				int receivingAgentId, long time ) {
    	UWUtility.LogInfo( "header = " + message.getMessageHeader( )[0] +
			   "receivingIpAddr = " + receivingIpAddr );

	String name = Thread.currentThread().getName( );

	boolean success = false;
	UWPSocket socket = null;
	OutputStream out = null;
	try {
	    String[] receivingGateways = message.getReceivingGateways( );

	    // Write the byte representation of the message to the byte output
	    // stream.
	    ByteArrayOutputStream baos = new ByteArrayOutputStream( );
	    
	    ObjectOutputStream oos = 
		new ObjectOutputStream( new BufferedOutputStream( baos ) );

	    oos.writeObject( message );
	    oos.close( );
	    byte[] bytMessage =  baos.toByteArray( );
	    baos.close( );
	    UWUtility.LogInfo( "UWAgentMailbox.sendMessage: " +
			       Thread.currentThread( ) + 
			       "will send bytMessage = " + bytMessage );

	    if ( receivingGateways == null ) {
		// direct transfer to the final destination
		UWUtility.LogInfo( "UWAgentMailbox.sendMessage: " + 
				   "Directly calling enqueueMessage on " + 
				   receivingIpAddr.getCanonicalHostName( ) +
				   " for receiving agent Id " + 
				   receivingAgentId + 
				   "; SSL = " + agent.getIsSSL( ) );
		
		socket = agent.getPlace( ).getUWPSocket( );
		out =
		    socket.initUWPSocket( UWUtility.MSG_TYPE_FUNC,   // type
					  receivingIpAddr.getCanonicalHostName( ), //dst
					  getPortNumber( ),         // port
					  "enqueueMessage",         // func
					  receivingAgentId,         // param 1
					  bytMessage.length,        // param 2
					  agent.getIsSSL( ) );

		out.write( bytMessage ); 

                success = true;
		// receive an ack if it is not a system message.
		if ( !message.getIsSystemMessage( ) ) {
		    int ack = socket.getAckFromServer( out );
		    String[] header = message.getMessageHeader( );
		    UWUtility.LogInfo( "sendMessage: org = " +
				       message.getSendingAgentId( ) +
				       " via = " + agent.getAgentId( ) +
				       " dst = " +
				       message.getReceivingAgentId( ) +
				       " ack = " + ack +
				       " header = " + header[0] 
				       );

		    success = (ack == 1 ) ? true : false;
		}

		socket.returnOutputStream( out );

	    } else {
		// message forwarding to the next gateway
		UWUtility.LogInfo( "UWAgentMailbox.sendMessage: " +
				   "transfer to gateways" );
		for ( int i = 0; i < receivingGateways.length; i++ )
		    System.err.println( "\t gateway[" + i + "]= " + 
					receivingGateways[i] );

		// Encode gateway array
		String gatewayNames = "";
		for ( int i=0; i < receivingGateways.length; i++ ) {
		    // add trailing space as a delimiter
		    gatewayNames = gatewayNames + receivingGateways[i] + " ";
		}
		byte[] bytGatewayNames = gatewayNames.getBytes( "UTF8" );
		UWUtility.LogInfo( "receivingGateways.length = " + 
				   receivingGateways.length );
		UWUtility.LogInfo( "gatewayNames = " + gatewayNames );
		
		// Encode gateway list position (starting at the end of the 
		// list)
		ByteBuffer gwPosBuff 
		    = ByteBuffer.allocate( UWUtility.INT_SIZE );
		int gwPos = receivingGateways.length - 1;
		gwPosBuff.putInt( gwPos );	
		UWUtility.LogInfo( "gwPos = " + gwPos );
		
		// Encode receiving agent ID
		ByteBuffer receivingAgentBuff 
		    = ByteBuffer.allocate( UWUtility.INT_SIZE );
		receivingAgentBuff.putInt( receivingAgentId );	
		UWUtility.LogInfo( "receivingAgentId = " + receivingAgentId );
		
		// Destination host name
		byte[] bytHostName = new byte[UWUtility.HOSTNAME_SIZE];
		byte[] bytGetHostName 
		    = receivingIpAddr.getCanonicalHostName( ).getBytes( "UTF8" );
		for ( int i=0; i < bytGetHostName.length; i++ ) {
		    bytHostName[i] = bytGetHostName[i];
		}
		for (int i = bytGetHostName.length; 
		     i < UWUtility.HOSTNAME_SIZE; i++ ) {
		    bytHostName[i] = ( byte )' '; // pad with spaces
		}

		// logging information
		UWUtility.LogInfo( "receivingGateways[gwPos] = " + 
				   receivingGateways[gwPos] );
		UWUtility.LogInfo( "gatewayNames = " + gatewayNames );
		UWUtility.LogInfo( "bytHostName.length = " + 
				   bytHostName.length );
		UWUtility.LogInfo( "bytGetHostName.length = " + 
				   bytGetHostName.length );
		UWUtility.LogInfo( "gwPos = " + gwPos );
		UWUtility.LogInfo( "receivingAgentId = " + receivingAgentId );
		
		// now send them through a socket
		socket = agent.getPlace( ).getUWPSocket( );
		out =
		    socket.initUWPSocket( UWUtility.MSG_TYPE_FUNC,  // type
					  receivingGateways[gwPos], // dest
					  getPortNumber( ),         // port
					  "enqueueMessageGateway",  // func
					  bytMessage.length,        // param 1
					  bytGatewayNames.length,   // param 2
					  agent.getIsSSL( ) ); 

		out.write( bytGatewayNames );    // write gateway array
		out.write( bytHostName );        // write destination host name
		out.write( gwPosBuff.array( ) ); // write gateway position
		out.write( receivingAgentBuff.array( ) ); // write recv agentID
		out.write( bytMessage );        // write the UWMessage 
		
                success = true;
		// receive an ack if it is not a system message.
		if ( !message.getIsSystemMessage( ) ) {
		    int ack = socket.getAckFromServer( out );

		    String[] header = message.getMessageHeader( );
		    UWUtility.LogInfo( "UWAgentMailbox.sendMessage: org = " +
				       message.getSendingAgentId( ) +
				       " via = " + agent.getAgentId( ) +
				       " dst = " +
				       message.getReceivingAgentId( ) +
				       " ack = " + ack +
				       " header = " + header[0] 
				       );

		    success = (ack == 1 ) ? true : false;
		}

		socket.returnOutputStream( out );
	    }			
	    
	    // Indicates message went to new UWAgentMailbox successfully
	    UWUtility.LogInfo( message.getSendingAgentId( ) + 
			       " sends message to " + receivingAgentId );
	}
	catch ( Exception e ) {
	    e.printStackTrace( );
	    UWUtility.Log( "UWAgentMailbox.sendMessage: " + e.toString( ) + 
			   " ... returns false to a calling function" );
	    success = false;
	    if ( socket != null )
		socket.returnOutputStream( out );
	}
	
	UWUtility.LogExit( );
	return success;
    }

    // Utility ////////////////////////////////////////////////////////////////
    /**
     * Retrieves the ip address rather than 127.0.0.1 corresponding to a given
     * ip name.
     *
     * @param ipName an ip name whose address is in query.
     * @return the ip address corresponding to the given ip name.
     */
    private String getIpAddress ( String ipName ) throws IOException {
        // lauch "host ipName" as an independent process
        Runtime runtime = Runtime.getRuntime( );
        String cmd[] = new String[2];
        cmd[0] = "host";
        cmd[1] = ipName;
        Process process = runtime.exec( cmd );

        // retrieve the standard output from the process
        InputStream in = process.getInputStream( );
        BufferedReader bufferedInput
            = new BufferedReader( new InputStreamReader( in ) );
        String line = bufferedInput.readLine( );

        // parse the output and obtain the last word.
        StringTokenizer tokenizer = new StringTokenizer( line, " " );
        String token = null;
        while ( tokenizer.hasMoreTokens( ) ) {
            token = tokenizer.nextToken( );
        }

        // wait for the termination of the process.
        try {
            process.waitFor( );
        } catch ( InterruptedException e ) {
        }

        // return the last word, (i.e., the ip address.)
        return token;
    }

    
    // Obsolete functions /////////////////////////////////////////////////////
    /**
     * Retrieves a given agent's inet address.
     * @param recipId the id of an agent whose inet address is in query.
     * @return the inet address of the agent specified in recipId.
     */
    public InetAddress getAgentIp( int recipId ) {
    	UWUtility.LogEnter( "recipId = " + recipId );
    	UWUtility.LogExit( );
		return agent.getAgentIp( recipId );
    }

    /**
     * Sets a pair of agent id and inet address to the local agent's directory.
     * @param agentId an agent id to register.
     * @param location the corresponding inet address.
     */
    public boolean registerAgentIp( int agentId, InetAddress location ) {
    	UWUtility.LogEnter( "agentId = " + agentId + 
			    ", location = " + location );
    	UWUtility.LogExit( );
	return agent.registerAgentIp( agentId, location );
    }

    /**
     * Schedules an immediate terminatioin. This signal is fowarded to all the
     * descendants of this local agent.
     */
    public void setTerminationRequest( ) {
    	UWUtility.LogEnter( );
    	UWUtility.LogExit( );

    	agent.setTerminationRequest( );
    }
}
