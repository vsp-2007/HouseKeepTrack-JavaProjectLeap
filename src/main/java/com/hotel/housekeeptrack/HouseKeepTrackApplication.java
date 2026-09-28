package com.hotel.housekeeptrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main entry point for HouseKeepTrack application.
 */
@SpringBootApplication
@EnableCaching
public class HouseKeepTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(HouseKeepTrackApplication.class, args);
    }
}
