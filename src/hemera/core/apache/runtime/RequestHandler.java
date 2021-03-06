package hemera.core.apache.runtime;

import hemera.core.apache.runtime.fileupload.HttpRequestParser;
import hemera.core.execution.interfaces.IExceptionHandler;
import hemera.core.structure.enumn.EHttpMethod;
import hemera.core.structure.enumn.EHttpStatus;
import hemera.core.structure.interfaces.IProcessor;
import hemera.core.structure.interfaces.IRequest;
import hemera.core.structure.interfaces.IResource;
import hemera.core.structure.interfaces.IResourceRegistry;
import hemera.core.structure.interfaces.IResponse;
import hemera.core.utility.logging.FileLogger;
import hemera.core.utility.uri.RESTURI;

import java.net.SocketTimeoutException;
import java.util.Map;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.json.JSONObject;

/**
 * <code>RequestHandler</code> defines an internal unit
 * registered with <code>HttpRequestHandlerRegistry</code>
 * by the <code>ConnectionListener</code> to parse the
 * incoming HTTP requests into processor requests and
 * route the requests to the corresponding processors.
 * <p>
 * <code>RequestHandler</code> is invoked with a new
 * HTTP request when <code>ConnectionHandler</code>
 * dispatches a new request received on the responsible
 * connection. This entire process is executed by the
 * dedicated executor thread that runs the handler for
 * a particular connection. Therefore, the processor
 * is also invoked by the dedicated connection thread.
 * <p>
 * There is only a single instance of this handler in
 * the runtime environment that handles all requests of
 * all connections. Therefore, this implementation must
 * provide thread-safety as well as high concurrency
 * capabilities.
 *
 * @author Yi Wang (Neakor)
 * @version 1.0.7
 */
class RequestHandler implements HttpRequestHandler {
	/**
	 * The <code>IExceptionHandler</code> instance
	 * used by the runtime environment.
	 */
	private final IExceptionHandler handler;
	/**
	 * The <code>IResourceRegistry</code> instance.
	 */
	private final IResourceRegistry registry;
	/**
	 * The <code>HttpRequestParser</code> instance.
	 */
	private final HttpRequestParser parser;
	/**
	 * The <code>FileLogger</code> instance.
	 */
	private final FileLogger logger;

	/**
	 * Constructor of <code>RequestHandler</code>.
	 * @param handler The <code>IExceptionHandler</code>
	 * instance used by the runtime environment.
	 * @param runtime The <code>IResourceRegistry</code>
	 * of the hosting runtime environment.
	 */
	RequestHandler(final IExceptionHandler handler, final IResourceRegistry registry) {
		this.handler = handler;
		this.registry = registry;
		this.parser = new HttpRequestParser();
		this.logger = FileLogger.getLogger(this.getClass());
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void handle(final HttpRequest httpRequest, final HttpResponse httpResponse, final HttpContext context) {
		try {
			// Parse URI.
			final String uriStr = httpRequest.getRequestLine().getUri();
			final RESTURI uri = new RESTURI(uriStr);
			final EHttpMethod method = EHttpMethod.parse(httpRequest.getRequestLine().getMethod());
			// Retrieve resource.
			final IResource resource = this.registry.getResource(uri, method);
			if (resource == null) throw new UnsupportedOperationException(uriStr);
			// Retrieve processor.
			final String[] path = uri.getElementArray();
			final IProcessor processor = resource.getProcessor(path, method);
			if (processor == null) throw new UnsupportedOperationException(uriStr);
			// Parse request arguments.
			final Map<String, Object> arguments = this.parser.parseArguments(httpRequest);
			// Create processor request.
			final Class<? extends IRequest> requestclass = processor.getRequestType();
			final IRequest request = requestclass.newInstance();
			try {
				request.parse(path, arguments);
			} catch (final Exception e) {
				throw new IllegalArgumentException(e.getMessage());
			}
			// Invoke processor based on redirect behavior.
			final String callbackArg = (String)arguments.get("callback");
			switch (processor.getRedirectBehavior(request)) {
			case Invoke:
				this.invoke(processor, request, httpResponse, callbackArg);
				break;
			case RedirectBeforeInvoke:
				final String beforeInvokeRedirectURI = processor.getRedirectURI(request);
				httpResponse.setStatusCode(EHttpStatus.C307_TemporaryRedirect.code);
				httpResponse.setHeader("Location", beforeInvokeRedirectURI);
				break;
			case RedirectAfterInvoke:
				final IResponse response = this.invoke(processor, request, httpResponse, callbackArg);
				final String afterInvokeRedirectURI = processor.getRedirectURI(request, response);
				httpResponse.setStatusCode(EHttpStatus.C307_TemporaryRedirect.code);
				httpResponse.setHeader("Location", afterInvokeRedirectURI);
				break;
			default: throw new IllegalArgumentException("Unsupported redirect behavior");
			}
		} catch (final SocketTimeoutException e) {
			// Log warning if client timed out.
			this.logger.warning("Client socket timed out.");
		} catch (final UnsupportedOperationException e) {
			this.setUncaughtExceptionResponse(httpResponse, EHttpStatus.C404_NotFound, e);
		} catch (final IllegalArgumentException e) {
			this.setUncaughtExceptionResponse(httpResponse, EHttpStatus.C400_BadRequest, e);
		} catch (final Exception e) {
			this.handler.handle(e);
			this.setUncaughtExceptionResponse(httpResponse, EHttpStatus.C500_InternalServerError, e);
		}
	}
	
	/**
	 * Invoke the processor to process given request
	 * and commit to given HTTP response with given
	 * callback argument. This method will set the
	 * response's HTTP status code based on produced
	 * response and also set the response entity with
	 * the response JSON data.
	 * @param processor The <code>IProcessor</code> to
	 * process the request.
	 * @param request The <code>IRequest</code> to be
	 * processed.
	 * @param httpResponse The <code>HttpResponse</code>
	 * to commit to.
	 * @param callbackArg The <code>String</code> call
	 * back argument.
	 * @return The produced <code>IResponse</code>.
	 * @throws Exception If any processing failed.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private IResponse invoke(final IProcessor processor, final IRequest request, final HttpResponse httpResponse, final String callbackArg) throws Exception {
		final IResponse response = processor.process(request);
		// Processor inactive.
		if (response == null) {
			this.setUncaughtExceptionResponse(httpResponse, EHttpStatus.C503_ServiceUnavailable, null);
		}
		// Commit response.
		else {
			final String jsonstr = response.toJSON().toString();
			final EHttpStatus status = response.getStatus();
			httpResponse.setStatusCode(status.code);
			// JSONP format.
			final boolean useJSONP = (callbackArg != null);
			if (useJSONP) {
				final StringBuilder jsonpBuilder = new StringBuilder();
				jsonpBuilder.append(callbackArg).append("(").append(jsonstr).append(")");
				final String wrapped = jsonpBuilder.toString();
				httpResponse.setEntity(new StringEntity(wrapped, ContentType.APPLICATION_JSON));
			}
			// If no callback function, return in JSON format.
			else {
				httpResponse.setEntity(new StringEntity(jsonstr, ContentType.APPLICATION_JSON));
			}
		}
		return response;
	}
	
	/**
	 * Set the exception response with the given Http
	 * response, status and exception.
	 * @param httpResponse The <code>HttpResponse</code>
	 * to set to.
	 * @param status The <code>EHttpStatus</code> value.
	 * @param exception The optional <code>Exception</code>
	 * to include in the error message.
	 */
	private void setUncaughtExceptionResponse(final HttpResponse httpResponse, final EHttpStatus status, final Exception exception) {
		httpResponse.setStatusCode(status.code);
		try {
			final JSONObject exceptionJSON = new JSONObject();
			exceptionJSON.put("http_status", status.name());
			if (status == EHttpStatus.C404_NotFound) {
				exceptionJSON.put("exception", "No such service provided: " + exception.getMessage());
			} else if (status == EHttpStatus.C400_BadRequest) {
				exceptionJSON.put("exception", "Invalid request: " + exception.getMessage());
			} else if (status == EHttpStatus.C500_InternalServerError) {
				exceptionJSON.put("exception", "A server error has occurred.");
			} else if (status == EHttpStatus.C503_ServiceUnavailable) {
				exceptionJSON.put("exception", "Requested service has been disabled.");
			}
			httpResponse.setEntity(new StringEntity(exceptionJSON.toString()));
		} catch (final Exception ignore) {}
	}
}
