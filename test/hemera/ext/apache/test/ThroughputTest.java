package hemera.ext.apache.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;

public class ThroughputTest {
	
	private final int senderCount;
	private final long duration;
	
	public ThroughputTest(final int senderCount, final long duration) {
		this.senderCount = senderCount;
		this.duration = duration;
	}

	public void start() throws Exception {
		System.out.println("Creating senders...");
		final AtomicInteger sentCount = new AtomicInteger();
		final AtomicInteger retryCount = new AtomicInteger();
		final AtomicBoolean terminate = new AtomicBoolean(false);
		final Thread[] senders = new Thread[this.senderCount];
		for (int i = 0; i < this.senderCount; i++) {
			final boolean usebody = (i%2 == 0);
			final Thread thread = new Thread(new RequestSender(usebody, sentCount, retryCount, terminate));
			senders[i] = thread;
		}
		
		System.out.println("Sending requests...");
		for (int i = 0; i < this.senderCount; i++) senders[i].start();
		TimeUnit.MILLISECONDS.sleep(this.duration);
		terminate.set(true);
		
		final int totalcount = sentCount.get();
		System.err.println(totalcount + " requests completed in " + this.duration + " milliseconds with " + retryCount.get() + " retries.");
		System.err.println(this.senderCount + " concurrent connections.");
		final double throughput = (double)totalcount / (double)TimeUnit.SECONDS.convert(this.duration, TimeUnit.MILLISECONDS);
		final String rawvalue = String.valueOf(throughput);
		final int index = rawvalue.indexOf(".");
		final String value = rawvalue.substring(0, index+2);
		System.err.println("Throughput: " + value + " requests/second.");
	}

	public static void main(String[] args) throws Exception {
		final int senderCount = 250;
		final long duration = TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS);
		new ThroughputTest(senderCount, duration).start();
	}

	private class RequestSender implements Runnable {

		private final boolean useBody;
		private final AtomicInteger sentCount;
		private final AtomicInteger retryCount;
		private final AtomicBoolean terminate;
		private final String urlbase = "http://localhost:8080/hello/hi";
		
		private RequestSender(final boolean useBody, final AtomicInteger sentCount, final AtomicInteger retryCount, final AtomicBoolean terminate) {
			this.useBody = useBody;
			this.sentCount = sentCount;
			this.retryCount = retryCount;
			this.terminate = terminate;
		}

		@Override
		public void run() {
			while (!this.terminate.get()) {
				try {
					final URL url = this.buildURL();
					final HttpURLConnection connection = this.sendRequest(url);
					final String response = this.readResponse(connection);
					this.parseResponse(response);
					this.sentCount.incrementAndGet();
				} catch (final Exception e) {
					this.retryCount.incrementAndGet();
				}
			}
		}
		
		private URL buildURL() throws MalformedURLException, UnsupportedEncodingException {
			if (this.useBody) {
				return new URL(this.urlbase);
			} else {
				final String encodedName = URLEncoder.encode("Yi", "UTF-8");
				final StringBuilder urlstr = new StringBuilder();
				urlstr.append(this.urlbase).append("?");
				urlstr.append("name").append("=").append(encodedName);
				final URL url = new URL(urlstr.toString());
				return url;
			}
		}
		
		private HttpURLConnection sendRequest(final URL url) throws Exception {
			final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			if (this.useBody) {
				final OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
				final String encodedName = URLEncoder.encode("Yi", "UTF-8");
				final StringBuilder builder = new StringBuilder();
				builder.append("name").append("=").append(encodedName);
				writer.write(builder.toString());
				writer.flush();
				writer.close();
			}
			return connection;
		}
		
		private String readResponse(final HttpURLConnection connection) throws Exception {
			final InputStreamReader streamreader = new InputStreamReader(connection.getInputStream());
			final BufferedReader reader = new BufferedReader(streamreader);
			final StringBuilder builder = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}
			reader.close();
			return builder.toString();
		}
		
		private void parseResponse(final String response) throws Exception {
			final JSONObject json = new JSONObject(response);
			final String value = json.getString("response");
			final boolean succeeded = !value.contains("error");
			if (!succeeded) {
				System.err.println(value);
			}
		}
	}
}
