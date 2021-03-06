package com.firefly.server.http2.servlet;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import com.firefly.codec.http2.model.CookieGenerator;
import com.firefly.codec.http2.model.HttpHeader;
import com.firefly.codec.http2.model.MetaData.Response;
import com.firefly.codec.http2.stream.HTTPOutputStream;
import com.firefly.mvc.web.servlet.SystemHtmlPage;
import com.firefly.server.exception.HttpServerException;
import com.firefly.utils.VerifyUtils;
import com.firefly.utils.log.Log;
import com.firefly.utils.log.LogFactory;

public class HTTPServletResponseImpl implements HttpServletResponse {

	private static Log log = LogFactory.getInstance().getLog("firefly-system");

	private final Response response;
	private final HTTPOutputStream output;
	private final HTTPServletRequestImpl request;

	private String characterEncoding;
	private Locale locale;
	private int bufferSize;
	private ServletOutputStream servletOutputStream;
	private PrintWriter printWriter;

	public HTTPServletResponseImpl(Response response, HTTPOutputStream output, HTTPServletRequestImpl request) {
		this.response = response;
		this.output = output;
		this.request = request;
		characterEncoding = request.http2Configuration.getCharacterEncoding();
		bufferSize = request.http2Configuration.getServletResponseBufferSize();
		response.setStatus(SC_OK);
	}

	@Override
	public void setStatus(int status) {
		response.setStatus(status);
	}

	@Override
	public void setStatus(int status, String reason) {
		response.setStatus(status);
		response.setReason(reason);
	}

	@Override
	public int getStatus() {
		return response.getStatus();
	}

	@Override
	public void sendError(int status, String reason) throws IOException {
		setStatus(status, reason);
		try (PrintWriter writer = getWriter()) {
			writer.print(SystemHtmlPage.systemPageTemplate(status, ""));
		}
	}

	@Override
	public void sendError(int status) throws IOException {
		setStatus(status);
		try (PrintWriter writer = getWriter()) {
			writer.print(SystemHtmlPage.systemPageTemplate(status, ""));
		}
	}

	@Override
	public void sendRedirect(String location) throws IOException {
		String absolute = toAbsolute(location);
		response.setStatus(SC_FOUND);
		response.getFields().put(HttpHeader.LOCATION, absolute);
		response.getFields().put(HttpHeader.CONTENT_LENGTH, "0");
		output.close();
	}

	private String toAbsolute(String location) {
		if (location.startsWith("http"))
			return location;

		StringBuilder sb = new StringBuilder();
		sb.append(request.getScheme()).append("://").append(request.getServerName()).append(":")
				.append(request.getServerPort());

		if (location.charAt(0) == '/') {
			sb.append(location);
		} else {
			String URI = request.getRequestURI();
			int last = 0;
			for (int i = URI.length() - 1; i >= 0; i--) {
				if (URI.charAt(i) == '/') {
					last = i + 1;
					break;
				}
			}
			sb.append(URI.substring(0, last)).append(location);
		}
		return sb.toString();
	}

	@Override
	public void setCharacterEncoding(String characterEncoding) {
		this.characterEncoding = characterEncoding;
	}

	@Override
	public String getCharacterEncoding() {
		return characterEncoding;
	}

	@Override
	public void setContentType(String contentType) {
		response.getFields().put(HttpHeader.CONTENT_TYPE, contentType);
	}

	@Override
	public String getContentType() {
		return response.getFields().get(HttpHeader.CONTENT_TYPE);
	}

	@Override
	public void setContentLength(int len) {
		response.getFields().putLongField(HttpHeader.CONTENT_LENGTH, len);
	}

	@Override
	public void setContentLengthLong(long len) {
		response.getFields().putLongField(HttpHeader.CONTENT_LENGTH, len);
	}

	@Override
	public void setLocale(Locale locale) {
		this.locale = locale;
		if (locale != null) {
			String contentLanguage = locale.getLanguage();
			if (VerifyUtils.isNotEmpty(contentLanguage)) {
				String country = locale.getCountry();
				if (VerifyUtils.isNotEmpty(country)) {
					contentLanguage += "-" + country;
				}
				response.getFields().put(HttpHeader.CONTENT_LANGUAGE, contentLanguage);
			}
		}
	}

	@Override
	public Locale getLocale() {
		return locale;
	}

	@Override
	public boolean containsHeader(String name) {
		return response.getFields().containsKey(name);
	}

	@Override
	public void setDateHeader(String name, long date) {
		response.getFields().putDateField(name, date);
	}

	@Override
	public void addDateHeader(String name, long date) {
		response.getFields().addDateField(name, date);
	}

	@Override
	public void setHeader(String name, String value) {
		response.getFields().put(name, value);
	}

	@Override
	public void addHeader(String name, String value) {
		response.getFields().add(name, value);
	}

	@Override
	public void setIntHeader(String name, int value) {
		response.getFields().putLongField(name, value);
	}

	@Override
	public void addIntHeader(String name, int value) {
		response.getFields().add(name, Integer.toString(value));
	}

	@Override
	public String getHeader(String name) {
		return response.getFields().get(name);
	}

	@Override
	public Collection<String> getHeaders(String name) {
		return enumerationToCollection(response.getFields().getValues(name));
	}

	@Override
	public Collection<String> getHeaderNames() {
		return enumerationToCollection(response.getFields().getFieldNames());
	}

	private <T> Collection<T> enumerationToCollection(Enumeration<T> enumeration) {
		if (enumeration == null) {
			return null;
		} else {
			List<T> list = new LinkedList<>();
			while (enumeration.hasMoreElements()) {
				list.add(enumeration.nextElement());
			}
			return list;
		}
	}

	@Override
	public void addCookie(Cookie cookie) {
		response.getFields().add(HttpHeader.SET_COOKIE, CookieGenerator.generateServletSetCookie(cookie));
	}

	@Override
	public String encodeURL(String url) {
		if (VerifyUtils.isEmpty(url))
			return null;

		if (url.contains(";" + request.http2Configuration.getSessionIdName() + "="))
			return url;

		String absoluteURL = toAbsolute(url);
		String requestedSessionId = request.getRequestedSessionId();
		if (VerifyUtils.isNotEmpty(requestedSessionId)) {
			return toEncoded(absoluteURL, requestedSessionId, request.http2Configuration.getSessionIdName());
		} else {
			return null;
		}
	}

	@Override
	public String encodeUrl(String url) {
		return encodeURL(url);
	}

	@Override
	public String encodeRedirectURL(String url) {
		return encodeURL(url);
	}

	@Override
	public String encodeRedirectUrl(String url) {
		return encodeRedirectURL(url);
	}

	private String toEncoded(String url, String sessionId, String sessionIdName) {
		if (url == null || sessionId == null)
			return url;

		String path = url;
		String query = "";
		String anchor = "";
		int question = url.indexOf('?');
		if (question >= 0) {
			path = url.substring(0, question);
			query = url.substring(question);
		}
		int pound = path.indexOf('#');
		if (pound >= 0) {
			anchor = path.substring(pound);
			path = path.substring(0, pound);
		}
		StringBuilder sb = new StringBuilder(path);
		if (sb.length() > 0) { // jsessionid can't be first.
			sb.append(";");
			sb.append(sessionIdName);
			sb.append("=");
			sb.append(sessionId);
		}
		sb.append(anchor);
		sb.append(query);
		return sb.toString();
	}

	private class HTTPServletOutputStream extends ServletOutputStream {

		private byte[] buf = new byte[bufferSize];
		private int count;

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setWriteListener(WriteListener writeListener) {
			try {
				writeListener.onWritePossible();
			} catch (IOException e) {
				writeListener.onError(e);
			}
		}

		@Override
		public synchronized void write(int b) throws IOException {
			if (count >= buf.length) {
				flush();
			}
			buf[count++] = (byte) b;
		}

		@Override
		public synchronized void write(byte[] array, int offset, int length) throws IOException {
			if (array == null || array.length == 0 || length <= 0) {
				return;
			}

			if (offset < 0) {
				throw new IllegalArgumentException("the offset is less than 0");
			}

			if (length >= buf.length) {
				flush();
				output.write(array, offset, length);
				return;
			}
			if (length > buf.length - count) {
				flush();
			}
			System.arraycopy(array, offset, buf, count, length);
			count += length;
		}

		@Override
		public synchronized void print(String s) throws IOException {
			if (VerifyUtils.isEmpty(s))
				s = "null";

			write(stringToByte(s));
		}

		@Override
		public synchronized void flush() throws IOException {
			if (count > 0) {
				output.write(buf, 0, count);
				count = 0;
				buf = new byte[bufferSize];
			}
		}

		@Override
		public synchronized void close() throws IOException {
			flush();
			output.close();
		}

	}

	public byte[] stringToByte(String str) {
		byte[] ret = null;
		try {
			ret = str.getBytes(characterEncoding);
		} catch (UnsupportedEncodingException e) {
			log.error("string to bytes", e);
		}
		return ret;
	}

	@Override
	public boolean isCommitted() {
		return output.isCommited();
	}

	@Override
	public void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}

	@Override
	public int getBufferSize() {
		return bufferSize;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		if (printWriter != null) {
			throw new IOException("the response has used PrintWriter");
		}

		if (servletOutputStream == null) {
			servletOutputStream = new HTTPServletOutputStream();
			return servletOutputStream;
		} else {
			return servletOutputStream;
		}
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		if (servletOutputStream != null) {
			throw new IOException("the response has used ServletOutputStream");
		}

		if (printWriter == null) {
			printWriter = new PrintWriter(new OutputStreamWriter(new HTTPServletOutputStream(), characterEncoding));
			return printWriter;
		} else {
			return printWriter;
		}
	}

	@Override
	public void flushBuffer() throws IOException {
		if (printWriter != null) {
			printWriter.flush();
			return;
		}

		if (servletOutputStream != null) {
			servletOutputStream.flush();
			return;
		}
	}

	@Override
	public void resetBuffer() {
		throw new HttpServerException("not implement this method!");
	}

	@Override
	public void reset() {
		throw new HttpServerException("not implement this method!");
	}

}
