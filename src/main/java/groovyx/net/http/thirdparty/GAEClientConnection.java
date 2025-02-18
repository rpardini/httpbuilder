/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2010 Martin Blom <martin@blom.org>

     This program is free software: you can redistribute it and/or
     modify it under the terms of the GNU Lesser General Public License
     as published by the Free Software Foundation, either version 3
     of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU Lesser General Public License for more details.

     You should have received a copy of the GNU Lesser General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.


     PLEASE NOTE THAT THIS FILE'S LICENSE IS DIFFERENT FROM THE REST OF ESXX!
*/

package groovyx.net.http.thirdparty;

import com.google.appengine.api.urlfetch.*;
import org.apache.http.*;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

class GAEClientConnection
        implements ManagedClientConnection {

    private static final URLFetchService urlFS = URLFetchServiceFactory.getURLFetchService();

    // From interface ManagedClientConnection
    private final ClientConnectionManager connManager;
    private HttpRoute route;
    private Object state;
    private boolean reusable;
    private HTTPRequest request;
    private HTTPResponse response;
    private boolean closed;

    public GAEClientConnection(ClientConnectionManager cm, HttpRoute route, Object state) {
        this.connManager = cm;
        this.route = route;
        this.state = state;
        this.closed = true;
    }

    public boolean isSecure() {
        return route.isSecure();
    }

    public HttpRoute getRoute() {
        return route;
    }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public void bind(Socket socket) throws IOException {

    }

    @Override
    public Socket getSocket() {
        return null;
    }

    public javax.net.ssl.SSLSession getSSLSession() {
        return null;
    }

    public void open(HttpRoute route, HttpContext context, HttpParams params)
            throws IOException {
        close();
        this.route = route;
//     System.err.println(">>>>");
    }

    public void tunnelTarget(boolean secure, HttpParams params)
            throws IOException {
        throw new IOException("tunnelTarget() not supported");
    }


    // From interface HttpClientConnection

    public void tunnelProxy(HttpHost next, boolean secure, HttpParams params)
            throws IOException {
        throw new IOException("tunnelProxy() not supported");
    }

    public void layerProtocol(HttpContext context, HttpParams params)
            throws IOException {
        throw new IOException("layerProtocol() not supported");
    }

    public void markReusable() {
        reusable = true;
    }

    public void unmarkReusable() {
        reusable = false;
    }

    public boolean isMarkedReusable() {
        return reusable;
    }

    public Object getState() {
        return state;
    }


    // From interface HttpConnection

    public void setState(Object state) {
        this.state = state;
    }

    public void setIdleDuration(long duration, TimeUnit unit) {
        // Do nothing
    }

    public boolean isResponseAvailable(int timeout)
            throws IOException {
        return response != null;
    }

    public void sendRequestHeader(HttpRequest request)
            throws HttpException, IOException {
        try {
            HttpHost host = route.getTargetHost();

            URI uri = new URI(host.getSchemeName()
                    + "://"
                    + host.getHostName()
                    + ((host.getPort() == -1) ? "" : (":" + host.getPort()))
                    + request.getRequestLine().getUri());

            this.request = new HTTPRequest(uri.toURL(),
                    HTTPMethod.valueOf(request.getRequestLine().getMethod()),
                    FetchOptions.Builder.disallowTruncate().doNotFollowRedirects());
        } catch (URISyntaxException ex) {
            throw new IOException("Malformed request URI: " + ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Unsupported HTTP method: " + ex.getMessage(), ex);
        }

//     System.err.println("SEND: " + this.request.getMethod() + " " + this.request.getURL());

        for (Header h : request.getAllHeaders()) {
//       System.err.println("SEND: " + h.getName() + ": " + h.getValue());
            this.request.addHeader(new HTTPHeader(h.getName(), h.getValue()));
        }
    }

    public void sendRequestEntity(HttpEntityEnclosingRequest request)
            throws HttpException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (request.getEntity() != null) {
            request.getEntity().writeTo(baos);
        }
        this.request.setPayload(baos.toByteArray());
    }

    public HttpResponse receiveResponseHeader()
            throws HttpException, IOException {
        if (this.response == null) {
            flush();
        }

        HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1),
                this.response.getResponseCode(),
                null);
//     System.err.println("RECV: " + response.getStatusLine());

        for (HTTPHeader h : this.response.getHeaders()) {
//       System.err.println("RECV: " + h.getName() + ": " + h.getValue());
            response.addHeader(h.getName(), h.getValue());
        }

        return response;
    }

    public void receiveResponseEntity(HttpResponse response)
            throws HttpException, IOException {
        if (this.response == null) {
            throw new IOException("receiveResponseEntity() called on closed connection");
        }

        ByteArrayEntity bae = new ByteArrayEntity(this.response.getContent());
        bae.setContentType(response.getFirstHeader("Content-Type"));
        response.setEntity(bae);

        response = null;
    }


    // From interface HttpInetConnection

    public void flush()
            throws IOException {
        if (request != null) {
            try {
//  System.err.println("----");
                response = urlFS.fetch(request);
                request = null;
            } catch (IOException ex) {
                ex.printStackTrace();
                throw ex;
            }
        } else {
            response = null;
        }
    }

    public void close()
            throws IOException {
        request = null;
        response = null;
        closed = true;
//     System.err.println("<<<<");
    }

    public boolean isOpen() {
        return request != null || response != null;
    }

    public boolean isStale() {
        return !isOpen() && !closed;
    }


    // From interface ConnectionReleaseTrigger

    public int getSocketTimeout() {
        return -1;
    }

    public void setSocketTimeout(int timeout) {
    }

    public void shutdown()
            throws IOException {
        close();
    }

    public HttpConnectionMetrics getMetrics() {
        return null;
    }

    public InetAddress getLocalAddress() {
        return null;
    }

    public int getLocalPort() {
        return 0;
    }

    public InetAddress getRemoteAddress() {
        return null;
    }

    public int getRemotePort() {
        HttpHost host = route.getTargetHost();
        return connManager.getSchemeRegistry().getScheme(host).resolvePort(host.getPort());
    }

    public void releaseConnection()
            throws IOException {
        connManager.releaseConnection(this, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    public void abortConnection()
            throws IOException {
        unmarkReusable();
        shutdown();
    }
}
