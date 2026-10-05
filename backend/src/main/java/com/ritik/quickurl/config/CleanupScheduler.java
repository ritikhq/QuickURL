package com.ritik.quickurl.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ritik.quickurl.services.UrlShortenerService;

@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupScheduler {
    private final UrlShortenerService urlShortenerService;

    @Scheduled(fixedRateString = "#{${quickurl.cleanup.interval-minutes} * 60 * 1000}")
    public void cleanupExpiredUrls(){
        try{
            log.debug("Running scheduled cleanup of expired URLs");
            urlShortenerService.cleanupExpiredUrls();
        }catch (Exception e){
            log.error("Error during scheduled cleanup",e);
        }
    }
}
