package top.nulldns.subdns.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.nulldns.subdns.dto.PDNSDto;
import top.nulldns.subdns.service.PDNSService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PDNSController {
    private final PDNSService pdnsService;

    @GetMapping("/api/get/zones")
    public ResponseEntity<List<PDNSDto.ZoneName>> listZoneName() {
        return ResponseEntity.ok(pdnsService.getZoneNameList());
    }

    @GetMapping("/api/get/search")
    public ResponseEntity<List<PDNSDto.SearchResult>> listSearchResult(String domain) {
        return ResponseEntity.ok(pdnsService.searchResultList(domain));
    }
}
