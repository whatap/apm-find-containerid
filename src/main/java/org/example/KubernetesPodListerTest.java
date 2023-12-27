package org.example;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class KubernetesPodListerTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        String KUBERNETES_SERVICE_HOST = System.getenv("KUBERNETES_SERVICE_HOST");
        String KUBERNETES_PORT_443_TCP_PORT = System.getenv("KUBERNETES_PORT_443_TCP_PORT");

        if (KUBERNETES_SERVICE_HOST == null || KUBERNETES_PORT_443_TCP_PORT == null) {
            System.out.println("not kube");
            return;
        }

        // GET_CONTAINER_ID_USING_WHATAP, POD_NAME 환경변수 가져오기
        String getContainerIdUsingWhatap = System.getenv("GET_CONTAINER_ID_USING_WHATAP");
        String podName = System.getenv("POD_NAME");

        // getContainerIdUsingWhatap 환경변수가 설정되지 않거나 false 이면 동작하지 않는다.
        if (getContainerIdUsingWhatap == null || "false".equalsIgnoreCase(getContainerIdUsingWhatap)) {
            System.out.println("If you want to find the containerId using a Whatap, you must set the GET_CONTAINER_ID_USING_WHATAP environment variable to true. Currently, this value is " + getContainerIdUsingWhatap);
            return;
        }

//        CA 인증서 파일 경로
//        String caCrtFile = "/path/to/ca.crt";

        // apiserver에 요청하기 위해 필요한 데이터 path 가져오기 (token)
        Path tokenPath = Paths.get("/whatap/token");

        // token path가 존재하지 않는 경우 return
        if (!Files.exists(tokenPath)) {
            System.out.println("Whatap token is not found");
            return;
        }else{
            // token path가 존재하는 경우 값을 읽어온다.
            String whatapToken;
            try {
                whatapToken = new String(Files.readAllBytes(tokenPath));
            } catch (IOException e) {
                System.out.println("Error occured while reading whatap token");
                e.printStackTrace();
                return;
            }
            System.out.println("WhatapToken=" + whatapToken);
        }


        // apiserver에 요청하기 위해 필요한 데이터 path 가져오기 (namespace)
        Path namespacePath = Paths.get("/run/secrets/kubernetes.io/serviceaccount/namespace");

        // namespace path가 존재하지 않는 경우 return
        if (!Files.exists(namespacePath)) {
            System.out.println("namespacePath is not found");
            return;
        }else{
            // namespace path가 존재하는 경우 값을 읽어온다
            String namespace;
            try {
                namespace = new String(Files.readAllBytes(namespacePath));
            } catch (IOException e) {
                System.out.println("Error occured while reading whatap token");
                e.printStackTrace();
                return;
            }
            System.out.println("namespace=" + namespace);
        }

        String token = new String(Files.readAllBytes(tokenPath));
        String namespace = new String(Files.readAllBytes(namespacePath));

//        String url = "https://localhost:57531/api/v1/namespaces/" + namespace + "/pods/" + podName;
        String url = "https://"+KUBERNETES_SERVICE_HOST+":"+KUBERNETES_PORT_443_TCP_PORT+"/api/v1/namespaces/" + namespace + "/pods/" + podName;
        OkHttpClient client = getUnsafeOkHttpClient();

        // HttpRequest 생성.
        Request request = new Request.Builder().url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/json") //JSON 응답 요청
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String jsonResponse = response.body().string();
            Gson gson = new Gson();
            JsonObject podObject = gson.fromJson(jsonResponse, JsonObject.class);

            // 환경변수를 확인하여 GET_CONTAINER_ID_USING_WHATAP 변수를 가진 컨테이너 찾기
            JsonArray containers = podObject.getAsJsonObject("spec").getAsJsonArray("containers");
            for (JsonElement container : containers) {
                JsonObject containerObject = container.getAsJsonObject();
                JsonArray envVariables = containerObject.getAsJsonArray("env");
                for (JsonElement envVariable : envVariables) {
                    JsonObject envObject = envVariable.getAsJsonObject();
                    String envName = envObject.get("name").getAsString();
                    String envValue = envObject.get("value").getAsString();
                    if ("get_container_id_using_whatap".equalsIgnoreCase(envName) && "true".equalsIgnoreCase(envValue)) {
                        // 컨테이너 ID를 찾아 출력.
                        String containerName = containerObject.get("name").getAsString();
                        JsonArray containerStatuses = podObject.getAsJsonObject("status").getAsJsonArray("containerStatuses");
                        for (JsonElement containerStatus : containerStatuses) {
                            JsonObject containerStatusObject = containerStatus.getAsJsonObject();
                            if (containerName.equals(containerStatusObject.get("name").getAsString())) {
                                String containerId = containerStatusObject.get("containerID").getAsString();
                                System.out.println("POD NAME: " + podName);
                                String[] tokens = containerId.split("://");
                                if (tokens.length > 1){
                                    String id = tokens[1];
                                    System.out.println("Container ID: " + id);
                                }else{
                                    System.out.println("Container parsing failed:" + containerId);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            Thread.sleep(36000);
        }
    }
    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // 모든 인증서를 신뢰하는 TrustManager 생성
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // SSL 컨텍스트 생성 및 초기화
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // SSL 소켓 팩토리 생성
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // 호스트명 검증을 건너뛰는 HostnameVerifier 생성
            HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // OkHttpClient 생성 및 설정
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier(hostnameVerifier)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}