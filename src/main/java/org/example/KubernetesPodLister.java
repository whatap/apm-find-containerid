//package org.example;
//import io.kubernetes.client.openapi.models.V1Container;
//import io.kubernetes.client.openapi.models.V1ContainerStatus;
//import io.kubernetes.client.openapi.models.V1EnvVar;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.Response;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.List;
//
//public class KubernetesPodLister {
//    public static void main(String[] args) throws IOException {
//        OkHttpClient client = new OkHttpClient();
//
////        CA 인증서 파일 경로
////        String caCrtFile = "/path/to/ca.crt";
//
//        // 서비스 어카운트 토큰을 읽어옵니다.
//        String token = new String(Files.readAllBytes(Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/token")));
//
//        // API 서버의 주소를 설정합니다.
//        String apiServer = "https://localhost:57531";
//
//        // HttpRequest를 생성합니다.
//        Request request = new Request.Builder().url(apiServer + "/api/v1/pods").addHeader("Authorization", "Bearer " + token).build();
//
//        // 요청을 전송하고 응답을 받습니다.
//        try (Response response = client.newCall(request).execute()) {
//            if (!response.isSuccessful()) {
//                throw new IOException("Unexpected code " + response);
//            }
//
//            // 응답 본문을 출력합니다.
//            System.out.println(response.body().string());
//        }
//    }
//
//    private static String getApmContainerName(List<V1Container> containers) {
//        for (V1Container container : containers) {
//            List<V1EnvVar> envVars = container.getEnv();
//            if (envVars == null || envVars.isEmpty()) {
//                continue;
//            }
//
//            for (V1EnvVar var : envVars) {
//                if ("WHATAP_JAVA_APM_IN_CLUSTER".equals(var.getName()) && "true".equals(var.getValue())) {
//                    return container.getName();
//                }
//            }
//        }
//        return null;
//    }
//
//    private static String getApmContainerId(List<V1ContainerStatus> containerStatuses, String apmContainerName) {
//        for (V1ContainerStatus containerStatus : containerStatuses) {
//            if (containerStatus.getName().equals(apmContainerName)) {
//                return containerStatus.getContainerID();
//            }
//        }
//        return null;
//    }
//}