package com.github.dynamic.threadpool.config.service;

import com.github.dynamic.threadpool.config.service.biz.ConfigService;
import com.github.dynamic.threadpool.common.config.ApplicationContextHolder;
import com.github.dynamic.threadpool.common.constant.Constants;
import com.github.dynamic.threadpool.common.toolkit.Md5Util;
import com.github.dynamic.threadpool.config.event.LocalDataChangeEvent;
import com.github.dynamic.threadpool.config.model.CacheItem;
import com.github.dynamic.threadpool.config.model.ConfigAllInfo;
import com.github.dynamic.threadpool.config.notify.NotifyCenter;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config cache service.
 *
 * @author chen.ma
 * @date 2021/6/24 21:19
 */
public class ConfigCacheService {

    static ConfigService configService = null;

    private static final ConcurrentHashMap<String, CacheItem> CACHE = new ConcurrentHashMap();

    public static boolean isUpdateData(String groupKey, String md5, String ip) {
        String contentMd5 = ConfigCacheService.getContentMd5IsNullPut(groupKey, ip);
        return Objects.equals(contentMd5, md5);
    }

    /**
     * Get Md5.
     * TODO：Add IP, different IP thread pool rewrite
     * TODO：groupKey && Md5 Cache
     *
     * @param groupKey
     * @param ip
     * @return
     */
    private static String getContentMd5IsNullPut(String groupKey, String ip) {
        CacheItem cacheItem = CACHE.get(groupKey);
        if (cacheItem != null) {
            return cacheItem.md5;
        }

        if (configService == null) {
            configService = ApplicationContextHolder.getBean(ConfigService.class);
        }
        String[] split = groupKey.split("\\+");

        ConfigAllInfo config = configService.findConfigAllInfo(split[0], split[1], split[2]);
        if (config != null && !StringUtils.isEmpty(config.getTpId())) {
            String md5 = Md5Util.getTpContentMd5(config);
            cacheItem = new CacheItem(groupKey, md5);
            CACHE.put(groupKey, cacheItem);
        }
        return (cacheItem != null) ? cacheItem.md5 : Constants.NULL;
    }

    public static String getContentMd5(String groupKey) {
        if (configService == null) {
            configService = ApplicationContextHolder.getBean(ConfigService.class);
        }

        String[] split = groupKey.split("\\+");
        ConfigAllInfo config = configService.findConfigAllInfo(split[0], split[1], split[2]);
        if (config == null || StringUtils.isEmpty(config.getTpId())) {
            String errorMessage = String.format("config is null. tpId :: %s, itemId :: %s, tenantId :: %s", split[0], split[1], split[2]);
            throw new RuntimeException(errorMessage);
        }

        return Md5Util.getTpContentMd5(config);
    }

    public static void updateMd5(String groupKey, String md5, long lastModifiedTs) {
        CacheItem cache = makeSure(groupKey);
        if (cache.md5 == null || !cache.md5.equals(md5)) {
            cache.md5 = md5;
            cache.lastModifiedTs = lastModifiedTs;
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey));
        }
    }

    static CacheItem makeSure(final String groupKey) {
        CacheItem item = CACHE.get(groupKey);
        if (null != item) {
            return item;
        }
        CacheItem tmp = new CacheItem(groupKey);
        item = CACHE.putIfAbsent(groupKey, tmp);
        return (null == item) ? tmp : item;
    }

}
