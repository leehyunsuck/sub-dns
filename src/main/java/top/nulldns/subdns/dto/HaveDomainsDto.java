package top.nulldns.subdns.dto;

import java.time.LocalDate;

public record HaveDomainsDto(
        String subDomain,
        String zone,
        LocalDate expirationDate
) { }
