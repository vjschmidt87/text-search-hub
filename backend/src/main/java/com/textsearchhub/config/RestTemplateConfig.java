package com.textsearchhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import java.util.concurrent.TimeUnit;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() throws Exception {
        // Trust all certificates — only used for public open-access APIs
        SSLContext sslContext = SSLContextBuilder.create()
            .loadTrustMaterial(null, (chain, authType) -> true)
            .build();

        SSLConnectionSocketFactory socketFactory =
            new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.of(5, TimeUnit.SECONDS))
            .setResponseTimeout(Timeout.of(15, TimeUnit.SECONDS))
            .build();

        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(socketFactory)
                    .build()
            )
            .setDefaultRequestConfig(requestConfig)
            .setUserAgent("Mozilla/5.0 (compatible; TextSearchHub/1.0; +https://github.com/text-search-hub)")
            .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
}
