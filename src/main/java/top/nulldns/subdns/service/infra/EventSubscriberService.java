package top.nulldns.subdns.service.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.service.domain.CheckAdminService;
import top.nulldns.subdns.service.facade.ExpirationDomainDateService;
import top.nulldns.subdns.service.facade.PDNSService;
import top.nulldns.subdns.config.finalconfig.Event;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventSubscriberService {
    private final CheckAdminService checkAdminService;
    private final ExpirationDomainDateService expirationDomainDateService;
    private final PDNSService pdnsService;

    public void onMessage(String message) {
        if (message.equals(Event.REFRESH_ADMINS.name())) {
            checkAdminService.refreshAdmins(false);
        } else if (message.equals(Event.REFRESH_ZONES.name())) {
            pdnsService.refreshZones(false);
        } else if (message.equals(Event.REFRESH_DOMAIN_MAX_DATES.name())) {
            expirationDomainDateService.refreshMaxDomainDates(false);
        } else {
            log.error("알 수 없는 이벤트 발생: {}", message);
        }
    }
}
