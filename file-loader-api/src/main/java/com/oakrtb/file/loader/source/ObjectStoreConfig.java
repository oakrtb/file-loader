package com.oakrtb.file.loader.source;

import java.util.Objects;

/**
 * Connection settings for object storage (OSS / TOS).
 *
 * @param endpoint      service endpoint, e.g. {@code https://oss-cn-hangzhou.aliyuncs.com}
 * @param accessKey     access key id
 * @param accessSecret  access key secret
 * @param bucket        bucket name
 * @param region        region (required by TOS; optional for OSS)
 * @param clientOptions timeouts / retries
 */
public record ObjectStoreConfig(
        String endpoint,
        String accessKey,
        String accessSecret,
        String bucket,
        String region,
        ObjectStoreClientOptions clientOptions
) {
    public ObjectStoreConfig {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(accessKey, "accessKey");
        Objects.requireNonNull(accessSecret, "accessSecret");
        Objects.requireNonNull(bucket, "bucket");
        if (clientOptions == null) {
            clientOptions = ObjectStoreClientOptions.defaults();
        }
    }

    public static ObjectStoreConfig oss(String endpoint, String accessKey, String accessSecret, String bucket) {
        return new ObjectStoreConfig(endpoint, accessKey, accessSecret, bucket, null, ObjectStoreClientOptions.defaults());
    }

    public static ObjectStoreConfig oss(
            String endpoint,
            String accessKey,
            String accessSecret,
            String bucket,
            ObjectStoreClientOptions clientOptions) {
        return new ObjectStoreConfig(endpoint, accessKey, accessSecret, bucket, null, clientOptions);
    }

    public static ObjectStoreConfig tos(
            String region, String endpoint, String accessKey, String accessSecret, String bucket) {
        return new ObjectStoreConfig(endpoint, accessKey, accessSecret, bucket,
                Objects.requireNonNull(region, "region"), ObjectStoreClientOptions.defaults());
    }

    public static ObjectStoreConfig tos(
            String region,
            String endpoint,
            String accessKey,
            String accessSecret,
            String bucket,
            ObjectStoreClientOptions clientOptions) {
        return new ObjectStoreConfig(endpoint, accessKey, accessSecret, bucket,
                Objects.requireNonNull(region, "region"), clientOptions);
    }
}
