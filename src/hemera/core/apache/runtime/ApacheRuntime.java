package hemera.core.apache.runtime;

import java.io.IOException;

import hemera.core.environment.config.Configuration;
import hemera.core.execution.interfaces.IExecutionService;
import hemera.core.execution.interfaces.task.handle.ICyclicTaskHandle;
import hemera.core.structure.runtime.Runtime;

/**
 * <code>ApacheRuntime</code> defines the implementation
 * of a runtime environment based on the Apache basic IO
 * HTTP core connector.
 *
 * @author Yi Wang (Neakor)
 * @version 1.0.0
 */
public class ApacheRuntime extends Runtime {
	/**
	 * The <code>Configuration</code> for the runtime.
	 */
	private final Configuration config;
	/**
	 * The <code>ICyclicTaskHandle</code> for the
	 * connection listener task.
	 */
	private ICyclicTaskHandle listenerHandle;

	/**
	 * Constructor of <code>ApacheRuntime</code>.
	 * @param service The <code>IExecutionService</code>
	 * used to dispatch request processing.
	 * @param config The <code>Configuration</code> for
	 * the runtime.
	 */
	public ApacheRuntime(final IExecutionService service, final Configuration config) {
		super(service);
		this.config = config;
	}

	@Override
	protected void activateComponents() throws Exception {
		final int port = this.config.runtime.socket.port;
		final int timeout = this.config.runtime.socket.timeout;
		final int buffersize = this.config.runtime.socket.bufferSize;
		final String certPath = this.config.runtime.socket.certPath;
		final String keyPass = this.config.runtime.socket.keyPass;
		final String appname = "Hemera/1.1";
		// Submit the connection listener task.
		final RequestHandler handler = new RequestHandler(this.service.getExceptionHandler(), this);
		try {
			final ConnectionListener listener = new ConnectionListener(this.service, port, timeout,
					buffersize, certPath, keyPass, appname, handler);
			this.listenerHandle = this.service.submit(listener);
		} catch (final IOException e) {
			this.logger.severe("Binding server socket on port: " + port + " failed.");
			throw e;
		}
	}

	@Override
	protected void shutdownComponents() throws Exception {
		this.listenerHandle.terminate();
	}
}
