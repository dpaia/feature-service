package com.sivalabs.ft.features.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.List;

/**
 * Cache configuration class.
 * This class configures a simple in-memory cache manager
 * that uses ConcurrentHashMap for storing cache entries.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        List<Cache> caches = Arrays.asList(
            new ConcurrentMapCache("featureById"),
            new ConcurrentMapCache("featuresByStatus"),
            new ConcurrentMapCache("featuresByAssignee")
        );
        cacheManager.setCaches(caches);
        return cacheManager;
    }
}
