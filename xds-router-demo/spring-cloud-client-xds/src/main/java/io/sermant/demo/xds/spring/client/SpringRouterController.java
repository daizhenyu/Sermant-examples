/*
 * Copyright (C) 2024-2024 Sermant Authors. All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.sermant.demo.xds.spring.client;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * SpringRouterController
 *
 * @author daizhenyu
 * @since 2024-09-23
 **/
@RequestMapping("router")
@RestController
public class SpringRouterController {
    private static final String VERSION = "version";

    private static final int SUCCEED_CODE = 200;

    private static final String ROUTER_METHOD_PATH = "/router";

    private static final int MAX_TOTAL = 200;

    private static final int MAX_PER_ROUTE = 20;

    private static final int CONNECT_TIMEOUT = 200;

    private static final int SOCKET_TIMEOUT = 200;

    private static final int KEEP_ALIVE_TIME = 10;

    private static CloseableHttpClient httpClient;

    private static CloseableHttpAsyncClient httpAsyncClient;

    private static OkHttpClient okClient;

    @Autowired
    private RestTemplate restTemplate;

    static {
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "200");

        PoolingHttpClientConnectionManager httpClientConnectionManager = new PoolingHttpClientConnectionManager();
        httpClientConnectionManager.setMaxTotal(MAX_TOTAL);
        httpClientConnectionManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setSocketTimeout(SOCKET_TIMEOUT)
                .build();
        httpClient = HttpClients.custom()
                .setConnectionManager(httpClientConnectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        PoolingNHttpClientConnectionManager nHttpClientConnectionManager = null;
        try {
            nHttpClientConnectionManager = new PoolingNHttpClientConnectionManager(new DefaultConnectingIOReactor());
        } catch (IOReactorException e) {
            //ignore
        }
        if (nHttpClientConnectionManager != null) {
            nHttpClientConnectionManager.setMaxTotal(MAX_TOTAL);
            nHttpClientConnectionManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);
        }
        httpAsyncClient = HttpAsyncClients.custom()
                .setConnectionManager(nHttpClientConnectionManager)
                .build();
        httpAsyncClient.start();

        ConnectionPool pool = new ConnectionPool(MAX_TOTAL, KEEP_ALIVE_TIME, TimeUnit.MINUTES);
        okClient = new OkHttpClient.Builder().connectionPool(pool).build();
    }

    /**
     * check service status
     *
     * @return result
     */
    @RequestMapping("checkStatus")
    public String checkStatus() {
        return "ok";
    }

    /**
     * test httpclient routing
     *
     * @param host host
     * @param version version
     * @return result
     */
    @RequestMapping("httpClient")
    public String testHttpClientRouting(String host, String version) {
        String url = buildUrl(host);
        try {
            HttpGet request = new HttpGet(url);
            request.addHeader(VERSION, version);
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                String result = EntityUtils.toString(response.getEntity());
                mockRealLogic(result);
                return result;
            } else {
                return "";
            }
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * test jdk http routing
     *
     * @param host host
     * @param version version
     * @return result
     */
    @RequestMapping("jdkHttp")
    public String testJdkHttpRouting(String host, String version) {
        String url = buildUrl(host);
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty(VERSION, version);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                String result = response.toString();
                mockRealLogic(result);
                return result;
            } else {
                return "";
            }
        } catch (IOException e) {
            return "";
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                return "";
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * test okhttp3 routing
     *
     * @param host host
     * @param version version
     * @return result
     */
    @RequestMapping("okHttp3")
    public String testOkHttp3Routing(String host, String version) {
        String url = buildUrl(host);
        Request request = new okhttp3.Request.Builder()
                .url(url)
                .addHeader(VERSION, version)
                .build();
        try (okhttp3.Response response = okClient.newCall(request).execute()) {
            int statusCode = response.code();
            if (statusCode == SUCCEED_CODE) {
                String result = response.body().string();
                mockRealLogic(result);
                return result;
            } else {
                return "";
            }
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * test http async client routing
     *
     * @param host host
     * @param version version
     * @return result
     */
    @RequestMapping("httpAsyncClient")
    public String testHttpAsyncClientRouting(String host, String version) {
        String url = buildUrl(host);
        try {
            HttpGet request = new HttpGet(url);
            request.setHeader(VERSION, version);
            Future<HttpResponse> future = httpAsyncClient.execute(request, null);
            HttpResponse response = future.get();
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                String result = EntityUtils.toString(response.getEntity());
                mockRealLogic(result);
                return result;
            } else {
                return "";
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            return "";
        }
    }

    /**
     * test RestTemplate routing
     *
     * @param host host
     * @param version version
     * @return result
     */
    @RequestMapping("restTemplate")
    public String testRestTemplateRouting(String host, String version) {
        String url = buildUrl(host);
        HttpHeaders headers = new HttpHeaders();
        headers.add(VERSION, version);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
        );
        if (response.getStatusCode().is2xxSuccessful()) {
            String result = response.getBody();
            mockRealLogic(result);
            return result;
        }
        return "";
    }

    private String buildUrl(String host) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("http://");
        urlBuilder.append(host);
        urlBuilder.append(ROUTER_METHOD_PATH);
        return urlBuilder.toString();
    }

    private void mockRealLogic(String result) {
        int i = 300;
        String tempString = "";
        while (i >= 0) {
            if (result.contains("message")) {
                tempString = "getMessage";
            } else {
                tempString = "getTime";
            }
            tempString.split("");
            i--;
        }
    }
}