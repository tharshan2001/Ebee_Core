package com.ebee.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RequestUtilsTest {

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    @Test
    void behindNginx_spoofedLeftmostXff_isIgnored() {
        // Client sends "X-Forwarded-For: 1.1.1.1"; nginx appends the real peer 203.0.113.7.
        MockHttpServletRequest req = request("172.18.0.5");
        req.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.7");

        assertThat(RequestUtils.getClientIp(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void behindNginx_xRealIp_isPreferred() {
        MockHttpServletRequest req = request("172.18.0.5");
        req.addHeader("X-Real-IP", "203.0.113.7");
        req.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.7");

        assertThat(RequestUtils.getClientIp(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void behindNginx_skipsTrustedHopsFromTheRight() {
        MockHttpServletRequest req = request("127.0.0.1");
        req.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.7, 10.0.0.2");

        assertThat(RequestUtils.getClientIp(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void directPublicClient_headersAreIgnored() {
        MockHttpServletRequest req = request("203.0.113.50");
        req.addHeader("X-Forwarded-For", "1.1.1.1");
        req.addHeader("X-Real-IP", "1.1.1.1");

        assertThat(RequestUtils.getClientIp(req)).isEqualTo("203.0.113.50");
    }

    @Test
    void trustedPeer_noHeaders_fallsBackToSocket() {
        assertThat(RequestUtils.getClientIp(request("10.0.0.3"))).isEqualTo("10.0.0.3");
    }

    @Test
    void malformedHeaders_neverUsed() {
        MockHttpServletRequest req = request("172.18.0.5");
        req.addHeader("X-Real-IP", "evil.example.com");
        req.addHeader("X-Forwarded-For", "203.0.113.7, not-an-ip");

        // Malformed rightmost XFF hop stops the walk; fall back to the proxy address.
        assertThat(RequestUtils.getClientIp(req)).isEqualTo("172.18.0.5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "::1", "fd00::1"})
    void trustedProxyRanges(String ip) {
        assertThat(RequestUtils.isTrustedProxy(ip)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "172.32.0.1", "203.0.113.7", "2001:db8::1", "999.1.1.1", "localhost", ""})
    void untrustedOrInvalid(String ip) {
        assertThat(RequestUtils.isTrustedProxy(ip)).isFalse();
    }
}
