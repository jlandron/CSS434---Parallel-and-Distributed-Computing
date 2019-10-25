package UWAgent;

/**
 * UWMessages are passed from AgentMailbox to AgentMailbox.
 * A UWMessage is uniquely identified within the system by
 * a combination of sendingAgentId and sendingTimestamp.
 * 
 * @author    Eric L. Nelson and Duncan Smith (CSS, UW Bothell)
 * @since    10/01/04
 * @version  8/01/06
 */

import java.io.*;
import java.util.*;
import java.net.InetAddress;

public class UWMessage implements Serializable {
    // Agents involved in message exchange
    private int sendingAgentId;
    private int receivingAgentId;
    
    // Machines (hosts) involved in message exchange
    private InetAddress sendingIp;
    private InetAddress receivingIp;
    private String sendingHostName;
    private String receivingHostName;
    
    // Timestamp when message was sent / received
    private long sendingTimestamp;
    private long receivingTimestamp;
    
    // Gateway path required to transfer this message to the recipient
    private String[] gateways;
    
    // Sending agent's name involved in message exchange
    private String sendingAgentName;
    
    // If true, this message is intended for use by the execution engine,
    // not a user agent
    private boolean isSystemMessage;

    // User-defined message header and body
    private String [] messageHeader;
    private Hashtable< String, Object> message;

    // agent IDs and IPs en route to the final destination
    private Vector<Integer> routingIds = new Vector<Integer>( );
    private Vector<InetAddress> routingIps = new Vector<InetAddress>( );
    
    /**
     * Is a UWMessage constructor that creates an empty message header and 
     * body with no sending/receiving agent identifiers.
     */
    public UWMessage( ) {
	sendingAgentId = -1;
	receivingAgentId = -1;
	try {
	    sendingIp = InetAddress.getLocalHost( );
	    receivingIp = InetAddress.getLocalHost( );
	} catch( java.net.UnknownHostException e ) { }
	sendingHostName = "localhost";
	receivingHostName = "localhost";
	sendingTimestamp = -1;
	receivingTimestamp = -1;
	messageHeader = null;
	message = null;
	sendingAgentName = null;
    }
    
    /**
     * Is a UWMessage constructor that creates a message header only with
     * the sending agent identifier.
     *
     * @param agent The UWAgent that is composing this UWMessage
     * @param mHeader The String that contains this UWMessage header
     */
    public UWMessage( UWAgent agent, String mHeader ) {
	sendingAgentId = agent.getAgentId( );
	receivingAgentId = -1;
	try {
	    sendingIp = InetAddress.getLocalHost( );
	    receivingIp = InetAddress.getLocalHost( );
	} catch( java.net.UnknownHostException e ) { }
	sendingHostName = "localhost";
	receivingHostName = "localhost";
	sendingTimestamp = -1;
	receivingTimestamp = -1;
	messageHeader = new String[1];
	messageHeader[0] = mHeader;
	message = null;
	sendingAgentName = agent.getName( );
    }
    
    /**
     * Is a UWMessage constructor that composes multiple message headers with
     * the sending agent identifier.
     *
     * @param agent The UWAgent that is composing this UWMessage
     * @param mHeader The String array that contains this UWMessage header
     */
    public UWMessage( UWAgent agent, String [] mHeader ) {
	sendingAgentId = agent.getAgentId( );
	receivingAgentId = -1;
	try {
	    sendingIp = InetAddress.getLocalHost( );
	    receivingIp = InetAddress.getLocalHost( );
	} catch( java.net.UnknownHostException e ) { }
	sendingHostName = "localhost";
	receivingHostName = "localhost";
	sendingTimestamp = -1;
	receivingTimestamp = -1;
	messageHeader = mHeader;
	message = null;
	sendingAgentName = agent.getName( );
    }
    
    /**
     * Is a UWMessage constructor that creates a message header and body
     * with the sending agent identifier.
     *
     * @param agent The UWAgent that is composing this UWMessage
     * @param mHeader The String that contains this UWMessage header
     * @param m The Hashtable containing this UWMessage body
     */
    public UWMessage( UWAgent agent, String mHeader, 
		      Hashtable<String, Object> m ) {
	sendingAgentId = agent.getAgentId( );
	receivingAgentId = -1;
	try {
	    sendingIp = InetAddress.getLocalHost( );
	    receivingIp = InetAddress.getLocalHost( );
	} catch( java.net.UnknownHostException e ) { }
	sendingHostName = "localhost";
	receivingHostName = "localhost";
	sendingTimestamp = -1;
	receivingTimestamp = -1;
	messageHeader = new String[1];
	messageHeader[0] = mHeader;
	message = m;
	sendingAgentName = agent.getName( );
    }
    
    /**
     * Is a UWMessage constructor that composes a message of multiple headers,
     * a body, and the sending agent's identifier.
     *
     * @param agent The UWAgent that is composing this UWMessage
     * @param mHeader The String array that contains this UWMessage header
     * @param m The Hashtable containing this UWMessage body
     */
    public UWMessage( UWAgent agent, String [] mHeader, 
		      Hashtable<String, Object> m ) {
	sendingAgentId = agent.getAgentId( );
	receivingAgentId = -1;
	try {
	    sendingIp = InetAddress.getLocalHost( );
	    receivingIp = InetAddress.getLocalHost( );
	} catch( java.net.UnknownHostException e ) { }
	sendingHostName = "localhost";
	receivingHostName = "localhost";
	sendingTimestamp = -1;
	receivingTimestamp = -1;
	messageHeader = mHeader;
	message = m;
	sendingAgentName = agent.getName( );
    }
    
    /**
     * Retrieves the sending agent identifier. 
     * @return The id of the UWAgent that is the sender of this UWMessage
     */
    public int getSendingAgentId( ) { return sendingAgentId; }
    
    /**
     * Retrieves the receiving agent identifier.
     * @return The id of the UWAgent that is the recipient of this UWMessage
     */
    public int getReceivingAgentId( ) { return receivingAgentId; }
    
    /**
     * Retrieves the sending ip address
     * @return The source IP address of this UWMessage
     */
    public InetAddress getSendingIp( ) { return sendingIp; }
    
    /**
     * Retrieves the destination ip address
     * @return The destination IP address of this UWMessage
     */
    public InetAddress getReceivingIp( ) { return receivingIp; }
    
    /**
     * Retrieves a set of gateways en route to the final destination.
     * @return The gateway path required to transfer this message to the 
     *         recipient
     */
    public String[] getReceivingGateways( ) { return gateways; }
    
    /**
     * Retrieves the sending host name.
     * @return The source host name of this UWMessage
     */
    public String getSendingHostName( ) { return sendingHostName; }
    
    /**
     * Retrieves the receiving host naem.
     * @return The destination host name of this UWMessage
     */
    public String getReceivingHostName( ) { return receivingHostName; }
    
    /**
     * Retreives the timestamp indicating when this UWMessage was sent.
     * @return The timestamp indicating when this UWMessage was sent.
     */
    public long getSendingTimestamp( ) { return sendingTimestamp; }
    
    /**
     * Retrieves the timestamp indicating when this UWMessage was received.
     * @return The timestamp indicating when this UWMessage was received.
     */
    public long getReceivingTimestamp( ) { return receivingTimestamp; }
    
    /**
     * Retrieves the message header.
     * @return The message header of this UWMessage.
     */
    public String [] getMessageHeader( ) { return messageHeader; }
    
    /**
     * Retrieves the message body.
     * @return The message body of this UWMessage
     */
    public Hashtable<String, Object> getMessage( ) { return message; }
    
    /**
     * Retrieves an array of keys of the hashtable message body.
     * @return The keys of the Hashtable message body of this UWMessage
     */
    public String [] getMessageKeys( ) { 
	Set s = message.keySet( );
	Enumeration e = message.keys( );
	String [] keys = new String[s.size( )];
	for( int i = 0; i < keys.length; i++ )
	    keys[i] = ( String )( e.nextElement( ) );
	return keys;
    }
    
    /**
     * Retrieves a message value corresponding to a given key.
     * @param key The key to locate the corresponding Object value within the 
     *            Hashtable message body
     * @return The message body Object associated with the specified key
     */
    public Object getMessageValue( String key ) {
	return message.get( key );
    }
    
    /**
     * Sets the sending agent identifier in String.
     * @param s The id of the UWAgent sending this UWMessage
     */
    public void setSendingAgentId( int s ) { sendingAgentId = s; }
    
    /**
     * Sets the receiving agent identifier in String.
     * @param r The id of the UWAgent recieving this UWMessage
     */
    public void setReceivingAgentId( int r ) { receivingAgentId = r; }
    
    /**
     * Sets the sending IP address.
     * @param s The InetAddress value to set the IP address of the UWAgent 
     *          sending this UWMessage
     */
    public void setSendingIp( InetAddress s ) { sendingIp = s; }
    
    /**
     * Sets the receiving IP address.
     * @param r The InetAddress value to set the IP address of the UWAgent 
     *          receiving this UWMessage
     */
    public void setReceivingIp( InetAddress r ) { receivingIp = r; }
    
    /**
     * Sets an array of gateway IP names en route to the final destination.
     * @param g Gateway path required to transfer this message to the recipient
     */
    public void setReceivingGateways(String[] g) { gateways = g; }
    
    /**
     * Sets the source host name in String.
     * @param s The String value to set the host name of the UWAgent 
     *          sending this UWMessage
     */
    public void setSendingHostName( String s ) { sendingHostName = s; }
    
    /**
     * Sets the destination host name in String
     * @param r The String value to set the host name of the UWAgent 
     *          receiving this UWMessage
     */
    public void setReceivingHostName( String r ) { receivingHostName = r; }
    
    /**
     * Sets the timestamp indicating when this message was sent.
     * @param s The long value to set the timestamp indicating when
     *          this UWMessage was sent
     */
    public void setSendingTimestamp( long s ) { sendingTimestamp = s; }
    
    /**
     * Sets the timestamp indicating when this message was received.
     * @param r The long value to set the timestamp indicating when
     *          this UWMessage was received
     */
    public void setReceivingTimestamp (long r ) { receivingTimestamp = r; }
    
    /**
     * Defines a header in this message.
     * @param mHeader The String value to set the header of this UWMessage
     */
    public void setMessageHeader( String mHeader ) {
	messageHeader = new String[1];
	messageHeader[0] = mHeader;
    }
    
    /**
     * Defines a header in this message.
     * @param mHeader The String array value to set the header
     */
    public void setMessageHeader( String [] mHeader ) { 
	messageHeader = mHeader; 
    }
    
    /**
     * Defines a body in this message.
     * @param m The Hashtable value to set the body of this UWMessage
     */
    public void setMessage( Hashtable<String, Object> m ) { message = m; }
    
    /**
     * Defines an empty body, (i.e., an empty hashtable) in this message.
     */
    public void clearMessage( ) { message = new Hashtable<String, Object>( ); }
    
    /**
     * Nullifies the message header.
     */
    public void clearMessageHeader( ) { messageHeader = null; }
    
    /**
     * Adds a message key and its corresponding value to the message body.
     * @param key The String to identify the corresponding Object value
     *            within the Hashtable message body of this UWMessage
     * @param value The Object corresponding to the specified key
     *            within the Hashtable message body of this UWMessage
     */
    public void addMessage( String key, Object value ) {
	message.put( key, value );
    }
    
    /**
     * Removes a message value corresing to a givne key from the message body.
     * @param key The String to identify the corresponding Object value to be
     *            removed from the Hashtable message body of this UWMessage
     */
    public void removeMessage( String key ) {
	message.remove( key );
    }
    
    /**
     * Retrieves the source agent's name.
     * @return the source agent's name
     */
    public String getSendingAgentName( ) {
	return sendingAgentName;
    }
    
    /**
     * Sets the source agent's name.
     * @param agentName the name of the source agent (in String)
     */
    public void setSendingAgentName( String agentName ) {
	this.sendingAgentName = agentName;
    }
    
    /**
     * Sets if this message is a system (true) or a user (false) message.
     * @param value true if it is a system message, otherwise false.
     */
    public void setIsSystemMessage( boolean value ) {
    	this.isSystemMessage = value;
    }
    
    /**
     * Checks if this message is a system (true) or a user (false) level.
     * @return true if it is a system message, otherwise false.
     */
    public boolean getIsSystemMessage( ) {
	return this.isSystemMessage;
    }    

    /**
     * Inserts a new pair of routing id and inet address.
     * @param id a new routing agent's id
     * @param ip a new routing agent's ip
     */
    public void insertRouteInfo( int id, InetAddress ip ) {
	routingIds.add( new Integer( id ) );
	routingIps.add( ip );
    }

    /**
     * Return the number of routing agents
     * @return the number of routing agents
     */
    public int getNumRoutes( ) {
	// validate routingIds and routingIps
	if ( routingIds.size( ) != routingIps.size( ) )
	    return 0;
	return routingIds.size( );
    }

    /**
     * Returns the id of the routing agent specified with index
     * @param index the index of the routing agent whose id is in query.
     * @return the id of the routing agent specified with index
     */
    public int getRouteId( int index ) {
	// validate index
	if ( routingIds.size( ) == 0 ||
	     index < 0 || index >= routingIds.size( ) )
	    return -1;
	return routingIds.elementAt( index ).intValue( );
    }

    /**
     * Returns the inet address of the routing agent specified with index.
     * @param index the index of the routing agent whose inet address is in 
     *              query
     * @return the inet address of the routing agent specified with index
     */
    public InetAddress getRouteIp( int index ) {
	// validate index
	if ( routingIps.size( ) == 0 ||
	     index < 0 || index >= routingIps.size( ) )
	    return null;
	return routingIps.elementAt( index );
    }

    /**
     * Prints out all routing information
     */
    public void showRouteInfo( ) {
	System.err.println( "message: " + messageHeader[0] );
	System.err.println( "\tsource: id = " + sendingAgentId +
			    " ip = " + sendingIp );
	for ( int i = 0; i < routingIds.size( ); i++ ) {
	    System.err.println( "\troute[" + i + "]: id = " +
				routingIds.elementAt( i ).intValue( ) + 
				" ip = " + 
				routingIps.elementAt( i ) );
	}
    }
}
