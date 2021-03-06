package test.net.ssl;

import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.alpn.ALPN;

import com.firefly.net.Client;
import com.firefly.net.Handler;
import com.firefly.net.SSLContextFactory;
import com.firefly.net.SSLEventHandler;
import com.firefly.net.Session;
import com.firefly.net.tcp.aio.AsynchronousTcpClient;
import com.firefly.net.tcp.ssl.DefaultCredentialSSLContextFactory;
import com.firefly.net.tcp.ssl.SSLSession;

public class SSLClientDemo {
	
	private static SSLContextFactory sslContextFactory = new DefaultCredentialSSLContextFactory();

	public static void main(String[] args) throws Throwable {
		Client client = new AsynchronousTcpClient(
		new SSLDecoder(), 
		new SSLEncoder(),
		new Handler(){
			private SSLContext sslContext = sslContextFactory.getSSLContext();
			
			@Override
			public void sessionOpened(final Session session) throws Throwable {
				long start = System.currentTimeMillis();
				final SSLEngine sslEngine = sslContext.createSSLEngine();
				System.out.println("client creates ssl engine elapsed time is " + (System.currentTimeMillis() - start));
				System.out.println(sslContext.getProtocol());
				SessionInfo info = new SessionInfo();
				info.sslSession = new SSLSession(sslContext, sslEngine, session, true, 
				new SSLEventHandler(){

					@Override
					public void handshakeFinished(SSLSession sslSession) {
						System.out.println("handshake finished!");
						session.encode("hello world SSL client!");
					}},
				new ALPN.ClientProvider(){

					@Override
					public List<String> protocols() {
						System.out.println("protocols spdy, http");
						return Arrays.asList("spdy/3", "http/1.1");
					}

					@Override
					public void unsupported() {
						System.out.println("client unsupported");
						ALPN.remove(sslEngine);
					}

					@Override
					public void selected(String protocol) {
						System.out.println("server selected the protocol: " + protocol);
						ALPN.remove(sslEngine);
						
					}});
				session.attachObject(info);
			}

			@Override
			public void sessionClosed(Session session) throws Throwable {
				System.out.println("client session closed " + session.getSessionId());
				SessionInfo sessionAttachment = (SessionInfo)session.getAttachment();
				if(sslContext != null && sessionAttachment.sslSession != null) {
					sessionAttachment.sslSession.close();
				}
			}

			@Override
			public void messageRecieved(Session session, Object message) throws Throwable {
				System.out.println("client receives message: " + message);
			}

			@Override
			public void exceptionCaught(Session session, Throwable t)
					throws Throwable {
				t.printStackTrace();
				if(session.isOpen())
					session.closeNow();
			}}, 1000 * 5);
		
		for (int i = 0; i < 10; i++) {
			int sessionId = client.connect("localhost", 7676);
			System.out.println("client session id: " + sessionId);
		}
		
	}

}
