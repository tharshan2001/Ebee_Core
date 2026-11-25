package com.ebee.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Resolves the real client IP behind our reverse proxy (nginx).
 *
 * <p>Forwarding headers are only trusted when the direct peer is itself a proxy
 * on a loopback or private network. Otherwise anyone could send their own
 * {@code X-Forwarded-For} and pick the IP used for rate limiting, the IP
 * whitelist and login audit records.
 *
 * <p>Order of preference when the peer is trusted:
 * <ol>
 *   <li>{@code X-Real-IP} — nginx overwrites it with {@code $remote_addr}.</li>
 *   <li>{@code X-Forwarded-For}, read right to left, skipping trusted proxy hops.
 *       nginx appends the real peer to the end, so the leftmost entries are
 *       client-controlled and must never be used.</li>
 *   <li>The socket address.</li>
 * </ol>
 */
public final class RequestUtils {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f.]*:[0-9A-Fa-f:.]*$");

    private RequestUtils() {}

    public static String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        if (realIp != null && parseLiteral(realIp) != null) {
            return realIp;
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (parseLiteral(hop) == null) {
                    // Malformed entry: stop rather than trust anything further left.
                    break;
                }
                if (!isTrustedProxy(hop)) {
                    return hop;
                }
            }
        }

        return remoteAddr;
    }

    /** Loopback, private (RFC 1918), link-local, and IPv6 unique-local addresses. */
    static boolean isTrustedProxy(String ip) {
        InetAddress addr = parseLiteral(ip);
        if (addr == null) {
            return false;
        }
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
            return true;
        }
        // IPv6 unique local addresses fc00::/7 (Docker IPv6 networks)
        return addr instanceof Inet6Address && (addr.getAddress()[0] & 0xFE) == 0xFC;
    }

    /**
     * Parses an IP literal without ever doing a DNS lookup. IPv4 is parsed by hand;
     * IPv6 strings always contain ':' which makes the JDK treat them strictly as literals.
     */
    private static InetAddress parseLiteral(String ip) {
        try {
            if (ip == null) {
                return null;
            }
            if (IPV4.matcher(ip).matches()) {
                String[] parts = ip.split("\\.");
                byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) {
                    int octet = Integer.parseInt(parts[i]);
                    if (octet > 255) {
                        return null;
                    }
                    bytes[i] = (byte) octet;
                }
                return InetAddress.getByAddress(bytes);
            }
            if (IPV6.matcher(ip).matches()) {
                return InetAddress.getByName(ip);
            }
            return null;
        } catch (UnknownHostException | NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
