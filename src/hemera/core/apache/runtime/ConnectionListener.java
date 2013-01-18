package hemera.core.apache.runtime;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.http.HttpResponseInterceptor;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

import hemera.core.execution.interfaces.IExecutionService;
import hemera.core.execution.interfaces.task.ICyclicTask;
import hemera.core.utility.logging.FileLogger;

/**
 * <code>ConnectionListener</code> defines an internal
 * task unit that listens for incoming HTTP connection
 * requests. Once a connection is established, the
 * task creates a new <code>ConnectionHandler</code>
 * instance that handles the dispatching of all the
 * requests sent in that connection. This handler
 * instance is submitted to the execution service for
 * execution.
 * <p>
 * <code>ConnectionListener</code> terminates itself
 * if an IO error occurs when accepting connections.
 *
 * @author Yi Wang (Neakor)
 * @version 1.0.0
 */
class ConnectionListener implements ICyclicTask {
	/**
	 * The <code>FileLogger</code> instance.
	 */
	private final FileLogger logger;
	/**
	 * The <code>IExecutionService</code> used by the
	 * runtime environment.
	 */
	private final IExecutionService service;
	/**
	 * The <code>ServerSocket</code> instance.
	 */
	private final ServerSocket serverSocket;
	/**
	 * The <code>HTTPParams</code> instance.
	 */
	private final HttpParams httpParams;
	/**
	 * The <code>HTTPService</code> instance.
	 */
	private final HttpService httpService;

	/**
	 * Constructor of <code>ConnectionListener</code>.
	 * @param service The <code>IExecutionService</code>
	 * used by the runtime environment.
	 * @param port The <code>int</code> port the listener
	 * should listen on.
	 * @param timeout The <code>int</code> socket
	 * connection timeout value in milliseconds.
	 * @param buffersize The <code>int</code> socket
	 * buffer size value in bytes.
	 * @param certPath The <code>String</code> optional
	 * SSL connection certificate path.
	 * @param keyPass The <code>String</code> optional
	 * password used to protect the certificate public
	 * key.
	 * @param appname The <code>String</code> server
	 * application name used for HTTP response header.
	 * @param handler The <code>RequestHandler</code>
	 * used by the runtime environment to route the
	 * received requests to corresponding processors.
	 * @throws IOException If server socket creation
	 * failed.
	 * @throws CertificateException If loading certificate
	 * failed.
	 * @throws NoSuchAlgorithmException  If initialize key
	 * store failed.
	 * @throws KeyStoreException  If initialize key
	 * store failed.
	 * @throws UnrecoverableKeyException If initialize
	 * key store failed.
	 * @throws KeyManagementException If initialize
	 * key store failed.
	 */
	ConnectionListener(final IExecutionService service, final int port,
			final int timeout, final int buffersize, final String certPath,
			final String keyPass, final String appname, final RequestHandler handler) throws IOException,
			KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		this.logger = FileLogger.getLogger(this.getClass());
		this.service = service;
		// Create server socket.
		this.serverSocket = this.initServerSocket(certPath, keyPass, port);
		// Setup HTTP parameters.
		this.httpParams = this.initHttpParameters(timeout, buffersize, appname);
		// Set up the HTTP protocol processor, using the basic chain.
		final HttpProcessor httpprocessor = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
				new ResponseDate(), new ResponseServer(), new ResponseContent(), new ResponseConnControl()
		});
		// Set up request handler that is invoked when the
		// connection handler dispatches a request.
		final HttpRequestHandlerRegistry handlerReqistry = new HttpRequestHandlerRegistry();
		handlerReqistry.register("*", handler);
		// Set up the HTTP service.
		this.httpService = new HttpService(httpprocessor, new DefaultConnectionReuseStrategy(),
				new DefaultHttpResponseFactory(), handlerReqistry, this.httpParams);
		// Log.
		if (certPath == null) {
			this.logger.info("Connection listener opened on port " + port);
		} else {
			this.logger.info("SSL connection listener opened on port " + port);
		}
	}
	
	/**
	 * Create and initialize the server socket using
	 * the certificate at the given path protected
	 * by given password and listens on the given port.
	 * @param certPath The <code>String</code> optional
	 * path to the SSL certificate.
	 * @param keyPass The <code>String</code> optional
	 * password used to protect the certificate public
	 * key.
	 * @param port The <code>int</code> port to listen
	 * on.
	 * @return The <code>ServerSocket</code> instance.
	 * @throws KeyManagementException If initialize key
	 * store failed.
	 * @throws UnrecoverableKeyException If initialize
	 * key store failed.
	 * @throws KeyStoreException If initialize key store
	 * failed.
	 * @throws NoSuchAlgorithmException If initialize key
	 * store failed.
	 * @throws CertificateException If loading certificate
	 * failed.
	 * @throws FileNotFoundException If certificate path
	 * is invalid.
	 * @throws IOException If certificate is invalid.
	 */
	private ServerSocket initServerSocket(final String certPath, final String keyPass, final int port) throws KeyManagementException,
	UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException {
		// Plain server socket.
		if (certPath == null) return new ServerSocket(port);
		// SSL server socket.
		else {
			final SSLContext context = SSLContext.getInstance("SSL");
			final KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			final char[] keyPassChars = keyPass.toCharArray();
			keyStore.load(new FileInputStream(certPath), keyPassChars);
			keyFactory.init(keyStore, keyPassChars);
			context.init(keyFactory.getKeyManagers(), null, new SecureRandom());
			final SSLServerSocketFactory serverFactory = context.getServerSocketFactory();
			return serverFactory.createServerSocket(port);
		}
	}
	
	/**
	 * Create and initialize the Http parameters.
	 * @param timeout The <code>int</code> socket
	 * connection timeout value in milliseconds.
	 * @param buffersize The <code>int</code> socket
	 * buffer size value in bytes.
	 * @param appname The <code>String</code> server
	 * application name used for HTTP response header.
	 * @return The <code>HttpParams</code> instance.
	 */
	private HttpParams initHttpParameters(final int timeout, final int buffersize, final String appname) {
		final HttpParams httpParams = new SyncBasicHttpParams();
		httpParams.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, timeout);
		httpParams.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, buffersize);
		httpParams.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false);
		httpParams.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true);
		httpParams.setBooleanParameter(CoreConnectionPNames.SO_KEEPALIVE, true);
		httpParams.setParameter(CoreProtocolPNames.ORIGIN_SERVER, appname);
		return httpParams;
	}
	
	@Override
	public boolean execute() throws Exception {
		try {
			// Accept new HTTP connection.
			final Socket socket = this.serverSocket.accept();
			final DefaultHttpServerConnection connection = new DefaultHttpServerConnection();
			connection.bind(socket, this.httpParams);
			// Create connection handler for the new connection.
			final ConnectionHandler handler = new ConnectionHandler(this.httpService, connection);
			// Submit handler for execution.
			this.service.submit(handler);
			return true;
		} catch (final SocketException e) {
			// This could be due to task termination.
			this.logger.info("Connection listener closed.");
			return false;
		} catch (final IOException e) {
			this.logger.exception(e);
			return false;
		}
	}
	
	@Override
	public void cleanup() throws Exception {
		if (!this.serverSocket.isClosed()) {
			this.serverSocket.close();
		}
	}

	@Override
	public void signalTerminate() throws Exception {
		// Close the server socket to wake up execution block.
		this.serverSocket.close();
	}

	@Override
	public int getCycleCount() {
		return 0;
	}

	@Override
	public long getCycleLimit(final TimeUnit unit) {
		return 0;
	}
}
