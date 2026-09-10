package com.hospital.platform.file.infrastructure;
import io.minio.MinioClient; import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration;
@Configuration public class MinioConfiguration { @Bean MinioClient minioClient(@Value("${hospital.minio.endpoint}") String endpoint,@Value("${hospital.minio.access-key}") String key,@Value("${hospital.minio.secret-key}") String secret){return MinioClient.builder().endpoint(endpoint).credentials(key,secret).build();} }
