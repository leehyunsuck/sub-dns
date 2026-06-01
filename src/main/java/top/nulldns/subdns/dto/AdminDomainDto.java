package top.nulldns.subdns.dto;

import top.nulldns.subdns.config.finalconfig.Status;

import java.time.LocalDate;

public record AdminDomainDto(
    Long id,
    Long memberId,
    String providerId,
    String fullDomain,
    String recordType,
    String content,
    LocalDate expiryDate,
    Status domainStatus
) {
}
