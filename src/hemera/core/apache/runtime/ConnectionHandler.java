package hemera.core.apache.runtime;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpServerConnection;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpService;

import hemera.core.execution.interfaces.task.ICyclicTask;

/**
 * <code>ConnectionHandler</code> defines an internal
 * unit that handles a single accepted connection to
 * dispatch the received requests. The handler is
 * submitted to the execution service as a cyclic
 * task by the <code>ConnectionListener</code> after
 * a new connection is accepted.
 * <p>
 * <code>ConnectionHandler</code> will terminate if
 * the connection is closed or any IO error occurs.
 *
 * @author Yi Wang (Neakor)
 * @version 1.0.0
 */
class ConnectionHandler implements ICyclicTask {
	/**
	 * The <code>HttpService</code> instance used by
	 * the connection listener.
	 */
	private final HttpService httpService;
	/**
	 * The <code>HttpServerConnection</code> accepted
	 * by the connection listener that this handler is
	 * responsible for.
	 */
	private final HttpServerConnection connection;
	/**
	 * The <code>HttpContext</code> shared by all the
	 * requests received in the responsible connection.
	 */
	private final HttpContext context;

	/**
	 * Constructor of <code>ConnectionHandler</code>.
	 * @param httpService The <code>HttpService</code>
	 * instance used by the connection listener.
	 * @param connection The <code>HttpServerConnection</code>
	 * accepted by the connection listener that this
	 * handler is responsible for.
	 */
	ConnectionHandler(final HttpService httpService, final HttpServerConnection connection) {
		this.httpService = httpService;
		this.connection = connection;
		this.context = new BasicHttpContext();
	}

	@Override
	public boolean execute() throws Exception {
		try {
			// Self-terminate if connection is closed.
			if (!this.connection.isOpen()) return false;
			// Dispatch requests to request router.
			else {
				this.httpService.handleRequest(this.connection, this.context);
				return true;
			}
		} catch (final ConnectionClosedException e) {
			// Terminate since connection closed.
			return false;
		} catch (final IOException e) {
			// Terminate on IO error.
			return false;
		} catch (final HttpException e) {
			// Terminate on unrecoverable HTTP protocol violation.
			return false;
		}
	}
	
	@Override
	public void cleanup() throws Exception {
		try {
			if (this.connection.isOpen()) {
				this.connection.shutdown();
			}
		} catch (final IOException ignore) {}
	}

	@Override
	public void signalTerminate() throws Exception {
		this.connection.shutdown();
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
