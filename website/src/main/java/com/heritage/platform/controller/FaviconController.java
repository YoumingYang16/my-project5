package com.heritage.platform.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class FaviconController {

    @GetMapping("/favicon.ico")
    public ResponseEntity<byte[]> favicon() {
        try {
            Resource resource = new ClassPathResource("static/favicon.ico");
            if (resource.exists()) {
                byte[] data = resource.getInputStream().readAllBytes();
                HttpHeaders headers = new HttpHeaders();
                headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS));
                return new ResponseEntity<>(data, headers, HttpStatus.OK);
            }
        } catch (Exception e) {
            // Ignore any error - just return empty favicon response
        }
        
        // If no favicon found, return empty 204 response
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
