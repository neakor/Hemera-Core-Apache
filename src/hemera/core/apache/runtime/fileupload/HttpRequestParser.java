package hemera.core.apache.runtime.fileupload;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.util.EntityUtils;

/**
 * <code>HttpRequestParser</code> defines the HTTP
 * request utility implementation that provides the
 * support to parse a received HTTP request and
 * retrieve the contained contents data.
 *
 * @author Yi Wang (Neakor)
 * @version 1.0.1
 */
public class HttpRequestParser {

	/**
	 * Parse out all the arguments including both URI
	 * arguments and body arguments from given request.
	 * @param httpRequest The <code>HttpRequest</code>
	 * to parse.
	 * @return The <code>Map</code> of arguments with
	 * <code>String</code> key and <code>Object</code>
	 * value. The value can either of of type array of
	 * <code>byte</code> or <code>String</code>.
	 * @throws FileUploadException If request body
	 * parsing failed.
	 * @throws IOException If entity retrieval failed.
	 * @throws ParseException If entity parsing failed.
	 * @throws URISyntaxException If given request's URI
	 * has syntax error.
	 */
	public Map<String, Object> parseArguments(final HttpRequest httpRequest) throws FileUploadException, ParseException, IOException, URISyntaxException {
		final Map<String, Object> arguments = new HashMap<String, Object>();
		// Parse request URI arguments.
		final URI uri = new URI(httpRequest.getRequestLine().getUri());
		this.parseArguments(uri, "UTF-8", arguments);
		// Parse request body arguments.
		this.parseBody(httpRequest, arguments);
		return arguments;
	}
	
	/**
	 * Parse the request body to retrieve the contents
	 * and store them in the given map.
	 * @param request The <code>HttpRequest</code> to
	 * parse.
	 * @param store The storage <code>Map</code> of
	 * <code>String</code> key to <code>Object</code>
	 * value pairs of the request contents. The value
	 * is either of type <code>String</code> or array
	 * of <code>byte</code>. <code>null</code> if the
	 * request doesn't have an entity body.
	 * @throws FileUploadException If content parsing
	 * failed.
	 * @throws IOException If entity retrieval failed.
	 * @throws ParseException If entity parsing failed.
	 * @throws URISyntaxException If given request's URI
	 * has syntax error.
	 */
	private void parseBody(final HttpRequest request, final Map<String, Object> store) throws FileUploadException, ParseException, IOException, URISyntaxException {
		// Request does not have an entity.
		if (!(request instanceof HttpEntityEnclosingRequest)) return;
		else {
			final HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest)request;
			final HttpEntity entity = entityRequest.getEntity();
			if (entity == null) return;
			// Parse based on entity content type.
			final Header typeheader = entity.getContentType();
			final String contentType = (typeheader==null) ? "" : typeheader.getValue();
			if (contentType.contains("multipart")) {
				this.parseMultipartBody(entityRequest, store);
			}
			// Parse as URL encoded.
			else {
				final String entityContent = EntityUtils.toString(entity);
				final Header encodingHeader = entity.getContentEncoding();
				final String encoding = (encodingHeader==null) ? "UTF-8" : encodingHeader.getValue();
				this.parseArguments(entityContent, encoding, store);
				// Note, parsing the entity directly using URLEncodedUtils.parse(Entity)
				// does not seem to parse properly sometimes.
			}
		}
	}

	/**
	 * Parse the given URL encoded URI into arguments
	 * and store them in the given storage.
	 * @param uri The <code>URI</code> to be parsed.
	 * @param encoding The <code>String</code> encoding
	 * name.
	 * @param store The storage <code>Map</code> of
	 * <code>String</code> key to <code>Object</code>
	 * value pairs of the request contents.
	 */
	private void parseArguments(final URI uri, final String encoding, final Map<String, Object> store) {
		final List<NameValuePair> uriArguments = URLEncodedUtils.parse(uri, encoding);
		if (uriArguments != null && !uriArguments.isEmpty()) {
			final int size = uriArguments.size();
			for (int i = 0; i < size; i++) {
				final NameValuePair pair = uriArguments.get(i);
				// May be null since it could be part of the URI.
				if (pair.getName() != null && pair.getValue() != null) {
					store.put(pair.getName(), pair.getValue());
				}
			}
		}
	}
	
	/**
	 * Parse the given URL encoded String into arguments
	 * and store them in the given storage.
	 * @param content The <code>String</code> to be parsed.
	 * @param encoding The <code>String</code> encoding
	 * name.
	 * @param store The storage <code>Map</code> of
	 * <code>String</code> key to <code>Object</code>
	 * value pairs of the request contents.
	 */
	private void parseArguments(final String content, final String encoding, final Map<String, Object> store) {
		final List<NameValuePair> uriArguments = URLEncodedUtils.parse(content, Charset.forName(encoding));
		if (uriArguments != null && !uriArguments.isEmpty()) {
			final int size = uriArguments.size();
			for (int i = 0; i < size; i++) {
				final NameValuePair pair = uriArguments.get(i);
				// May be null since it could be part of the URI.
				if (pair.getName() != null && pair.getValue() != null) {
					store.put(pair.getName(), pair.getValue());
				}
			}
		}
	}
	
	/**
	 * Parse the entity request's body as a multi-part
	 * entity and store the arguments in given store
	 * using the file upload streaming API.
	 * @param request The <code>HttpEntityEnclosingRequest</code>
	 * request to be parsed.
	 * @param store The storage <code>Map</code> of
	 * <code>String</code> key to <code>Object</code>
	 * value pairs of the request contents. The value
	 * is either of type <code>String</code> or array
	 * of <code>byte</code>. <code>null</code> if the
	 * request doesn't have an entity body.
	 * @throws FileUploadException If content parsing
	 * failed.
	 * @throws IOException If iterating data failed.
	 */
	private void parseMultipartBody(final HttpEntityEnclosingRequest request, final Map<String, Object> store) throws FileUploadException, IOException {
		// Parse the request using request context.
		final FileUpload fileupload = new FileUpload();
		final HttpRequestContext context = new HttpRequestContext(request);
		final FileItemIterator iterator = fileupload.getItemIterator(context);
		while (iterator.hasNext()) {
			final FileItemStream item = iterator.next();
			final String fieldName = item.getFieldName();
			final InputStream stream = item.openStream();
			// Form text data.
			if (item.isFormField()) {
				final String value = Streams.asString(stream);
				store.put(fieldName, value);
			}
			// File data.
			else {
				final byte[] data = IOUtils.toByteArray(stream);
				store.put(fieldName, data);
			}
		}
	}
}
