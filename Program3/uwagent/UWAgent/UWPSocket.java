package UWAgent;

/**
 * This is a socket creation and termination class that avoids too much
 * socket creation to the same destination.
 *
 * @author     Munehiro Fukuda (CSS Munehiro Fukuda)
 * @since      7/1/05
 * @version    8/1/06
 */
import java.net.*;
import java.io.*;
import java.util.*;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.nio.ByteBuffer;

public class UWPSocket {
    // Constants
    // Socket timeout, in milliseconds. Required in gateway navigation to 
    // determine whether or not a destination host is behind a gateway. 
    // If the host is unreachable, we don't want to wait for the default 
    // timeout duration (several minutes).
    private static final int SOCKET_TIMEOUT = 5000; // 5 seconds   
   
    // variables provided from UWPlace
    private ServerSocket srvr = null;    // To accept a new agent or a message
    private boolean isSSL = false;       // Indicating if SSL is requested
    private Hashtable<String, Integer> 
	tunnelIpPort = null;             // a set of ( ipName, localPort )

    // client-side hashtable
    private Object clientSync = null;    // mutual exclusion among clients
    private Hashtable<String, Socket> 
	host2ssock = null;               // <hostname, sender_socket>
    private Hashtable<OutputStream, Socket> 
	out2ssock  = null;               // <outputstream, sender_socket>
    private Hashtable<Socket, Thread>
	ssock2thread = null;             // <sender_socket, sender_thread>

    // server-side hashtable
    private Hashtable<InputStream, Socket> 
	in2rsock = null;                 // <inputstream, receiver_socket>
    
    /**
     * Is the constructor that initializes all hashtables to maintain sender
     * and receiver sockets.
     * @param srvrSock a server socket allocated to the local UWPlace.
     * @param isSSL true if SSL communication is requested, otherwise false
    */
    public UWPSocket( ServerSocket srvrSock, boolean isSSL, 
		      Hashtable tunnelIpPort ) {
	this.srvr = srvrSock;
	this.isSSL = isSSL;
	this.tunnelIpPort = tunnelIpPort;

	// client-side initialization
	clientSync = new Object( );
	host2ssock = new Hashtable<String, Socket>( );
	out2ssock = new Hashtable<OutputStream, Socket>( );
	ssock2thread = new Hashtable<Socket, Thread>( );

	// server-side initialization
	in2rsock = new Hashtable<InputStream, Socket>( );
    }

    /**
     * Creates a client-side socket.
     *
     * @param messageType  the type of a message sent along this connection
     * @param hostName     the name of a destination host
     * @param portNum      the port number of this socket connection
     * @param funcName     the function name to call at the destination
     * @param headerParam1 parameter 1 passed to the fucntion 
     * @param headerParam2 parameter 2 passed to the function
     * @param isSSL        true if SSL is requested, otherwise false
     * @return an outputstream associated with this client-side socket 
     */
    public OutputStream initUWPSocket( int messageType, String hostName,
				       String portNum, String funcName, 
				       int headerParam1, int headerParam2, 
				       boolean isSSL )
	throws SocketTimeoutException, UnknownHostException {


	// check if a hop should be through a ssh tunnel
	Integer localPort = tunnelIpPort.get( hostName );
	if ( localPort == null ) {
	    String[] cannonicalIp = hostName.split( "." );
	    if ( cannonicalIp != null && cannonicalIp.length > 0 )
		localPort = tunnelIpPort.get( cannonicalIp[0] );
	}

	// if ( localPort != null ), tunnel thru localPort to destination
	String destIp = ( localPort == null ) ? hostName : "localhost";
	String destPort = 
	    ( localPort == null ) ? portNum : localPort.toString( );

	UWUtility.LogInfo( "UWPSocket.initUWPSocket:" +
			   " hostName = " + hostName +
			   " messageType = " + messageType +
			   " funcName = " + funcName + 
			   " thread = " + Thread.currentThread( ) );
	    
	// open a client-side socket and get its output stream.
	OutputStream out = getOutputStream( destIp, destPort, isSSL );
	// send a header
	if ( out != null )
	    sendHeader( out, messageType, funcName, headerParam1, headerParam2 );
	// return an outputstream to send more data
	return out;
    }

    /**
     * Opens a client-side socket and gets its output stream.
     *
     * @param  hostName the name of a destination host
     * @param  portNum  the port number of this socket connection
     * @param  isSSL    true if SSL is requested, otherwise false
     * @return an output stream associated with this new client-side socket
     */
    private OutputStream getOutputStream( String hostName, 
					       String portNum, boolean isSSL ) 
	throws SocketTimeoutException, UnknownHostException {

	// multiple threads may try to access client-side hashtables.
	synchronized( clientSync ) {
	    while ( true ) {
		try {
		    // check if someone else has established a socket to a 
		    // given host.
		    Socket socket = host2ssock.get( hostName );
		    if ( socket == null ) {
			// no sockets established to the host
			// creates a socket    
			if ( isSSL ) {
			    SSLSocketFactory sslsocketfactory =
				( SSLSocketFactory )SSLSocketFactory.
				getDefault( );
			    socket = ( SSLSocket )sslsocketfactory.
				createSocket( );
			}
			else {
			    socket = new Socket( );
			}
			// name to "localhost" in this case.
			InetAddress local = InetAddress.getLocalHost( );
			if ( local.getHostName( ).equals( hostName ) || 
			     local.getCanonicalHostName( ).equals( hostName ) || 
			     local.getCanonicalHostName( ).startsWith( hostName ) ) {
			    hostName = "localhost";
			}
			
			// creates a destination inet address.
			InetSocketAddress isaddr = new
			    InetSocketAddress( hostName, 
					       Integer.parseInt(portNum) );
			
			// establish a connection to the destination and 
			// get its output stream.
			socket.connect( isaddr, SOCKET_TIMEOUT );
			OutputStream out = socket.getOutputStream( );

			// associate it with my thread reference
			ssock2thread.put( socket, Thread.currentThread( ) );
			    
			// associate it with the destination host name
			host2ssock.put( hostName, socket );
			
			// associate it with this new output stream
			out2ssock.put( out, socket );
			
			return out;
		    }

		    // check if the socket owner is actually still alive
		    Thread owner = ssock2thread.get( socket );
		    if ( owner == null ) {
			System.err.println("no owner for a given UWPSocket " );
			System.exit( -1 );
		    }
			
		    if ( owner.isAlive( ) == false ||      // owner dead or
			 owner == Thread.currentThread()|| //interrupted before
			 owner.toString( ).equals( Thread.currentThread( ).
						   toString( ) )
			 ) {
			// remove this dead/interrupted owner. 
			ssock2thread.remove( socket );
			host2ssock.remove( hostName );
			
			for ( Enumeration e = out2ssock.keys( ); 
			      e.hasMoreElements( ); ) {
			    OutputStream out = 
				( OutputStream )e.nextElement( );

			    if ( out2ssock.get( out ) == socket ) {
				// this is the orphan thread.
				out2ssock.remove( out );

				try {
				    out.flush( );
				    out.close( );
				    socket.close( );
				} catch ( IOException ioe ) {
				    // the server might have already closed it.
				    // or this connection might have been
				    // previously interrupted.
				    ioe.printStackTrace( );
				}
			    }
			}
		    }
		    else {
			// a socket has established to the host by someone.
			// wait for it to be reliquished by the current client
			clientSync.wait( );
		    }
		    
		    // guarantteed that no socket exists to the given host
		    // go back to the top of loop to create a new socket.
		
		} catch ( SocketTimeoutException e ) {
		    System.err.println( "!!!SOCKET to " + hostName + 
					" TIMEOUT by thread: " + 
					Thread.currentThread( ) );
		    e.printStackTrace( );
		    System.exit( -1 );
		    throw e;
		} catch ( UnknownHostException e ) {
		    // Host name not found; caller needs to deal with this 
		    throw e;
		} catch ( IOException ioe ) {
		    ioe.printStackTrace( );
		    return null;
		} catch ( InterruptedException e ) {
		    e.printStackTrace( );
		    return null;
		} // end of try
	    } // end of while
	} // end of synchronized
    }

    /**
     * Sends a message header on a given output stream.
     *
     * @param out          the output stream on which a header is sent
     * @param msgType      the type of a message sent along this connection
     * @param funcName     the function name to call at the destination
     * @param headerParam1 parameter 1 passed to funcName
     * @param headerParam2 parameter 2 passed to funcName
     */
    private void sendHeader( OutputStream out, int msgType, String funcName, 
			     int headerParam1, int headerParam2 ) {
	// Set up a 40-byte header with the following structure
	// message type (int) | function name (string) | 
	// header parameter 1 (int) | header parameter 2 (int)

	// header
	ByteBuffer headerBuff = ByteBuffer.allocate( UWUtility.HEADER_SIZE ); 

	// message type
	headerBuff.putInt( msgType ); 
	
	try {
	    // function name
	    byte[] bytFuncName = funcName.getBytes( "UTF8" );
	    headerBuff.put( bytFuncName );
	    for ( int i = bytFuncName.length; i< UWUtility.FUNC_NAME_SIZE; 
		  i++ ) {
		// pad with spaces to end of func name field
		headerBuff.put((byte)' ');	
	    }
	
	    // Parameters passed to the fucntion that will be invoked at
	    // the receiver
	    headerBuff.putInt( headerParam1 );
	    headerBuff.putInt( headerParam2 );
	    
	    // write the header
	    out.write( headerBuff.array( ) ); 
	} catch( Exception e ) {
	    e.printStackTrace( );
	}
    }

    /**
     * Is used to return (i.e. deallocate) a given output stream that has been
     * created from a client-side socket.
     *
     * @param  out an output stream to return or to deallocate.
     * @return true if it has been successfull deallocated.
     */
    public boolean returnOutputStream( OutputStream out ) {
	// multiple threads may use client-side hastables.
	synchronized( clientSync ) {
	    
	    // remove the socket corresponding to this output stream.
	    Socket socket = out2ssock.remove( out );
	    try {
		out.flush( );
		out.close( ); // close output stream.
	    } catch ( IOException ioe ) {
		// the server might have already closed it.
		ioe.printStackTrace( );
	    }
	    if ( socket == null ) {
		return false; // this shouldn't occur
	    }

	    // remove the socket corresponding to this thread
	    ssock2thread.remove( socket );

	    // remove the destination host name of this socket and close 
	    // the socket.
	    for ( Enumeration e = host2ssock.keys( ); e.hasMoreElements( ); ) {
		String host = ( String )e.nextElement( );
		if ( host2ssock.get( host ) == socket ) {
		    host2ssock.remove( host );
		    try {
			socket.close( ); // a new statement
		    } catch( IOException ioe ) {
			// the server might have already closed it.
			ioe.printStackTrace( );
		    }
		    
		    // now wake up all other client threads that try to 
		    // create a new socket to the same host.
		    clientSync.notifyAll( );
		    return true;
		}
	    }
	    return false;
	}
    }

    public int getAckFromServer( OutputStream out ) {
	Socket socket = null;
	// multiple threads may use client-side hashtables.
	synchronized( clientSync ) {
	    // retrieve the socket corresponding to this output stream.
	    socket = out2ssock.get( out );	
	    if ( socket == null ) {
		// why can't a socket owner get its socket! panic!!
		return -1;
	    }
	}
	int ack = 0;
	try {
	    // now establish an input stream
	    InputStream in = socket.getInputStream( );
	    byte b[] = new byte[1];
	    // read an 1-byte ack from the server
	    if ( in.read( b ) == -1 ) // socket has been already closed 
		return -2;
	    ack = b[0];
	} catch ( IOException e ) {
	    e.printStackTrace( );
	}
	
	return ack;
    }

    // CURRENTLY, NOT USED
    /**
     * Accepts a new socket connection, (i.e., a server-side socket).
     * @return a new server-side socket.
     */
    public InputStream getInputStream( ) {
	// System.err.println( "UWPSocket.retriveInputStream: starts" );

	Socket socket = null;
	InputStream in = null;
	// System.err.println( "UWPSocket.retriveInputStream:getSoTimeout"+
	//		"= " + getSoTimeout( ) );

	// accept a new socket
	try {
	    if ( isSSL ) {
		socket = ( SSLSocket )srvr.accept( );
	    } else {
		socket = srvr.accept( );
	    }
	} catch ( SocketTimeoutException e ) {
	} catch ( IOException e ) {
	    e.printStackTrace( );
	}

	// get a new input stream from this socket.
	try { 
	    if ( socket != null ) {
		in = socket.getInputStream( );
		in2rsock.put( in, socket ); // associate it with this socket
		return in;
	    }
	} catch ( IOException e ) {
	    e.printStackTrace( );
	}
	return null;
    }

    // CURRENTLY, NOT USED
    /**
     * Is used to return (i.e. deallocate) a given input stream that has been
     * created from a server-side socket.
     *
     * @param  in an input stream to return or to deallocate.
     * @return true if the input stream and its socket have been deallocated,
     *         otherwise false
     */
    public boolean returnInputStream( InputStream in ) {
	// remove the server-side socket corresponding to the given input 
	// stream
	Socket socket = in2rsock.remove( in );
	if ( socket != null ) {
	    try {
		in.close( );
		socket.close( );
	    } catch ( IOException e ) {
		// the client might have already closed it.
		e.printStackTrace( );
	    }
	    // System.err.println( "UWPSockete.returnInputStream:" + 
	    //		"in = " + in );
	    return true;
	}
	else
	    return false;
    }
}
