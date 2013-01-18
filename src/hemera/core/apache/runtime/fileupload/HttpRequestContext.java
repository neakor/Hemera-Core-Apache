package hemera.core.apache.runtime.fileupload;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.fileupload.RequestContext;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;

/**
 * <code>HttpRequestContext</code> defines the internal
 * implementation that provides the request context for
 * a <code>HttpEntityEnclosingRequest</code>.
 *
 * @author Yi Wang (Neakor)
 * @version 1.0.0
 */
class HttpRequestContext implements RequestContext {
	/**
	 * The <code>HttpEntity</code> of the request.
	 */
	private final HttpEntity entity;
	
	/**
	 * Constructor of <code>HttpRequestContext</code>.
	 * @param request The <code>HttpEntityEnclosingRequest</code>
	 * this context is based on.
	 */
	HttpRequestContext(final HttpEntityEnclosingRequest request) {
		this.entity = request.getEntity();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		if (this.entity == null) return null;
		return this.entity.getContent();
	}

	@Override
	public String getContentType() {
		if (this.entity == null) return null;
		final Header header = this.entity.getContentType();
		if (header == null) return null;
		return header.getValue();
	}

	@Override
	public int getContentLength() {
		if (this.entity == null) return 0;
		return (int)this.entity.getContentLength();
	}

	@Override
	public String getCharacterEncoding() {
		if (this.entity == null) return null;
		final Header header = this.entity.getContentEncoding();
		if (header == null) return null;
		return header.getValue();
	}
}
