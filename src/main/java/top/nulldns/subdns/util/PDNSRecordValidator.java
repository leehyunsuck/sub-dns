package top.nulldns.subdns.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class PDNSRecordValidator {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?!-)(?!.*--)[A-Za-z0-9-]{1,63}(?<!-)(\\.[A-Za-z0-9-]{1,63})*\\.?$"
    );
    private static final Pattern LABEL_PATTERN = Pattern.compile(
            "^(?!-)(?!.*--)[A-Za-z0-9-]{1,63}(?<!-)$"
    );
    private static final Set<String> VALID_TYPES = Set.of(
            "A", "AAAA", "CNAME", "TXT"
            //"MX, "NS", "SOA", "PTR", "SRV", "CAA"
    );
    private static final Set<String> EXACT_BLOCK_WORDS = Set.of(
            "www", "api", "dns", "ns", "ns1", "ns2", "ns3", "ns4",
            "mx", "mail", "email", "smtp", "imap", "pop", "ftp", "sftp", "ssh",
            "dev", "stg", "prod", "test", "demo",
            "db", "sql", "server", "client", "cloud", "network",
            "auth", "login", "signin", "signup", "register", "password",
            "dashboard", "config", "manage", "billing", "payment", "secure",
            "wpad", "autodiscover", "isatap", "local", "localhost",
            "noreply", "abuse", "support", "helpdesk", "ssl", "cert", ".well-known",
            "google", "naver", "kakao", "aws", "azure", "apple", "microsoft", "help"
    );

    private static final Set<String> CONTAINS_BLOCK_WORDS = Set.of(
            "admin", "administrator", "root", "system", "sysadmin",
            "master", "webmaster", "hostmaster", "postmaster",
            "nulldns", "subdns"
    );

    public static boolean isValidType(String type) {
        if (type == null || type.isEmpty()) return false;

        return VALID_TYPES.contains(type);
    }

    public static boolean isValidLabel(String label) {
        if (label == null || label.isEmpty()) return false;

        label = label.toLowerCase();
        if (label.length() > 63) return false;
        if (label.length() < 4) return false;
        if (EXACT_BLOCK_WORDS.contains(label)) return false;

        for (String banWord : CONTAINS_BLOCK_WORDS) {
            if (label.contains(banWord)) return false;
        }

        return LABEL_PATTERN.matcher(label).matches();
    }


    public static boolean validate(String type, String content) {
        if (!isValidType(type)) return false;
        if (content == null || content.isEmpty()) return false;

        switch (type) {
            case "A":
                return isIPv4(content);
            case "AAAA":
                return isIPv6(content);
            case "CNAME":
                return isValidDomainName(content);
            case "TXT":
                return isValidTxt(content);
            default:
                return false;
        }
    }

    public static boolean isIPv4(String ip) {
        return IPV4_PATTERN.matcher(ip).matches();
    }

    public static boolean isIPv6(String ip) {
        try {
            if (!ip.contains(":")) return false;

            return InetAddress.getByName(ip) instanceof Inet6Address;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidDomainName(String domain) {
        if (domain.length() > 253) return false;

        return DOMAIN_PATTERN.matcher(domain).matches();
    }

    public static boolean isValidTxt(String txt) {
        return txt.length() <= 255;
    }

}
