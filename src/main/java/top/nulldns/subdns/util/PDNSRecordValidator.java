package top.nulldns.subdns.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Set;
import java.util.regex.Pattern;

public class PDNSRecordValidator {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(  // CNAME용
            "^(?!-)(?!.*--)[A-Za-z0-9-]{1,63}(?<!-)(\\.[A-Za-z0-9-]{1,63})*\\.?$"
    );
    private static final Pattern LABEL_PATTERN = Pattern.compile(
            "^(?!-)(?!.*--)[A-Za-z0-9-]{4,63}(?<!-)$"
    );
    private static final Pattern LABEL_PATTERN_ADMIN = Pattern.compile(
            "^[A-Za-z0-9-]{1,63}$"
    );
    private static final Set<String> VALID_TYPES = Set.of(
            "A", "AAAA", "CNAME", "TXT"
            //"MX, "NS", "SOA", "PTR", "SRV", "CAA"
    );
    private static final Set<String> EXACT_BLOCK_WORDS = Set.of(
            // 기본 서비스
            "www", "api", "dns", "ns", "ns1", "ns2", "ns3", "ns4",
            "mx", "mail", "email", "smtp", "imap", "pop", "ftp", "sftp", "ssh",
            "dev", "stg", "prod", "test", "demo",

            // 보안 및 인증 (중요)
            "_acme-challenge", "_domainconnect", "ssl", "cert", ".well-known",
            "auth", "login", "signin", "signup", "register", "password", "security", "verify",

            // 피싱 방지
            "account", "bank", "wallet", "pay", "payment", "billing", "token", "secure",
            "official", "support", "help", "helpdesk", "abuse", "noreply",

            // 인프라 및 네트워크
            "db", "sql", "server", "client", "cloud", "network", "vpn", "internal",
            "gateway", "proxy", "monitor", "status", "update", "cdn", "static", "media",
            "wpad", "autodiscover", "isatap", "local", "localhost", "host",

            // 플랫폼 및 기업
            "dashboard", "config", "manage", "m", "mobile", "app", "site",
            "google", "naver", "kakao", "aws", "azure", "apple", "microsoft",
            "legal", "terms", "privacy", "policy", "jobs", "contact",
            "service", "public", "private"
    );
    private static final Set<String> CONTAINS_BLOCK_WORDS = Set.of(
            "admin", "administrator", "root", "system", "sysadmin",
            "master", "webmaster", "hostmaster", "postmaster",
            "nulldns", "subdns", "official"
    );
    private static final Set<String> BLOCKED_CNAME_TARGETS = Set.of(
            "googlehosted.com", "verify.microsoft.com", "github.io", "gitlab.io",
            "awsapps.com", "acm-validations.aws", "pages.dev", "vercel-dns.com",
            "netlify.app", "herokuapp.com", "amazonses.com", "sendgrid.net",
            "mailgun.org", "firebaseapp.com", "supabase.co", "atlassian.net"
    );
    private static final Set<String> BLOCKED_TXT_VALUES = Set.of(
            "google-site-verification", "ms=", "facebook-domain-verification",
            "v=spf1", "v=dmarc1", "naver-site-verification", "apple-domain-verification"
    );

    public static boolean isValidType(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        type = type.toUpperCase();

        return VALID_TYPES.contains(type);
    }

    public static boolean isValidLabelAdmin(String label) {
        if (label == null || label.isEmpty()) return false;

        return LABEL_PATTERN_ADMIN.matcher(label).matches();
    }

    public static boolean isValidLabel(String label) {
        if (label == null || label.isEmpty()) return false;

        if (EXACT_BLOCK_WORDS.contains(label)) return false;
        for (String banWord : CONTAINS_BLOCK_WORDS) {
            if (label.contains(banWord)) return false;
        }

        return LABEL_PATTERN.matcher(label).matches();
    }


    public static boolean validate(String type, String content, String zone, boolean isAdmin) {
        if (!isValidType(type)) return false;

        switch (type) {
            case "A":
                return isIPv4(content);
            case "AAAA":
                return isIPv6(content);
            case "CNAME":
                if (isAdmin) {
                    return isValidCNAME(content, zone);
                }
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

    public static boolean isValidCNAME(String content, String zone) {
        return content.endsWith("." + zone + ".");
    }

    public static boolean isValidDomainName(String content) {
        if (content.length() > 253) return false;

        for (String domain : BLOCKED_CNAME_TARGETS) {
            if (content.contains(domain)) return false;
        }

        return DOMAIN_PATTERN.matcher(content).matches();
    }

    public static boolean isValidTxt(String txt) {
        for (String block : BLOCKED_TXT_VALUES) {
            if (txt.contains(block)) return false;
        }

        return txt.length() <= 255;
    }

}
