/**
 * Copyright (c) 2022 EyezahMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.cosmetica.util;

import cc.cosmetica.api.HttpNotOkException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;

public class Response implements Closeable {
	private Response(CloseableHttpClient client, CloseableHttpResponse response) {
		this.client = client;
		this.response = response;
		this.status = this.response.getStatusLine();
	}

	private final CloseableHttpClient client;
	private final CloseableHttpResponse response;
	private final StatusLine status;

	public StatusLine getStatus() {
		return this.status;
	}

	public int getStatusCode() {
		return this.status.getStatusCode();
	}

	public OptionalInt getError() {
		int code = this.getStatusCode();

		if (code >= 200 && code < 300) {
			return OptionalInt.empty();
		} else {
			return OptionalInt.of(code);
		}
	}

	public HttpEntity getEntity() {
		return this.response.getEntity();
	}

	public String getAsString() throws IOException {
		return EntityUtils.toString(this.getEntity(), StandardCharsets.UTF_8);
	}

	public JsonObject getAsJson() throws IOException, JsonParseException {
		String s = EntityUtils.toString(this.getEntity(), StandardCharsets.UTF_8).trim();
		return JsonParser.parseString(s).getAsJsonObject();
	}

	public JsonArray getAsJsonArray() throws IOException, JsonParseException {
		String s = EntityUtils.toString(this.getEntity(), StandardCharsets.UTF_8).trim();
		return JsonParser.parseString(s).getAsJsonArray();
	}

	public JsonElement getAsJsonElement() throws IOException, JsonParseException {
		String s = EntityUtils.toString(this.getEntity(), StandardCharsets.UTF_8).trim();
		return JsonParser.parseString(s);
	}

	@Override
	public void close() throws IOException {
		this.response.close();
		this.client.close();
	}

	public static Response get(String request) throws ParseException, IOException, HttpNotOkException {
		return get(SafeURL.ofSafe(request));
	}

	/**
	 * @apiNote cosmetica api will include the safe url in an {@link HttpNotOkException}.
	 */
	public static Response get(SafeURL request) throws ParseException, IOException, HttpNotOkException {
		Response result = _get(request.url());
		if (result.getError().isPresent()) throw new HttpNotOkException(request.safeUrl(), result.getError().getAsInt());
		return result;
	}

	private static Response _get(String request) throws ParseException, IOException {
		final int timeout = 15 * 1000;

		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectionRequestTimeout(timeout)
				.setConnectTimeout(timeout)
				.setSocketTimeout(timeout)
				.build();

		CloseableHttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(requestConfig)
				.build();

		final HttpGet get = new HttpGet(request);

		CloseableHttpResponse response = client.execute(get);
		return new Response(client, response);
	}

	public static PostBuilder post(SafeURL request) {
		return new PostBuilder(request);
	}

	public static PostBuilder post(String request) {
		return new PostBuilder(SafeURL.ofSafe(request));
	}

	public static class PostBuilder {
		private PostBuilder(SafeURL url) {
			this.url = url;
		}

		private final SafeURL url;
		private final StringBuilder entity = new StringBuilder("{");
		private boolean started = false;

		public PostBuilder set(String key, String value) {
			if (this.started) {
				this.entity.append(',');
			}
			else {
				this.started = true;
			}

			this.entity.append('"').append(key).append("\": \"").append(value).append('"');
			return this;
		}

		public Response submit() throws ParseException, IOException {
			final int timeout = 15 * 1000;

			RequestConfig requestConfig = RequestConfig.custom()
					.setConnectionRequestTimeout(timeout)
					.setConnectTimeout(timeout)
					.setSocketTimeout(timeout)
					.build();

			CloseableHttpClient client = HttpClients.custom()
					.setDefaultRequestConfig(requestConfig)
					.build();

			final HttpPost post = new HttpPost(this.url.url());
			post.setHeader("Accept", "application/json");
			post.setHeader("Content-type", "application/json");
			post.setEntity(new StringEntity(this.entity.append('}').toString()));

			CloseableHttpResponse response = client.execute(post);

			// validate
			Response r = new Response(client, response);
			if (r.getError().isPresent()) throw new HttpNotOkException(this.url.safeUrl(), r.getError().getAsInt());
			return r;
		}
	}
}