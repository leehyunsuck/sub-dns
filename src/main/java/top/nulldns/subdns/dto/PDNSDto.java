package top.nulldns.subdns.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class PDNSDto {
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResult {
        private String  name,       // 도메인 이름
                        type,       // 레코드 타입
                        content;    // 레코드 타입에 맞는 내용
    }

    @Data
    @Builder
    public static class Rrset {
        private String  name,
                        type;

        @Builder.Default
        private Integer ttl = 3600;

        @JsonProperty("changetype")
        private String changeType;

        private List<Record> records;
    }

    @Data
    @Builder
    public static class Record {
        private String content;

        @Builder.Default
        private boolean disabled = false;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZoneName {
        private String name;
    }

    @Data
    @Builder
    public static class CanAddSubDomainZones {
        private String subDomain;
        private List<ZoneAddCapability> zones;
    }

    @Data
    @Builder
    public static class ZoneAddCapability {
        private String name;
        private boolean canAdd;
    }
}
