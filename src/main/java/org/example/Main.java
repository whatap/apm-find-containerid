package org.example;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        //1. POD_NAME 환경변수 체크
        String podName = System.getenv("POD_NAME");

        //2. 위 환경변수가 없으면 쿠버 클러스터 환경에 설치된 APM 이 아닌것으로 판단함.
        if (podName == null){
            System.out.println("You need to register the POD_NAME environment variables");
            return;
        }

        try{
            //2.1 환경변수가 존재하면 클라이언트 API 를 사용
            ApiClient client = Config.fromCluster();
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();

            //2.2 네임스페이스 읽어오기
            String namespace = new String(Files.readAllBytes(Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace")));

            //2.3 네임스페이스와 환경변수에 등록된 파드 이름을 통해 파드를 조회한다.
            V1Pod apmPod = api.readNamespacedPod(podName, namespace, null);
            V1PodSpec podSpec = apmPod.getSpec();
            V1PodStatus podStatus = apmPod.getStatus();

            if (podSpec == null || podStatus == null) {
                System.out.println("getSpec=" + podSpec + "//" + "getStatus=" + podStatus);
                return;
            }

            List<V1Container> containers = podSpec.getContainers();
            List<V1ContainerStatus> containerStatuses = podStatus.getContainerStatuses();

            if (containers == null || containerStatuses == null) {
                return;
            }

            String apmContainerName = getApmContainerName(containers);
            if (apmContainerName == null) {
                System.out.println("WHATAP_JAVA_APM_IN_CLUSTER 가 등록된 컨테이너가 존재하지 않음");
                return;
            }

            String apmContainerId = getApmContainerId(containerStatuses, apmContainerName);
            if (apmContainerId == null) {
                System.out.println("Container with WHATAP_JAVA_APM_IN_CLUSTER registered does not exist: apmContainerName=" + apmContainerName);
                return;
            }

            System.out.println("container with WHATAP_JAVA_APM_IN_CLUSTER registered does exist :  apmContainerName=" + apmContainerName + "//apmContainerId=" + apmContainerId);

        } catch (ApiException e){
            System.err.println("API 호출에 실패했습니다. 권한이 부족하거나 API 서버에 문제가 있을 수 있습니다."+"statusCode="+ e.getCode());
            e.printStackTrace();
        } catch (Exception e){
            System.err.println("알 수 없는 오류가 발생했습니다.");
            e.printStackTrace();
        }
    }
    private static String getApmContainerName(List<V1Container> containers) {
        for (V1Container container : containers) {
            List<V1EnvVar> envVars = container.getEnv();
            if (envVars == null || envVars.isEmpty()) {
                continue;
            }

            for (V1EnvVar var : envVars) {
                if ("WHATAP_JAVA_APM_IN_CLUSTER".equals(var.getName()) && "true".equals(var.getValue())) {
                    return container.getName();
                }
            }
        }
        return null;
    }

    private static String getApmContainerId(List<V1ContainerStatus> containerStatuses, String apmContainerName) {
        for (V1ContainerStatus containerStatus : containerStatuses) {
            if (containerStatus.getName().equals(apmContainerName)) {
                return containerStatus.getContainerID();
            }
        }
        return null;
    }
}