package UWAgent;

/**
 * Is an abstract class to create a UWAgent agent.
 *
 * @author  Koichi Kashiwagi, Duncan Smith, Munehiro Fukuda (CSS, UWBothell)
 * @author  Jumpei Miyauchi (Ehime Univ)
 * @since   10/1/04
 * @version 5/23/06
 */

import java.util.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;

public abstract class UWAgent implements Serializable {
    // Variables /////////////////////////////////////////////////////////////
    // UWAgents can communicate through one "personal" trusted UWAgentMailbox
    private transient UWAgentMailbox mailbox;
    // Check if messages are in UWAgentMailbox
    private boolean noMoreMessageWaiting = false;

    // The UWAgentMailbox pushes the incoming messages to this Vector.
    // A UWMessage is uniquely identified within the system by
    // a combination of sendingAgentId and sendingTimestamp.
    private Vector<UWMessage> messages = new Vector<UWMessage>( );

    // Current UWPlace that this UWAgent is working at.
    private transient UWPlace place;

    // Each agent should be located with its class name, its client user name, 
    // and a system-unique agent identifier.  
    // The agent identifier consists of 
    //     an IP address (32 bits), 
    //     a time stamp, and 
    //     a String agentId.
    private String agentName;      // class name
    private String clientUserName;
    private InetAddress ip;
    private long timeStamp = 0;
    private int agentId;
    private String packagePath;    // this agent's package path

    // When an agent hops to another node, it won't terminate soon. This is
    // because its children may send messages to this agent at the source
    // that must reroute those messages to the agent at the new destination.
    // forwardingAddr stores its destination address.
    private InetAddress forwardingAddr = null;

    // Upon a return from the hop-invoked function, an agent must check
    // if it will terminate or has hopped to another place. The former case
    // requests this agent to send an "exit" message to its parent, whereas
    // the latter case does not send it.
    private transient boolean hopped = false;

    // Store destination information during gateway traversal
    private String destHostName;   // destination node
    private String[] destGateway;  // gateways to traverse on the way to dest.
    private int destGatewayPos;	   // current position in the gateway list
    private String destFuncName;   // function to call at destination node
    private String[] destFuncArgs; // function arguments

    // Maximum number of children this agent is allowed to spawn
    private int maxChildren = 0;

    // Number of children still alive: incremented in spawnChild, decremented
    // when a child notifies the parent that it is terminating.
    private int numAliveChildren = 0;

    // Combined with the agentId to produce system unique identifier
    // for children of UWAgents.
    private int nextChildId = 0;
    private Vector<Integer> childIds = new Vector<Integer>( );

    // Provides a representation of the last known host location
    // of the corresponding UWAgents.
    // e.g. "agent id 555" might map to host location "localhost"
    private Hashtable<Integer, InetAddress> agentIpDirectory 
	= new Hashtable<Integer, InetAddress>( );
    // gateways required to reach an agent
    private Hashtable<Integer, String[]> agentGatewayDirectory 
	= new Hashtable<Integer, String[]>( );	

    // Caches pairs of agent id and ip in the same domain for direct
    // communicatioin
    private Hashtable<Integer, InetAddress> agentIpCache
	= new Hashtable<Integer, InetAddress>( );
    private boolean cacheEnabled = true; // default

    // arguments used to hop to a new host
    private String nextFunc;    // a function to be called upon a hop
    private String[] funcArgs;  // arguments passed to the function
    private String nextHost;    // a host IP name to hop to

    // The names of all classes carried with this agent
    private List<String> agentClassNames = new ArrayList<String>( );

    // Initialization: constructor, init( ), and activateMailBox( ) ///////////
    /**
     * Default constructor.
     */
    public UWAgent( ) { }

    /**
     * Abstract method that can be used in subclasses of UWAgent
     */
    public abstract void init( );

    /**
     * Activate the UWAgentMailbox so that messages can be sent / received
     */
    public void activateMailbox( ) {
    	UWUtility.LogEnter( );
	try {
	    // Instantiate a mailbox
	    mailbox = new UWAgentMailbox( this );

	    // In general, uwplace and mailbox have the same port. 
	    // But just in case they have a different port.
	    if ( UWPlace.portnumber != UWAgentMailbox.portnumber ) {
		mailbox.setPortNumber( UWPlace.portnumber );
	    }

	    // Logging information: mailbox is identified with 
	    // ip, timestamp, and agent id.
	    String bindName = ip.toString( ) + "." + 
		String.valueOf( timeStamp ) + "." + String.valueOf( agentId );
	    UWUtility.LogInfo( "UWAgent.activateMailbox: bindName = " + 
			       bindName );
	}
	catch ( Exception e ) {
	    UWUtility.Log( e.toString( ) );
	    UWUtility.Log( "Cause: " + e.getCause( ) );
	}
    	UWUtility.LogExit( );
    }

    // Get and set utilities /////////////////////////////////////////////////
    /**
     * Returns the parent of a specified agentId.
     *
     * @param agentId the id of an agent whose parent is the caller's interest.
     * @return the parent id of agentId.
     */
    public int getParentId( int agentId ) {
    	UWUtility.LogEnter( "agentId = " + agentId );
	
    	int parent = -1;
	if ( agentId == 0 ) {
	    // I'm the root.
	    parent = -1;
	} else if ( agentId < maxChildren ) {
	    // I'm the root's child.
	    parent = 0;
	} else {
	    // I'm a desdendant of the root's child.
	    parent = agentId / maxChildren;
	}

    	UWUtility.LogInfo( "UWAgent.getParentId: parent = " + parent );
    	UWUtility.LogExit( );
	return parent;
    }

    /**
     * Uses the parent id of this UWAgent to register the InetAddress of
     * this UWAgent's parent.
     * 
     * @param inet the InetAddress of the parent agent
     */
    private void setParentAgentIp( InetAddress inet ) {
    	UWUtility.LogEnter( "inet = " + inet );
    	registerAgentIp( getParentId( agentId ), inet );
    	UWUtility.LogExit( );
    }

    /**
     * Returns the location of the parent of this UWAgent.
     *
     * @return the InetAddress of the parent agent
     */
    private InetAddress getParentAgentIp( ) {
	synchronized( agentIpDirectory ) {
	    UWUtility.LogEnter( "agentId = " + agentId );
	    UWUtility.LogInfo( "UWAgent.getParentAgentIp: ip = " + 
	       agentIpDirectory.get( new Integer( getParentId( agentId ) ) ) );
	    UWUtility.LogExit( );
	    return ( InetAddress )
	     ( agentIpDirectory.get( new Integer( getParentId( agentId ) ) ) );
	}
    }

    /**
     * Sets this agent class name
     * @param name an agent class name
     */
    public void setName( String name ) {
	agentName = name;
    }
    
    /**
     * Retrieve this agent class name
     * @return theis agent's derived class rather than UWAgent.
     */
    public String getName( ) {
	return agentName;
    }
    
    /**
     * Sets this agent client user name
     * @param name an agent client user name
     */
    public void setClientName( String name ) {
	clientUserName = name;
    }
    
    /**
     * Retrieve this agent's client user name
     * @return this agent's client user name
     */
    public String getClientName( ) {
	return clientUserName;
    }
    
    /**
     * Sets the maximum number of children each agent can spawn
     * @param max the maximum number of children to create
     */
    public void setMaxChildren( String max ) {
	maxChildren = Integer.parseInt(max);
    }
    
    /**
     * Retrieves the maximum number of children each agent can spawn
     * @return the maximum number of children each agent can spawn
     */
    public int getMaxChildren( ) {
	return maxChildren;
    }
    
    /**
     * Sets the timestamp when this agent was created.
     * @param time the time when this agent was created.
     */
    public void setTimeStamp( long time ) {
	timeStamp = time;
    }
    
    /**
     * Retrieves the timestamp when this agent was created.
     * @return the timestamp when this agent was created.
     */
    public long getTimeStamp( ) {
	return timeStamp;
    }
    
    /**
     * Sets the InetAddress where this agent was created.
     * @param inet the InetAddress where this agent was created.
     */
    public void setIp( InetAddress inet ) {
	ip = inet;
    }
    
    /**
     * Retrieves the InetAddress where this agent was created.
     * @return the InetAddress where this agent was created.
     */
    public InetAddress getIp( ) {
	return ip;
    }

    /**
     * Retrieves the ip name where this agent is running.
     * @return the current ip name in String 
     */
    public String getCurrentIpName( ) {
	String ipName = "localhost";
	try {
	    ipName = ( InetAddress.getLocalHost( ) ).getCanonicalHostName( );
	} catch ( Exception e ) {
	    ipName = "localhost";
	}
	return ipName;
    }


    
    /**
     * Sets the agent-domain-unique identifier.
     * @param id an agent identifier.
     */
    public void setAgentId( int id ) {
	agentId = id;
    }
    
    /**
     * Retrieves this agent's identifier.
     * @return this agent's identifier.
     */
    public int getAgentId ( ) {
	return agentId;
    }

    /**
     * Sets this agent's package path
     * @param id this agent's packape path
     */
    public void setPackagePath( String path ) {
	packagePath = path;
    }
    
    /**
     * Retrieves this agent's package path
     * @return this agent's package path
     */
    public String getPackagePath ( ) {
	return packagePath;
    }
    
    /**
     * Sets the current UWPlace where this agent can work.
     * @param p the current UWPlace where this agent can work.
     */
    public void setPlace( UWPlace p ) {
	place = p;
    }
    
    /**
     * Retrieves a reference to the current working UWPlace.
     * @return the current UWPlace where this agent is working.
     */
    public UWPlace getPlace( ) {
	return place;
    }
    
    /**
     * Returns whether the place this agent is running on supports SSL.
     * @return true if this agent supports SSL.
     */
    public boolean getIsSSL( ) {
	return place.getIsSSL( );
    }
    
    /**
     * Sets the next function to call upon a migration. It is only intented for
     * a call by UWInject in addition to UWAgent itself.
     *
     * @param func the next function to call.
     */
    public void setNextFunc( String func ) {
	nextFunc = func;
    }
    
    /**
     * Retrieves the next function to be called upon a migration.
     * @return the next function to be called upon a migration.
     */
    public String getNextFunc( ) {
	return nextFunc;
    }
    
    /**
     * Sets arguments passed to the next function to call upon a migration.
     * @param args arguments passed to the next function
     */
    public void setFuncArgs( String[] args ) {
	funcArgs = args;
    }
    
    /**
     * Retrieves arguments passed to the next function to call upon a 
     * migration. It is only intended for a call by UWPlace.
     * @return arguments passed to the next function.
     */
    public String[] getFuncArgs( ) {
	return funcArgs;
    }

    /**
     * Returns the UWAgentMailbox allocated to this agent. Internally used.
     * @return the UWAgentMailbox allocated to this agent.
     */
    private UWAgentMailbox getMailbox( ) {
	return mailbox;
    }

    /**
     * Enables agent IPs to be cached.
     */
    public void enableAgentIpCache( ) {
	cacheEnabled = true;
    }

    /**
     * Disables agent IPs to be cached.
     */
    public void disableAgentIpCache( ) {
	cacheEnabled = false;
    }

    /**
     * Checks if agent IPs are cached.
     * @return true if agent IPs are cached.
     */
    public boolean isAgentIpCacheEnabled( ) {
	return cacheEnabled;
    }

    /**
     * Resets this agent's hopped flag meaning that this agent has hopped.
     */
    public void resetHopped( ) {
	hopped = false;
    }

    /**
     * Checks if this agent has been hopped
     * @return true if this agent has been hopped.
     */
    public boolean isHopped( ) {
	return hopped;
    }

    /**
     * Checks how many messages remain unread in this agent. It is called 
     * by UWPlace.
     * @return the number of messages spooled in this agent
     */
    public int getNumMessages( ) {
	synchronized( messages ) {
	    return messages.size( );
	}
    }

    // Hop function group /////////////////////////////////////////////////////
    /**
     * Provides mobility for the UWAgent to move to another UWPlace.
     *
     * @param hostname a new destination host IP name
     * @param funcName a function to be called at the destination
     */
    public final void hop( String hostname, String funcName ) {
	hop( hostname, funcName, null );
    }

    /**
     * Provides mobility for the UWAgent to move to another UWPlace.
     *
     * @param hostName a new destination host IP name
     * @param funcName a function to be called at the destination
     * @param funcArgs arguments passed to the function
     */
    public final void hop( String hostName, String funcName, 
			   String[] funcArgs ) {

    	UWUtility.LogEnter( "hostName = " + hostName + 
			    ", funcName = " + funcName );

	// get -g option from current UWPlace
	String uwpGateway = place.getGateway( );
	if ( !( "".equals( uwpGateway ) )) { // gateway specified
	    // Try to contact hostName directly using a socket connection
	    boolean socketError = false;

	    try {
		UWPSocket uwpsocket = place.getUWPSocket( );
		OutputStream out = 
		    uwpsocket.initUWPSocket( UWUtility.MSG_TYPE_FUNC, hostName,
					     place.getPortNumber( ), 
					     "detectHost", 
					     0, // header param 1
					     0, // header param 2
					     place.getIsSSL( ) );
		// OutputStream out is unused since there is no data to send
		uwpsocket.returnOutputStream( out );
	    } catch ( SocketTimeoutException e ) {
		// Host name found, but isn't responding
		socketError = true;
	    } catch ( UnknownHostException e ) {
		// Host name not found
		socketError = true;
	    } catch ( Exception e ) {
		UWUtility.Log( e.toString( ) );
		e.printStackTrace( );
	    }
	    
	    if ( socketError ) {
		// No response, so try the gateway
		String[] gateway = new String[1];
		gateway[0] = uwpGateway;
		hop( hostName, gateway, funcName, funcArgs );
		UWUtility.LogExit( );
		return;
	    }
	}

	// Socket connection succeeded (or no gateway specified), so just go
	// directly to destination
	setNextFunc( funcName );
	setFuncArgs( funcArgs );
	
	try {
	    // destGateway contains the list of gateways that will allow the
	    // notification message to get back to the parent agent
	    InetAddress destAddr = InetAddress.getByName( hostName );
	    notifyRelativesOfMyIp( destAddr, destGateway );
	    // notify of my new destination to all the agents who cached my
	    // location, too.
	    if ( destGateway == null )
		notifyCachedColleaguesOfMyIp( destAddr ); // cache new location
	    else {
		notifyCachedColleaguesOfMyIp( null );     // flush the cache
		flushAgentIpDirectory( );
		flushAgentIpCache( );
	    }

	} catch ( java.net.UnknownHostException e ) { }

	// send myself to the destination
	synchronized( messages ) {
	    // the rest of hop( ) is a critical section:
	    // while I am serializing messages and carrying them with me, 
	    // UWPlace#SocketThread may deliver a new message to me.

	    hopped = place.sendAgent( this, hostName );
	    messages.clear( ); // clean up messages at this zombie
	
	    // This agent remain as a zomibe at the source to reroute messages
	    // from its children to the agent that has hopped to the new 
	    // destination.
	    //
	    // Set forwardingAddr to the destination so as to reroute future
	    // messages to the destination
	    InetAddress local = null;
	    try {
		local = InetAddress.getLocalHost( );
	    } catch ( java.net.UnknownHostException uhe ) {
		local = null;
	    }
	    if ( hopped == false ) {
		// hop failed
		UWUtility.LogInfo( "Hop failed" );
	    }
	    else if ( local != null &&  
		      ( local.getHostName( ).equals( hostName ) ||
			local.getCanonicalHostName( ).equals( hostName ) || 
			local.getCanonicalHostName( ).startsWith( hostName ) ) ) {
		// local hop
		UWUtility.LogInfo( "Hop destination is local host, " + 
				   "so no forwarding address is necessary." );
	    } else {
		// hopped to a remote site
		try {
		    this.forwardingAddr = InetAddress.getByName( hostName );
		} catch ( java.net.UnknownHostException uhe ) {
		    this.forwardingAddr = null;
		}
		UWUtility.LogInfo( "Setting agent " + this.agentId + 
				   "'s forwarding address to " + 
				   this.forwardingAddr );
	    }
	}
	UWUtility.LogExit( );
    }
    
    /**
     * Hop across each gateway.
     * Must be public so it is visible to AgentThread.run() through hop( ).
     */
    public final void hopGateway( ) {
	UWUtility.LogEnter( );
	try {
	    if ( destGateway == null || 
		 destGatewayPos >= destGateway.length ) {
		// this is the final destination
		hop( destHostName, destFuncName, destFuncArgs );
	    } else {
		// still en route to the final destination
		String nextHostName = destGateway[destGatewayPos++];
		// Hop to the next gateway, and recursively call hopGateway
		hop( nextHostName, "hopGateway", null );
	    }
	} catch ( Exception e ) {
	    UWUtility.Log( "UWAgent.hopGateway: " + e.toString( ) );
	    e.printStackTrace( );
	}
	UWUtility.LogExit( );
    }

    /**
     * Hops through gateway list to destination host as accepting no arguments.
     *
     * @param hostName a new destination host IP name
     * @param gateway  an array of gateways all the way to the hostName
     * @param funcName a function to be called at the destination
     */    
    public final void hop( String hostName, String[] gateway, String funcName 
			   ){
	hop( hostName, gateway, funcName, null );
    }

    /**
     * Hops through gateway list to destination host as accepting string[].
     *
     * @param hostName a new destination host IP name
     * @param gateway  an array of gateways all the way to the hostName
     * @param funcName a function to be called at the destination
     * @param funcArgs arguments passed to the function
     */    
    public final void hop( String hostName, String[] gateway, String funcName,
			   String[] funcArgs ) {

    	UWUtility.LogEnter( "gateway version; hostName = " + hostName + 
			   ", funcName = " + funcName );
	
	try {
	    // Initialize destination variables, so that the current agent 
	    // always knows its destination, and which gateways it needs to 
	    // use to get there. These member variables will travel with the 
	    // agent as it moves from host to host.

	    // final destination
	    destHostName = hostName;

	    // fulfill the destGateway[] with gateway[] argument.
	    if ( gateway == null ) {
		destGateway = null;
	    } else {
		// Store list of gateways with current agent
		destGateway = new String[gateway.length];
		for ( int i = 0; i < gateway.length; i++ ) {
		    destGateway[i] = gateway[i];
		    UWUtility.LogInfo( "gateway[" + i + "] = " + gateway[i] );
		}
		destGatewayPos = 0;
	    }
	    
	    // function name
	    destFuncName = funcName;
	    
	    // argument setting
	    if ( funcArgs == null ) {
		destFuncArgs = null;
	    } else {
		destFuncArgs = new String[funcArgs.length];
		for ( int i = 0; i <= funcArgs.length - 1; i++ ) {
		    destFuncArgs[i] = funcArgs[i];
		}
	    }
	    
	    // Start hopping through the gateway list
	    hopGateway( );
    	} catch ( Exception e ) {
	    UWUtility.Log( e.toString( ) );
	    UWUtility.Log( "Cause: " + e.getCause( ) );
    	}
	UWUtility.LogExit( );
    }

    /**
     * Notify this UWAgent's parent and children of its location (IP address).
     * It is called upon a hop.
     * @param addr    the calling agent's inet address
     */
    public void notifyRelativesOfMyIp( InetAddress addr ) {
	notifyRelativesOfMyIp( addr, null );
    }
    
    /**
     * Notify this UWAgent's parent and children of its location (IP address).
     * It is called upon a hop.
     * @param addr    the calling agent's inet address
     * @param gateway the callign agent's gateways list
     */
    public void notifyRelativesOfMyIp( InetAddress addr, String[] gateway ) {
    	UWUtility.LogEnter( "addr = " + addr );
	
    	// Add all my children and parent to a recepient list.
	Vector<Integer> allRecipients = new Vector<Integer>( );
	for ( int i = 0; i < childIds.size( ); i++ )
	    allRecipients.add(childIds.elementAt( i ) );
	int parentId = getParentId( agentId );
	if ( parentId != -1 )
	    allRecipients.add( new Integer(parentId) );
	
	// Since the corresponding UWAgent locations are always known, use
	// the most recently registered location.
	while ( !allRecipients.isEmpty( ) ) {
	    int recipId = ( ( Integer )allRecipients.remove( 0 ) ).intValue( );
	    InetAddress recipAddr = getAgentIp( recipId, agentIpDirectory );
	    
	    UWUtility.LogInfo( "agentId = " + agentId + ", addr = " + addr +
			       ", recipId = " + recipId + 
			       ", recipAddr = " + recipAddr );
	    
	    UWAgentMailbox mb = getMailbox( );
	    boolean success = mb.notifyAgentLocation( agentId, addr, recipId,
						      recipAddr, ip, 
						      timeStamp, gateway );
	}
    	UWUtility.LogExit( );
    }

    /**
     * Informs of this UWAgent's locaiton (IP address) to all the agents that
     * have cached it previous IP address.
     * @param addr    the calling agent's inet address
     */
    public void notifyCachedColleaguesOfMyIp( InetAddress addr ) {
    	UWUtility.LogEnter( "addr = " + addr );


	for ( Enumeration e = agentIpCache.keys( ); 
	      e.hasMoreElements( ); ) {
	    int recipId = ( ( Integer ) e.nextElement( ) ).intValue( );
	    InetAddress recipAddr = agentIpCache.get( new Integer( recipId ) );

	    UWUtility.LogInfo( "agentId = " + agentId + ", addr = " + addr +
			       ", recipId = " + recipId + 
			       ", recipAddr = " + recipAddr );

	    UWAgentMailbox mb = getMailbox( );
	    boolean success = mb.cacheAgentLocation( agentId, addr, 
						     recipId, recipAddr );
	}
    	UWUtility.LogExit( );
    }

    // Agent locating function group //////////////////////////////////////////
    /**
     * Return the IP address of the specified agent, using the local agent IP 
     * directory (and thereafter agent IP cache), or null if the agent ID is 
     * not found in the local directory or cache.
     *
     * @param recipId the identifier of an agent whose InetAddress is in query.
     * @return the InetAddress of a given agent identifier.
     */
    public InetAddress getAgentIp( int recipId ) {
	// first, try the local directory
	InetAddress recipIp = getAgentIp( recipId, agentIpDirectory );
	if ( recipIp == null )
	    // second, try the loca cache
	    recipIp = getAgentIp( recipId, agentIpCache );
	return recipIp;
    }
    
    /**
     * Return the IP address of the specified agent, using a givne directory,
     * null if the agent ID is not found there.
     *
     * @param recipId the identifier of an agent whose InetAddress is in query.
     * @param directory the directory to check for this recipId.
     * @return the InetAddress of a given agent identifier.
     */
    private InetAddress getAgentIp( int recipId, Hashtable directory ) {
    	UWUtility.LogEnter( "recipId = " + recipId );
	
	if ( directory == agentIpDirectory )
	    printAgentLocationIpDirectory( ); // agent 0 - 9's IPs
	else
	    printAgentLocationIpCache( );
	
    	InetAddress foundAddr = null; // the corresponding agent's InetAddress
	boolean foundIp = false;      // indicating if the ip has been found
	try {
	    // Look up the UWAgent location in the local directory
	    boolean containsKey = false;
	    synchronized( directory ) {
		containsKey 
		    = directory.containsKey( new Integer( recipId ) );
	    }
	    if ( containsKey ) {
		// found in the directory
		UWUtility.LogInfo( "found in local directory" );
		synchronized( directory ) {
		    foundAddr = ( InetAddress ) 
			directory.get( new Integer( recipId ) );
		}
		String hostAddress = foundAddr.getHostAddress( );
		    
		if ( "127.0.0.1".equals( hostAddress ) ) {
		    // it resides on the current local host.
		    String hostName = foundAddr.getCanonicalHostName( );
		    try {
			// get the local host's public IP rather than
			// 127.0.0.1
			foundAddr = InetAddress.getByName( hostName );
			UWUtility.LogInfo( "register:" + recipId + 
					   ", " + foundAddr );
			//re-register it
			if ( directory == agentIpDirectory )
			    registerAgentIp( recipId, foundAddr ); 
			else
			    cacheAgentIp( recipId, foundAddr );
		    } catch ( Exception e ) {
			UWUtility.Log( e.toString( ) );
			UWUtility.Log( "Cause: " + e.getCause( ) );
		    }
		}
		foundIp = true; // ip has been found.
	    }
	    
	    if ( !foundIp )     // ip has not been found
		foundAddr = null;
	    
	    UWUtility.LogInfo( "foundAddr = " + foundAddr );
    	} catch ( Exception e ) {
	    UWUtility.Log( e.toString( ) );
	    UWUtility.Log( "Cause: " + e.getCause( ) );
    	}
    	UWUtility.LogExit( );
	return foundAddr;      // return the corresponding agent's ip.
    }
    
    /**
     * Is used to pass a pair of an agent identifier and its inet address 
     * from getAgentIpWithForwarding( ) to talk( ) and enqueueMessage( ).
     */
    private class AgentInfo {
	public int agentId;               // an agent identifier
	public InetAddress agentAddress;  // its corresponding inet address

	/**
	 * Creates an empty object.
	 */
	AgentInfo( ) { }

	/**
	 * Initializes a new object with agent identifier and its ip address.
	 * @param id      an agent identifier
	 * @param address the corresponding InetAddress
	 */
	AgentInfo(int id, InetAddress address) {
	    this.agentId = id;
	    this.agentAddress = address;
	}
    }
    
    /**
     * Retrieves the IP address of the specified agent, using the local agent 
     * IP directory, or a forwarding algorithm if the specified agent ID is 
     * not found in the local directory.
     *
     * @param recipId the identifier of an agent whose InetAddress is in query.
     * @return an AgentInfo object that includes this agent id and inet 
     *         address.
     */
    private AgentInfo getAgentIpWithForwarding( int recipId ) {
    	UWUtility.LogEnter( "recipId = " + recipId );
	
	// verify recipId.
    	if ( recipId == -1 ) {
	    UWUtility.LogInfo( "Attempt to find recipient for nonexistent " +
			       "agent -1" );
	    AgentInfo ai = new AgentInfo( 0, null );
	    UWUtility.LogExit( );
	    return ai; // return an empty AgentInfo object.
    	}
	
	// agent ID where the message will be forwarded to
	int foundId = 0; 
	// (could be the ultimate destination, or just the next hop)
	InetAddress foundAddr = getAgentIp( recipId ); // get recipient address
	
	// If recipient is not found in local directory, calculate next hop
	if ( foundAddr == null ) {
	    UWUtility.LogInfo( "Agent " + recipId + " not found in agent " + 
			       this.agentId + "'s local directory." );
	    
	    int destId = recipId;	  // used to calculate destination
	    int currentId = this.agentId; // this agent id
	    
	    UWUtility.LogInfo( "Step 0: destId = " + destId + 
			       ", currentId = " + currentId );

	    // find the ascedant shared between currentId and recipId
	    while ( destId > currentId ) {
		destId /= maxChildren;
	    }
	    
	    UWUtility.LogInfo( "Step 1: destId = " + destId );
	    if ( destId < currentId ) {
		// Forward to parent
		UWUtility.LogInfo( "Destination ID less than current ID, " +
				   "so forwarding to parent" );
		foundId = this.getParentId( this.agentId );
	    } else {
		// destId == currentId
		// foward to a child
		UWUtility.LogInfo( "Destination ID == current ID," +
				   " so calculating child to forward to" );
		// Calculate which child to forward to
		destId = recipId;
		int childIdLow = currentId * maxChildren;
		int childIdHigh = ( ( currentId + 1 ) * maxChildren ) - 1;
		
		UWUtility.LogInfo( "Step 2: Low = " + childIdLow + 
				   ", High = " + childIdHigh );
		while ( destId < childIdLow || destId > childIdHigh ) {
		    destId /= maxChildren;
		}
		UWUtility.LogInfo( "Step 2: new destId = " + destId );
		foundId = destId; // a specific chilid to forward to
	    }

	    if ( foundId == recipId ) {
		UWUtility.LogInfo( "foundId = " + foundId + " = recipId!!!" );
		printAgentLocationIpDirectory( );
		// my parent or child is the final receipient, but I couldn't
		// find its corresponding IP. Maybe, I haven't created the
		// corresponding child yet. Let's wait for this agent to
		// create the child or for max 10sec.
		final long maxWait = 10000; // 10 sec.

		Date startTime = new Date( );
		for( int i = getMaxChildren( ); i > 0; i-- ) {
		    // the same number of repetitions as the max children
		    synchronized( childIds ) {
			try {
			    // wait for a child creation
			    childIds.wait( maxWait ); 
			} catch ( InterruptedException e ) {
			    break; // something wrong.
			}
		    }
		    Date curTime = new Date( );
		    if ( curTime.getTime( ) - startTime.getTime( ) >= maxWait )
			break; // time expired.
		}
		printAgentLocationIpDirectory( );

		// now try my agentIpDirectory again.
		if ( getAgentIp( recipId ) == null ) {
		    // This is a problem.
		    UWUtility.LogInfo( "Forwarding algorithm indicated agent "
				       + foundId + 
				       " but this was the original recipient" 
				       );
		    // So contact with my parent anyway. 
		    foundId = this.getParentId( this.agentId );
		    UWUtility.LogInfo( "Trying parent agent: " + foundId );
		}
	    }

	    // now retrieve the inet address corresponding to the next hop
	    foundAddr = getAgentIp( foundId );
	    if ( foundId > -1 ) {
		UWUtility.LogInfo( "Forwarding to agent " + foundId + 
				   " at " + foundAddr );
	    } else {
		UWUtility.Log( "Error: Already at the root, " + 
			       "so cannot forward to parent. Recipient = " +
			       recipId );
	    }
	} else {
	    // recipId was found in the local directory
	    // or in the cache
	    foundId = recipId;
	}
	
	// instantiate and return an AgentInfo object that includes the next 
	// hop's id and ip pair.
	AgentInfo ai = new AgentInfo( foundId, foundAddr );
    	UWUtility.LogExit( );
	return ai;
    }
    
    // message sending functions //////////////////////////////////////////////
    /**
     * Provides communication for the UWAgent to send a UWMessage to
     * another UWAgent though its "personal" AgentMailbox.
     *
     * @param recipId the ID of the intended recipient
     * @param message the message to be sent
     * @return true if the message has been sent or forwarded successfully.
     */
    public final boolean talk( int recipId, UWMessage message ) {
    	UWUtility.LogEnter( );
	
	String[] mess = message.getMessageHeader( );

	// locate the destination id, ip, and gateways en route ot the dest.
	UWUtility.LogInfo( "Looking for agent " + recipId );
	AgentInfo ai = getAgentIpWithForwarding( recipId );
	int foundId = ai.agentId;
	InetAddress foundAddr = ai.agentAddress;
	String[] foundGateways = getAgentGateways( foundId );

	// for logging destination information.
	if ( mess != null ) {
	    UWUtility.LogInfo( "talk to " + foundId + " at " + 
			       foundAddr + ", " + mess[0] );
	} else {
	    UWUtility.LogInfo( "talk to " + foundId + " at " + foundAddr );
	}

	boolean success = false;	
	if ( foundAddr != null ) {
	    // construct message source/destination information.
	    message.setReceivingAgentId( recipId );
	    message.setSendingAgentId( this.agentId );
	    try {
		message.setReceivingIp( foundAddr );
		message.setSendingIp( InetAddress.getLocalHost( ) );
	    } catch ( java.net.UnknownHostException e ) {
		e.printStackTrace( );
	    }
	    // include the gateways information in the message
	    message.setReceivingGateways( foundGateways );
	    
	    // Send the message to the found address.
	    try {
		success = getMailbox( ).sendMessage( message, foundAddr, foundId, 
					   timeStamp );
                //System.err.println( "UWAgent.talk: return from mailbox.sendMessage: foundAddr = " + foundAddr + " foundId = " + foundId );
		//success = true;
	    }
	    // Do nothing important. If the exception was thrown, then 
	    // execution should continue with other methods to find the 
	    // recipient UWAgent at the user level.
	    catch ( Exception e ) {
		success = false;
	    }
	}
	
    	UWUtility.LogExit( );
	return success;
    }

    /**
     * Retrieves an array of gateways on the way to a given agent id.
     *
     * @param recipId a destination agent id.
     * @return an array of gateways on the way to recipId.
     */
    private String[] getAgentGateways( int recipId ) {

    	UWUtility.LogEnter( "recipId = " + recipId );
	
	// stores an array of gateways on the way to recipId
	String[] foundGateways = null; 
	try {
	    if ( agentGatewayDirectory.containsKey( new Integer( recipId ) ) ){
		UWUtility.LogInfo( "found gateway list in local directory" );
		foundGateways = ( String[] )
		    agentGatewayDirectory.get( new Integer( recipId ) );
	    } else {
		foundGateways = null;
	    }
    	} catch ( Exception e ) {
	    UWUtility.Log( e.toString( ) );
	    UWUtility.Log(" Cause: " + e.getCause( ) );
    	}
	
    	UWUtility.LogExit( );
    	return foundGateways;
    }

    /**
     * Registers a pair of agent id and ip to the calling agent's directory
     *
     * @param agentId the destination agent identifier
     * @param ipName the destination host's ip name
     * @return true if completed without any exceptions
     */
    public boolean registerAgentIp( int agentId, String ipName ) {
	
	try {
	    InetAddress location = InetAddress.getByName( ipName );
	    return registerAgentIp( agentId, location, null, 0 );	    
	} catch ( UnknownHostException e ) {
	    UWUtility.Log( e.toString( ) );
	    return false;
	}
    }
    
    /**
     * Registers a pair of agent id and ip to the calling agent's directory
     *
     * @param agentId the destination agent identifier
     * @param location the destination host's inet address
     * @return true anytime.
     */
    public boolean registerAgentIp( int agentId, InetAddress location ) {
	return registerAgentIp( agentId, location, null, 0 );
    }

    /**
     * Registers a pair of agent id and id as well as a pair of agent id 
     * and gateways to this agent to the calling agent's directory.
     *
     * @param agentId the destination agent identifier
     * @param location the destination host's inet address
     * @param gateways gateways en route to the destination host
     * @param gwPos the current position of the gateways[] array
     * @return true anytime.
     */
    public boolean registerAgentIp( int agentId, InetAddress location, 
				    String[] gateways, int gwPos ) {
    	UWUtility.LogEnter("agentId = " + agentId + 
			   ", location = " + location +
			   ", myId = " + getAgentId( ) );

	// print out gateways if there are any on the way to agentId
	if ( gateways != null && gateways.length > 0 ) {
	    UWUtility.LogInfo( "gateways.length = " + gateways.length + 
			       ", gatways[0] = " + gateways[0] );
	}
	UWUtility.LogInfo( "Directory before" );		
    	printAgentLocationIpDirectory( ); // agent 0 - 9's IPs

	// remove the old (agentId, Ip) pair.
	Integer aId = new Integer( agentId );
	synchronized( agentIpDirectory ) {
	    if ( agentIpDirectory.containsKey( aId ) ) {
		agentIpDirectory.remove(aId);
	    }
	    // insert a new (agentId, Ip) pair
	    agentIpDirectory.put( aId, location );
	}

	UWUtility.LogInfo( "Directory after" );		
    	printAgentLocationIpDirectory( ); // agent 0 - 9's IPs
	
	if ( gateways != null ) {
	    // if there are gateways, remove the old (agentId, gateways ) pair
	    UWUtility.LogInfo( "gateways[0] = " + gateways[0] );
	    if ( agentGatewayDirectory.containsKey( aId ) ) {
		agentGatewayDirectory.remove( aId );
	    }
	    // insert a new (agentId, gateways ) pair.
	    agentGatewayDirectory.put( aId, gateways );
	}
	
	UWUtility.LogExit( );
	return true;
    }

    /**
     * Caches a pair of agent id and ip into the calling agent's directory.
     *
     * @param agentId the destination agent identifier
     * @param ipName the destination host's ip name
     * @return true if the caching operation has been successfully completed.
     */
    public boolean cacheAgentIp( int agentId, String ipName ) {
	UWUtility.LogEnter( );
	boolean retVal = false;

	// create an inet address from a given ip name.
	InetAddress inet = null;
	try {
	    inet = InetAddress.getByName( ipName );
	} catch ( UnknownHostException e ) {
	    // a wrong ip name
	    UWUtility.Log( e.toString( ) );
	    inet = null;
	}

	if ( inet != null )
	    // cache it if it is a correct ip name
	    retVal = cacheAgentIp( agentId, inet );
	UWUtility.LogExit( );
	return retVal;
    }

    /**
     * Caches a pair of agent id and ip into the calling agent's directory.
     *
     * @param agentId the destination agent identifier
     * @param location the destination host's inet address
     * @return true anytime.
     */
    public boolean cacheAgentIp( int agentId, InetAddress location ) {
    	UWUtility.LogEnter("agentId = " + agentId + 
			   ", location = " + location +
			   ", myId = " + getAgentId( ) );

	UWUtility.LogInfo( "Directory before" ); printAgentLocationIpCache();

	// remove the old (agentId, Ip) pair.
	Integer aId = new Integer( agentId );
	synchronized( agentIpCache ) {
	    if ( agentIpCache.containsKey( aId ) ) {
		agentIpCache.remove(aId);
	    }
	    // insert a new (agentId, Ip) pair only if Ip is not null.
	    if ( location != null )
		agentIpCache.put( aId, location );
	}

	UWUtility.LogInfo( "Directory after" ); printAgentLocationIpCache( );
	
	UWUtility.LogExit( );
	return true;
    }

    /**
     * Flushes all agentIpDirectory entries except those regarding my
     * parent and children.
     */
    public void flushAgentIpDirectory( ) {
	UWUtility.LogEnter( );
	synchronized( agentIpDirectory ) {

	    UWUtility.LogInfo( "UWAgent.flushAgentIpDirectory: before" );
	    printAgentLocationIpDirectory( );

	    for ( Enumeration e = agentIpDirectory.keys( ); 
		  e.hasMoreElements( ); ) {
		int id = ( ( Integer ) e.nextElement( ) ).intValue( );

		if ( id == getParentId( getAgentId( ) ) ) {
		    // if this is my parent, leave it in the directory
		    continue;
		}

		// calculate the id range of my children 
		int leftMostChild =  ( getAgentId( ) == 0 ) ? 
		    1 :  getAgentId( ) * getMaxChildren( );
		int rightMostChild = ( getAgentId( ) == 0 ) ?
		    getMaxChildren( ) - 1 : 
		    ( getAgentId( ) + 1 ) * getMaxChildren( ) - 1;
		    
		if ( leftMostChild <= id && id <= rightMostChild ) {
		    // if this is a child of mine, leave it in the directory
		    continue;
		}
		     
		// remove this id/ip pair
		agentIpDirectory.remove( new Integer( id ) );
	    }

	    UWUtility.LogInfo( "UWAgent.flushAgentIpDirectory: after" );
	    printAgentLocationIpDirectory( );
	}
	UWUtility.LogExit( );
    }

    /**
     * Flushes all agentIpCache entries.
     */
    public void flushAgentIpCache( ) {
	UWUtility.LogEnter( );
	synchronized( agentIpCache ) {
	    agentIpCache.clear( );
	}
	UWUtility.LogExit( );
    }

    /**
     * Check if a given agent's inet address has been cached.
     * @param agentId the identifier of an agent whose address will be checked
     *                to exist in cache.
     * @return true if the given agent's inet address has been cached.
     */
    public boolean isAgentIpCached( int agentId ) {
	synchronized( agentIpCache ) {
	    UWUtility.LogInfo( "isAgentIpCached = " +
			       ( agentIpCache.get( new Integer( agentId ) ) 
				 != null ) );
	    return ( agentIpCache.get( new Integer( agentId ) ) != null ) ?
		true : false;
	}
    }

    /**
     * Check if a given agent's inet address has been cached and is equal to
     * a given inet address.
     * @param agentId the identifier of an agent whose address will be checked
     *                to exist in cache and to equal a given inet address.
     * @param agentIp the inet address to check.
     * @return true if the given agent's inet address has been cached and
     *         equals the given inet address
     */
    private boolean testAgentIpCached( int agentId, InetAddress agentIp ) {
	synchronized( agentIpCache ) {
	    UWUtility.LogInfo( "agentId = " + agentId +
			       "agentIp = " + agentIp +
			       "agetIpCache.get( " + agentId + " ) = " +
			       agentIpCache.get( new Integer( agentId ) ) );
	    return ( agentIpCache.get( new Integer( agentId ) ) == agentIp ) ?
		true : false;
	}
    }

    // message receiving functions ////////////////////////////////////////////
    /**
     * Enqueue an incoming message into an agent's message-spooling queue.
     * @param message a message to enqueue.
     * @return true if the given message has been successfully delivered.
     */
    public boolean enqueueMessage( UWMessage message ) {
    	UWUtility.LogEnter( );

	boolean success = true;
	
	int recipId = message.getReceivingAgentId( ); // the receiver agent id
	String[] header = message.getMessageHeader( ); // the message header

	UWUtility.LogInfo( "UWAgent.enqueueMessage: Recipient = " + recipId + 
			   ", sender = " + message.getSendingAgentId() + 
			   ", my ID = " + this.agentId + 
			   "header[0] = " + header[0] );

	String[] head = message.getMessageHeader( );

    	if ( recipId != this.agentId ) {
	    // Message is not for me, so forward it on
	    // record this routing information first
	    try {
		message.insertRouteInfo( getAgentId( ), 
					 InetAddress.getLocalHost( ) );
	    } catch ( UnknownHostException e ) {
		UWUtility.Log( e.toString( ) );
		UWUtility.Log( "Cause: " + e.getCause( ) );
	    }

	    // now find the next hop
	    AgentInfo ai = getAgentIpWithForwarding( recipId );
	    int foundId = ai.agentId;                 // the next hop's id

	    InetAddress foundAddr = ai.agentAddress;  // the next hop's ip
	    success = getMailbox( ).sendMessage( message, foundAddr, foundId, 
						 timeStamp );   // forward it!

	} else {
	    synchronized( this.messages ) {

		// a critical section:
		// while UWPlace.SocketThread is delivering a message to this
		// agent, the agent itself may be departing for another place
		// as serializing its messages vector.
		if ( this.forwardingAddr != null ) {
		    // Message is for me, but I'm gone to a different site
		    UWUtility.LogInfo( "Recipient agent " + recipId + 
				       " has a forwarding address " + 
				       this.forwardingAddr + ", so forwarding."
				       );
		    // forward it!
		    success = getMailbox( ).sendMessage( message, 
							 forwardingAddr, 
							 recipId, 
							 timeStamp );
		}
		// Message is for me, and I'm here to accept it.
		else if ( message.getIsSystemMessage( ) ) {
		    // Message is a system-level message.
		    handleSystemMessage( message );
		} else {
		    // Message is a user-level message.
		    
		    // For debugging
		    // message.showRouteInfo( );
		    
		    // Find the farthest route (including the message source) 
		    // that can communicated directly with me, and cache its id
		    // and ip.
		    UWAgentMailbox mb = getMailbox( );
		    int numRoutes = message.getNumRoutes( );
		    for ( int i = -1 ; 
			  cacheEnabled == true && numRoutes > 0 && 
			      i < numRoutes;
			  i++ ) {
			// numRoutes == 0 means the message came directly from
			// the source and thus no need to cache it.
			
			// start from the message source (i = -1).
			int routeId = ( i == -1 ) ? 
			    message.getSendingAgentId( ) : 
			    message.getRouteId( i );
			InetAddress routeIp = ( i == -1 ) ? 
			    message.getSendingIp( ) : message.getRouteIp( i );
			
			// check if it has been already cached.
			if ( testAgentIpCached( routeId, routeIp ) == true )
			    // yes
			    break;
			
			// the current routeId/routeIp pair hasn't been cached.
			try {
			    InetAddress senderIp = InetAddress.getLocalHost( );
			    if ( mb.cacheAgentLocation( getAgentId( ), 
							senderIp,
							routeId, 
							routeIp ) == true ) {
				// contacted with the farthest route 
				// (including the message source). Cache it in 
				// my agentIpCache.
				if ( cacheAgentIp( routeId, routeIp ) == true )
				    break;
				UWUtility.LogInfo( "routeId = " + routeId +
						   "routeIp = " + routeIp +
						   " failed" );
			    }
			} catch ( UnknownHostException e ) {
			    UWUtility.Log( e.toString( ) );
			    break;
			}
		    }

		    // finally put this message in my message queue.
		    messages.addElement( message ); 

		    messages.notifyAll( ); // wake up myself if I'm blocked.
		}
	    }
	}
	UWUtility.LogExit( );
	return success;
    }

    /**
     * Handle messages that are sent within the UWAgent execution engine
     * (not user messages)
     * @param message a system-level message
    */
    private void handleSystemMessage( UWMessage message ) {
	UWUtility.LogEnter( );
	int senderAgentId = message.getSendingAgentId( ); // sender agent id
	String[] header = message.getMessageHeader( );    // the message header
	UWUtility.LogInfo( "Agent " + this.agentId + 
			   " received the system message " +
			  header[0] + " from agent " + senderAgentId + 
			   " at address " + message.getSendingIp( ) );
	
	// Handle each message type here
	if ( header[0].equals( "childStarting" ) ) {
	    // one of my children has started.
	    numAliveChildren++;

	    // register this child' id and ip in my local directory.
	    registerAgentIp( senderAgentId, message.getSendingIp( ), null, 0 );
	} else if ( header[0].equals( "childExiting" ) ) {
	    // one of my children has terminated
	    numAliveChildren--;

	    // Remove this child id from the childIds vector
	    Integer aId = new Integer( senderAgentId );
	    childIds.remove( new Integer( senderAgentId ) );

	    UWUtility.LogInfo( "Directory before" );
	    printAgentLocationIpDirectory( ); // agent 0 - 9's IPs

	    // Update the local directory to reflect the fact that the sending
	    // agent is exiting. If we have already received a newer address 
	    // for the sender, do not remove it.
	    synchronized( agentIpDirectory ) {
		InetAddress foundAddr 
		    = ( InetAddress )agentIpDirectory.get( aId );
		if ( foundAddr != null && 
		     foundAddr.equals( message.getSendingIp( ) ) ) {
		    agentIpDirectory.remove( aId );
		}
	    }

	    UWUtility.LogInfo( "Directory after" );		
	    printAgentLocationIpDirectory( ); // agent 0 - 9's IPs
	}
	UWUtility.LogExit( "numAliveChildren == " + numAliveChildren );
    }
    
    /**
     * Removes the UWMessage from the UWAgentMailbox.  Deletes and logs
     * messages sent from untrusted sources.
     * If message is empty, then this thread waits for a new message.
     * And then, the Thread is resumed when new message arrives.
     * Otherwise, the Thread is resumed using wakeupMessageWaitingThread() 
     * method.
     *
     * @return the front UWMessage in the UWAgentMailbox.
     */
    public UWMessage retrieveNextMessage( ) {
    	UWUtility.LogEnter( );
	
	// If not, return UWMessage and remove it from the vector list
	synchronized ( messages ) {
	    if ( messages.isEmpty( ) ) {
		if ( noMoreMessageWaiting == false ) {

		    // if the message vector is empty and an agent is still
		    // allowed to wait for future messages
		    try {

			UWUtility.LogInfo( "messages.wait" );
			messages.wait( ); // wait for a message

			
		    } catch ( Exception e ) {
			UWUtility.Log( e.toString( ) );
		    }
		} else {

		    // if the message vector is empty and an agent is no
		    // longer allowed to wait for future messages
		    UWUtility.LogInfo( "noMoreMessageWaiting(1) = " + 
				       noMoreMessageWaiting + 
				       ", messages.size = " + 
				       messages.size( ) );
		    noMoreMessageWaiting = false; // confirm prohibit
		    UWUtility.LogExit( );
		    return null;
		}
	    }

	    // at this point, a message may be available or the agent was
	    // woken up upon noMoreMessageWaiting == true.
	    if ( messages.isEmpty( ) ) {
		// noMoreMessageWaiting may be true
		UWUtility.LogInfo( "noMoreMessageWaiting(2) = " + 
				   noMoreMessageWaiting + 
				   ", messages.size = " + messages.size( ) );
		noMoreMessageWaiting = false; // confirm prohibit.
		UWUtility.LogExit( );
		return null;
	    }
	    UWUtility.LogExit( );

	    // return the front message.
	    return ( ( UWMessage )messages.remove( 0 ) );
	}
    }

    /**
     * This method resumes the Thread that is stopped by retrieveNextMessage( )
     * method.
     *
     * Usage: If an agent has internally started a child thread in charge of 
     * message retrieval and thereafter wants to terminate itself, this agent's
     * main thread must call wakeupMessageWaitingThread to wake up a child,
     * so that all threads are to exit.
     */
    public void wakeupMessageWaitingThread( ) {
	synchronized ( messages ) {
	    // wake up threads waiting for incoming messages
	    messages.notifyAll( );
	    // prohibit agents to wait for any more messages
	    noMoreMessageWaiting = true; 
	}
    }

    // spawnChild function group /////////////////////////////////////////////
    /**
     * spawnChild #0:
     * Instantiates a child from a given agetName class at the local host
     * without giving any arguments. It internally calls spawnChild #3.
     *
     * @param agentName a class file from which a child is instantiated.
     * @return a child agent
     */
    public UWAgent spawnChild( String agentName ) {
    	UWUtility.LogEnter( "agentName = " + agentName );
    	UWUtility.LogExit( );
	return spawnChild( agentName, null, "localhost" );
    }

    /**
     * spawnChild #1:
     * Instantiates a child from a given agetName class at the local host
     * as passing agentArgs to it. It internally calls spawnChild #3.
     *
     * @param agentName a class file from which a child is instantiated.
     * @param agentArgs a String array of arguments passed to a child
     * @return a child agent
     */
    public UWAgent spawnChild( String agentName, String[] agentArgs ) {
    	UWUtility.LogEnter( "agentName = " + agentName );
    	UWUtility.LogExit( );
	return spawnChild( agentName, agentArgs, "localhost" );
    }

    /**
     * spawnChild #2:
     * Instantiates a child from a given agetName class at a given host.
     * without giving any arguments. It internally calls spawnChild #3.
     *
     * @param agentName a class file from which a child is instantiated.
     * @return a child agent
     */
    public UWAgent spawnChild( String agentName, String host ) {
    	UWUtility.LogEnter( "agentName = " + agentName );
    	UWUtility.LogExit( );
	return spawnChild( agentName, null, host );
    }

    /**
     * spawnChild #3:
     * Instantiates a child from a given agetName class at a given host
     * as passing agentArgs to it. It internally calls spawnChildInternal
     * to complete a child creation.
     *
     * @param agentName a class file from which a child is instantiated.
     * @param agentArgs a String array of arguments passed to a child.
     * @param host a host name where a child is spawned.
     * @return a child agent
     */
    public UWAgent spawnChild( String agentName, String[] agentArgs,
			       String host ) {
	
	// call spawnChildInternal to complete a child creation.
	return spawnChildInternal( agentName, agentArgs, host, -1 );
    }

    /**
     * spawnChild #4:
     * Instantiates a child from a given agetName class at a given host
     * as passing agentArgs to it. 
     * For the purpose of resuming a crashed agent with this method, 
     * this version of spawnChild( ) allows a user to specify a child
     * agent id. It must be between the parent id * getMaxChildren( ) and
     * (the parent id + 1) * getMaxChildren( ) - 1. It internally calls 
     * spawnChildInternal to complete a child creation.
     * It internally calls spawnChildInternal to complete a child creation. 
     *
     * @param agentName a class file from which a child is instantiated.
     * @param agentArgs a String array of arguments passed to a child.
     * @param host a host name where a child is spawned.
     * @param childId an agent id to be allocated to this child
     * @return a child agent
     */
    public UWAgent spawnChild( String agentName, String[] agentArgs,
			       String host, int childId ) {
	
	// call spawnChildInternal to complete a child creation.
	return spawnChildInternal( agentName, agentArgs, host, childId );
    }
    
    /**
     * spawnChild #5:
     * Instantiates a child from a given agetName class at a given host
     * as passing agentArgs to it and appending new class names to it.
     * It internally calls spawnChild #6.
     *
     * @param agentName a class file from which a child is instantiated.
     * @param agentArgs a String array of arguments passed to a child.
     * @param host a host name where a child is spawned.
     * @param classNames additional class names 
     * @return a child agent
     */
    public UWAgent spawnChild( String agentName, String[] agentArgs,
			       String host, String[] classNames ) {
	
	return spawnChild( agentName, agentArgs, host, classNames, -1 );
    }

    /**
     * spawnChild #6:
     * Instantiates a child from a given agetName class at a given host
     * as passing agentArgs to it and appending new class names to it.
     * For the purpose of resuming a crashed agent with this method, 
     * this version of spawnChild( ) allows a user to specify a child
     * agent id. It must be between the parent id * getMaxChildren( ) and
     * (the parent id + 1) * getMaxChildren( ) - 1. It internally calls 
     * spawnChildInternal to complete a child creation.
     *
     * @param agentName a class file from which a child is instantiated.
     * @param agentArgs a String array of arguments passed to a child.
     * @param host a host name where a child is spawned.
     * @param classNames additional class names 
     * @param specifiedChildId an agent id to be allocated to this child
     * @return a child agent
     */
    public UWAgent spawnChild( String agentName, String[] agentArgs,
			       String host, String[] classNames, 
			       int specifiedChildId ) {
	
    	UWUtility.LogEnter( "agentName = " + agentName );

	// register additional class names to this agent's class hash table
	if ( classNames != null ) {
	    for ( int i = 0; i < classNames.length; i++ ) {
		if ( !agentClassNames.contains( classNames[i] ) ) {
		    agentClassNames.add( classNames[i] );
		}
	    }
	}

	// now call spawnChildInternal to complete a child creation.
	UWAgent newChild = spawnChildInternal( agentName, agentArgs, host, 
					       specifiedChildId );

    	UWUtility.LogExit( );
	return newChild;
    }
    
    /**
     * Spawns a new child agent of any class, inheriting appropriate 
     * identifier information.
     *
     * @param agentName name of spawned agent
     * @param agentArgs arguments of spawned agent. agentArgs can be a
     * 	                String[] or null.
     * @param host      Itinerary. Spawned agent is injected to this host. It
     *                  must reside in the same IP domain as the parent.
     * @param specifiedChildId: set a specific child id (-1 means system given)
     * @return: the spawned UWAgent
     */
    private UWAgent spawnChildInternal( String agentName, String[] agentArgs,
					String host, int specifiedChildId ) {
	
    	UWUtility.LogEnter("agentName = " + agentName);
	
	UWUtility.LogInfo( "[debug: UWAgent(spawnChildInternal)] agentName= " +
			   agentName );
	
	// Create a new agent from agentName and agentArgs
	if ( !agentClassNames.contains( agentName ) ) {
	    agentClassNames.add( agentName );
	}
	Object agentObj = makeObjectFromClass( agentName, agentArgs );
	if ( agentObj == null ) {
	    UWUtility.Log( "Cannot create an object for agent " + agentName );
	    UWUtility.LogExit( );
	    return null;
	}
	
	// Root agent is limited to one fewer children than other agents
	int maxChildrenAdj = maxChildren;
	int parent = getParentId( agentId );
	if ( parent == -1 )
	    maxChildrenAdj--; // this is the root agent
	
	// Verify that we haven't exceeded child limit
	if ( childIds.size() >= maxChildrenAdj ) {
	    UWUtility.Log( "Cannot spawn more than " + maxChildrenAdj + 
			   " children." );
	    UWUtility.LogExit( );
	    return null;
	}
	
	// Initialize this child agent's state.
	( ( UWAgent )agentObj ).setNextFunc( "init" );
	( ( UWAgent )agentObj ).setClientName( clientUserName );
	( ( UWAgent )agentObj ).setMaxChildren(Integer.toString(maxChildren));
	( ( UWAgent )agentObj ).setName( agentName );
	( ( UWAgent )agentObj ).setIp( getIp( ) );
	( ( UWAgent )agentObj ).setTimeStamp( getTimeStamp( ) );
	( ( UWAgent )agentObj ).setPackagePath( getPackagePath( ) );
	
	// Set or calculate child ID	
	int childId = -1;	
	if ( specifiedChildId == -1 ) {
	    // automatic id assignment
	    do {
		// calculate a new id. 
		// nextChildId increases monotonically and must be rounded
		// by maxChildrenAdj.
		childId = agentId * maxChildrenAdj 
		    + ( nextChildId++ % maxChildrenAdj );
		if ( agentId == 0 ) {
		    // root's child starts with 1 rather than 0.
		    childId++;
		}
		if ( !childIds.contains( new Integer( childId ) ) )
		    // check if the same id has been already assigned.
		    break;
	    } while ( true );
	} else {
	    // user-specified id assignment
	    // Check child agent id range		
	    if (  ( specifiedChildId == 0 ) ||
		   ( specifiedChildId < ( agentId * maxChildrenAdj ) ) ||
		   ( specifiedChildId >= ( agentId * maxChildrenAdj + 
					   maxChildrenAdj + 
					   ( ( agentId == 0 ) ? 1 : 0 ) ) 
		     ) 
		  ) {
		// out of range
		UWUtility.LogExit( );
		return null;
	    }
	    // Check if this child agent already exists
	    else if ( childIds.contains( new Integer( specifiedChildId ) ) ) {
		// the same child exists
		UWUtility.LogExit( );
		return null;
	    }
	    childId = specifiedChildId;
	}
	( ( UWAgent ) agentObj ).setAgentId( childId );
	synchronized( childIds ) {
	    childIds.add( new Integer( childId ) );
	    // wake up a UWPlace.SocketThread( ) from 
	    // getAgentIpWithForawrding( )
	    childIds.notifyAll( );
	}
	
	// register agent id and ip pair.
	try {
	    // register this child id and ip pair.
	    // note: a destination must be located in the same ip domain.
	    if (host.equals( "localhost" ) ) {
		registerAgentIp( childId, InetAddress.getLocalHost( ) );
	    } else {
		registerAgentIp( childId, InetAddress.getByName( host ) );
	    }
	    // register the parent id and ip pair
	    ( ( UWAgent )agentObj ).setParentAgentIp( 
					     InetAddress.getLocalHost( ) );
	} catch ( java.net.UnknownHostException e ) {
	    UWUtility.Log( e.toString( ) );
	    UWUtility.Log( "Cause: " + e.getCause( ) );
	    return null;
	}

	// inject the child to a given host
	boolean success = injectAgent( ( UWAgent )agentObj, host );

	UWUtility.LogExit( );
	// return a reference to the child only if successfully dispatched.
	return ( success ) ? ( UWAgent )agentObj : null;
    }

    /**
     * This method makes an object of UWAgent or user program.
     * An object of className is created from byte representation of class.
     *
     * @param className created class name
     * @param classArgs arguments of class named as className
     * @return object of className
     */
    private Object makeObjectFromClass(String className, String[] classArgs) {
    	UWUtility.LogEnter("className = " + className);
	
    	Object classObj = new Object( );
	Class classClass = null;
	try {
	    // locate a class hash table associated with my ip, timepstamp,
	    // and agent id, and thereafter find a class from the table 
	    // corresponding to className.
	    classClass = getPlace( ).makeClass( getIp( ), getTimeStamp( ),
						getAgentId( ), className );

	    if ( classClass != null )
		UWUtility.LogInfo( "[debug: UWAgent(makeObjectFromClass)] " +
				   " classClass is " + classClass );

	    if ( classClass == null ) {
		// Not found from my hash table. Then, try to find the class 
		// from the local directory.
		classClass = Class.forName( className );
		
		UWUtility.LogInfo( "[debug: UWAgent(makeObjectFromClass)] " +
				   " classClass is " + classClass );

		if ( classClass == null ) {
		    UWUtility.Log( className + " not found" );
		    UWUtility.LogExit( );
		    return null;
		}
	    }
	} catch ( Exception e1 ) {
	    UWUtility.Log( "UWAgent.makeObjectFromClass(1): " + e1 );
	    e1.printStackTrace( );
	    UWUtility.LogExit( );
	    return null;
	}
	
	// now found the class.
	try {
	    if ( classArgs == null || classArgs.length == 0 ) {
		// no constructor arguments. just instantiate it.
		classObj = classClass.newInstance( );
	    } else {
		// some constructor arguments.
		Object[] constructorArgs = new Object[] { classArgs };
		// Locate this class's constructor and instantiate the class
		// The constructor should accept a String array.
		Constructor classConst =
		    classClass.getConstructor( new Class[]{ String[].class } );
		classObj = classConst.newInstance( constructorArgs );
	    }
	} catch ( Exception e2 ) {
	    UWUtility.Log( "UWAgent.makeObjectFromClass(2): " + e2 );
	    e2.printStackTrace( );
	    UWUtility.LogExit( );
	    return null;
	}

    	UWUtility.LogExit( );
	return classObj; // return an instantiated object
    }

    /**
     * Injects a new agent to a given destination host.
     *
     * @param uwa          a new UWAgent
     * @param destHostname the destination host IP name
     * @return true upon a successful agent injection.
     */
    private final boolean injectAgent( UWAgent uwa, String destHostname ) {
    	UWUtility.LogEnter( "agentId = " + uwa.getAgentId( ) + 
			    ", destHostname = " + destHostname );
	
	// verify my class name list that is inherited by a new agent.
	if ( agentClassNames.isEmpty( ) ) {
	    UWUtility.Log( "Error: no class name" );
	    UWUtility.LogExit( );
	    return false;
	}
	
	// copy my class name list to the temporary list that is passed 
	// to the destination.
	List<String> tmpClassNames = new ArrayList<String>( );
	for ( int i = 0; i < agentClassNames.size( ); i++ ) {
	    tmpClassNames.add( agentClassNames.get( i ) );
	    
	}
	// do we really need this?
	agentClassNames.clear( );
	
	// now dispatch this agent to the destination.
	boolean success =
	    getPlace( ).sendAgent( uwa, tmpClassNames, destHostname );
    	UWUtility.LogExit( );

	return success;
    }
    
    /**
     * Retrieves the number of chidren that have been spawned by this agent.
     * This does not mean the number of children that are still alive. 
     *
     * @return the number of active children.
     */
    public int getNumChildren( ) {
	return childIds.size( );
    }
    
    /** 
     * Retrieves a vector of the identifiers of children that have been spawned
     * so far. Each identifier is maintained in an Integer object. This does
     * not mean that all identifiers are still valid. Some children may have
     * already gone.
     *
     * @return a vector of child identifiers created so far.
     */
    public Vector getChildIds( ) {
	return childIds;
    }

    /**
     * Return the number of children that are still running.
     *
     * @return the number of children that are still running.
     */
    public int getNumAliveChildren( ) {
	return numAliveChildren;
    }
    
    // print functions ///////////////////////////////////////////////////////
    /**
     * Prints the information of all agents workig on the local UWPlace.
     */
    public void printAgentInfo( ) {
	place.printAgentInfo( );
    }
    
    /**
     * Prints the contents of agent ip directory in a simple format.
     */
    public void printAgentLocationIpDirectory( ) {
	printAgentLocationIpDirectory( false );
    }

    /**
     * Prints the contents of agent ip directory.
     * @param simple if it is true, print only the IPs of agent 0 - 9, 
     *               otherwise the IPs of all agents registered to this
     *               directory.
     */
    public void printAgentLocationIpDirectory( boolean simple ) {
	if ( simple == true ) {
	    UWUtility.LogInfo( "##########" );
	    UWUtility.LogInfo( "# myId = " + getAgentId( ) );
	} else {
	    UWUtility.LogInfo( "##########" );
	    UWUtility.LogInfo( "# myId = " + getAgentId( ) + 
			       " this = " + this +
			       "... agentIpDirectory = " + agentIpDirectory );
	}

	synchronized( agentIpDirectory ) {
	    int size = agentIpDirectory.size( );
	    UWUtility.LogInfo( "# size = " + size );
	    if ( size == 0 ) {
		if ( simple == true ) 
		    UWUtility.LogInfo( "\tagentIpDirectory size = 0" );
		else
		    UWUtility.LogInfo( "\tagentIpDirectory size = 0" );
	    } else {
		if ( simple == true ) {
		    for ( int i = 0; i < 10; i++ ) {
			UWUtility.LogInfo( "# " + i + ": " + 
				  agentIpDirectory.get( new Integer( i ) ) );
		    }
		}
		else {
		    for ( Enumeration e = agentIpDirectory.keys( ); 
			  e.hasMoreElements( ); ) {
			int id = ( ( Integer ) e.nextElement( ) ).intValue( );
			InetAddress ip 
			    = agentIpDirectory.get( new Integer( id ) );
			UWUtility.LogInfo( "\t\tdisplay:id = " + id + 
					   ", ip = " + ip );
		    }
		}
	    }
	}
    }

    /**
     * Prints the contents of agent ip cache.
     */
    public void printAgentLocationIpCache( ) {
	UWUtility.LogInfo( "##########" );
	UWUtility.LogInfo( "# myId = " + getAgentId( ) + " this = " + this +
			   "... agentIpCache = " + agentIpCache );

	synchronized( agentIpCache ) {
	    int size = agentIpCache.size( );
	    UWUtility.LogInfo( "# size = " + size );
	    if ( size == 0 ) {
		UWUtility.LogInfo( "\tagentIpCache size = 0" );
	    } else {
		for ( Enumeration e = agentIpCache.keys( ); 
		      e.hasMoreElements( ); ) {
		    int id = ( ( Integer ) e.nextElement( ) ).intValue( );
		    InetAddress ip = agentIpCache.get( new Integer( id ) );
		    UWUtility.LogInfo( "\t\tdisplay:id = " + id + 
				       ", ip = " + ip );
		}
	    }
	}
    }
    
    // Immediate Cascading Termination ///////////////////////////////////////
    // It is an obsolete feature
    // If true, the parent has requested that this agent terminate itself and 
    // its descendants
    private boolean terminationRequest = false;
    
    /**
     * Schedules an immediate termination. This signal is forwarded to all the
     * descendants.
     */
    public void setTerminationRequest() {
	terminationRequest = true;
	
	// Recursively set flag for descendants
	Vector<Integer> allRecipients = new Vector<Integer>( );
	for (int i = 0; i < childIds.size( ); i++) {
	    allRecipients.add( childIds.elementAt( i ) );
	}
	
	while ( !allRecipients.isEmpty( ) ) {
	    int recipId = ( ( Integer ) 
			    ( allRecipients.remove( 0 ) ) ).intValue( );
	    InetAddress recipAddr = getAgentIp( recipId );
	}
    }

    /**
     * Checks if an immediate termination has been scheduled.
     * @return true if an immediate termination has been scheduled.
     */
    public boolean getTerminationRequest( ) {
	return terminationRequest;
    }
}
