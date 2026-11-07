package com.hermes.webui;
import org.junit.Test;
import static org.junit.Assert.*;
public class ServerUrlTest {
    @Test public void setupPreservesProxyPath() {
        assertEquals("https://example.com/hermes/",ServerUrl.normalize(" HTTPS://Example.COM/hermes "));
    }
    @Test public void rejectsUntrustedSetup() {
        for (String value : new String[]{"http://example.com", "https://user:pass@example.com", "https://example.com/?token=x", "https://example.com/#x", "file:///etc/passwd", "https://example.com:99999", "https://example.com:0", "", "javascript:alert(1)"}) {
            try { ServerUrl.normalize(value); fail(value); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void permissionsAreOriginScoped() {
        String base = "https://hermes.example.com/proxy/";
        assertTrue(ServerUrl.sameOrigin(base,"https://HERMES.example.com:443/api/chat"));
        for (String value : new String[]{"https://hermes.example.com.evil.com", "http://hermes.example.com", "https://hermes.example.com:8443", "https://evil.com", "https://user@hermes.example.com", "data:text/html,hello", "broken"})
            assertFalse(value,ServerUrl.sameOrigin(base,value));
    }
}
