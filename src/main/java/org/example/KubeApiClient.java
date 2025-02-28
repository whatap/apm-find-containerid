package org.example;
import whatap.org.json.JSONArray;
import whatap.org.json.JSONObject;
import whatap.util.ThreadUtil;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class KubeApiClient extends Thread {

    private static KubeApiClient instance = null;

    public static final String SERVICEACCOUNT_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    private static final String WHATAP_TOKEN_PATH = "/whatap/token";
    private static final String LOCAL_CA_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    private static final String LOCAL_NAMESPACE_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";
//    private static final String KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
//    private static final String KUBERNETES_SERVICE_PORT = "KUBERNETES_SERVICE_PORT";
    private static final String KUBERNETES_SERVICE_HOST = "TEST_HOST";
    private static final String KUBERNETES_SERVICE_PORT = "TEST_PORT";

    private String localNamespace = null;
    private String localPodName = null;
    private String whatapToken = null;
    private InputStream localCaCrt = null;
    private SSLContext localSSLContext = null;
    private String basePath = null;
    private HttpsURLConnection httpsURLConnection = null;

    private String containerID = null;

    public static synchronized KubeApiClient getInstance() {
        if (instance == null) {
            KubeApiClient ist = new KubeApiClient();
            ist.init();
            ist.setDaemon(false);
            ist.setName("KubeApiClient");
            ist.start();
            instance = ist;
        }
        return instance;
    }

    private void init() {
        // kubernetes 내부 host 와 path 를 환경변수에서 가져옴
        String host = System.getenv(KUBERNETES_SERVICE_HOST);
        String port = System.getenv(KUBERNETES_SERVICE_PORT);
        host = "10.21.10.71";
        port = "443";

        if (host == null || port == null) {
            return;
        }

        // 기본적인 쿠버네티스 클러스터 구성 정보 설정
        // 파드 이름, api-server, 네임스페이스, token, caCrt
        this.setBasePath(host, port);
        this.setLocalPodName();
        this.setLocalNamespace();
        this.setWhatapToken();
        try {
            this.setLocalSSLContext();
        } catch (CertificateException | KeyStoreException | IOException | NoSuchAlgorithmException | KeyManagementException e) {
            System.out.println("inItError="+e.getMessage());
        }
    }

    private void setLocalPodName(){
        try {
            // https://kubernetes.io/docs/concepts/containers/container-environment/
            // The hostname of a Container is the name of the Pod in which the Container is running.
            // It is available through the hostname command or the gethostname function call in libc.
            this.localPodName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            System.out.println("setLocalPodNameError="+e.getMessage());
        }
    }
    private void setLocalNamespace() {
        // apiserver에 요청하기 위해 필요한 데이터 path 가져오기 (namespace)
        Path namespacePath = Paths.get(LOCAL_NAMESPACE_PATH);

        // namespace path가 존재하지 않는 경우 return
        if (!Files.exists(namespacePath)) {
            System.out.println("localNamespacePath is not found");
        } else {
            // namespace path가 존재하는 경우 값을 읽어온다
            try {
                this.localNamespace = new String(Files.readAllBytes(namespacePath));
            } catch (IOException e) {
                System.out.println("Error occured while reading namespace");
                return;
            }
            System.out.println("localNamespace=" + this.localNamespace);
        }
    }
    private void setWhatapToken() {
        String token = System.getenv("WHATAP_TOKEN");
        if (token != null) {
            this.whatapToken = token;
            return;
        }

        // apiserver에 요청하기 위해 필요한 데이터 path 가져오기 (token)
        Path tokenPath = Paths.get(WHATAP_TOKEN_PATH);

        // token path가 존재하지 않는 경우 SERVICEACCOUNT_TOKEN_PATH 사용
        if (!Files.exists(tokenPath)) {
            System.out.println("Whatap token is not found// use SERVICEACCOUNT_TOKEN_PATH");
            tokenPath = Paths.get(SERVICEACCOUNT_TOKEN_PATH);
        }
        try {
            this.whatapToken = new String(Files.readAllBytes(tokenPath));
        } catch (IOException e) {
            System.out.println("Error occured while reading whatap token=" + e.getMessage());
        }
    }
    private void setLocalSSLContext() throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, KeyManagementException {
        // apiserver에 요청하기 위해 필요한 데이터 path 가져오기 (ca.crt)
        // Path tokenPath = Paths.get(SERVICEACCOUNT_CA_PATH);
        Path caCrtPath = Paths.get(LOCAL_CA_PATH);

        // token path가 존재하지 않는 경우 return
        if (!Files.exists(caCrtPath)) {
            System.out.println("caCrtPath is not found");
        } else {
            // token path가 존재하는 경우 값을 읽어온다.
            try {
                this.localCaCrt = Files.newInputStream(caCrtPath);
            } catch (IOException e) {
                System.out.println("Error occured while reading localCaCrt=" + e.getMessage());
                return;
            }
            System.out.println("localCaCrt=" + this.localCaCrt);
        }
        // 1.1 CA 인증서 로드
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert = (X509Certificate) cf.generateCertificate(this.localCaCrt);

        // 1.2 키스토어에 CA 인증서 추가
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("caCert", caCert);

        // 1.3 트러스트매니저 팩토리 초기화
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        // 1.4 SSL 컨텍스트 초기화
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        this.localSSLContext = sslContext;
    }
    private void setBasePath(String host, String port) {
        try {
            int iPort = Integer.parseInt(port);
            URI uri = new URI("https", null, host, iPort, null, null, null);
            String uriString = uri.toString();
            if (uriString != null) {
                if (uriString.endsWith("/")) {
                    uriString = uriString.substring(0, uriString.length() - 1);
                }
                this.basePath = uriString;
            }
        } catch (URISyntaxException | NumberFormatException e) {
            System.out.println("setBasePathError="+e.getMessage());
        }
    }
    private void setHttpsURLConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        // HttpsUrlConnection 설정
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(this.localSSLContext.getSocketFactory());
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + this.whatapToken);
        conn.setRequestProperty("Accept", "application/json");
        this.httpsURLConnection = conn;
    }
    private void setContainerID(String containerID){
        this.containerID = containerID;
    }

    public String getContainerID(){
        return this.containerID;
    }
    @Override
    public void run() {
        while (true) {
            System.out.println("basePath=" + this.basePath);
            System.out.println("localPodName=" + this.localPodName);
            System.out.println("localNamespace=" + this.localNamespace);
            System.out.println("localSSLContext=" + this.localSSLContext);
            if (this.whatapToken == null){
                System.out.println("whatapToken=" + this.whatapToken);
                this.setWhatapToken();
                ThreadUtil.sleep(1000);
                continue;
            }

            try {
                process();
            } catch (Exception e) {
                System.out.println("KubeApiClient.run() Error="+e.getMessage());
            }
            if (this.containerID != null){
                System.out.println("already exist//containerID=" + this.containerID);
                return;
            }
            ThreadUtil.sleep(5000);
        }
    }
    public JSONObject readNamespacedPod(String namespace, String podName) throws IOException {
        //String targetPath = "/api/v1/namespaces/{namespace}/pods/{name}".replaceAll("\\{name\\}", this.escapeString(podName)).replaceAll("\\{namespace\\}", this.escapeString(this.namespace));
        String targetPath = "/api/v1/namespaces/"+namespace+"/pods/"+podName;
        String urlString = this.basePath+targetPath;
        this.setHttpsURLConnection(urlString);
        HttpsURLConnection conn = this.httpsURLConnection;

        // 요청 및 응답 처리
        int responseCode = conn.getResponseCode();
        if (responseCode == HttpsURLConnection.HTTP_OK) {
            //응답 내용 읽기
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String inputLine;
                StringBuilder response;
                response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                //JSON 파싱 및 처리
                return new JSONObject(new String(response.toString().getBytes(), StandardCharsets.UTF_8));
            } catch (Throwable e) {
                System.out.println("JSON PARSE FAILED=" + e.getMessage());
            }
        }
        return null;
    }
    public String lookupLocalContainerID(JSONObject podObject) {
        // containerStatuses
        JSONObject status = podObject.optJSONObject("status");

        // containerSpec
        JSONObject spec = podObject.optJSONObject("spec");

        // 컨테이너 ID 와 이름을 저장할 HashMap 생성
        Map<String, String> containerLookup = new HashMap<>();

        // 컨테이너 ID 와 이름을 매핑하여 HashMap에 추가

        if (status != null) {
            JSONArray containerStatuses = status.optJSONArray("containerStatuses");
            System.out.println("containerStatuses=" + containerStatuses);
            for (int i = 0; i < containerStatuses.length(); i++) {
                JSONObject containerStatus = containerStatuses.optJSONObject(i);
                System.out.println("containerStatus=" + containerStatus);
                String containerName = containerStatus.getString("name");
                String containerID = containerStatus.getString("containerID");
                containerLookup.put(containerName, containerID);
                System.out.println("containerName=" + containerName + "/// containerId=" + containerID);
            }
        }

        if (spec != null) {
            JSONArray containers = spec.optJSONArray("containers");
            for (int i = 0; i < containers.length(); i++) {
                JSONObject container = containers.optJSONObject(i);
                JSONArray envVariables = container.optJSONArray("env");
                System.out.println("envVariables:" + envVariables);
                for (int j = 0; j < envVariables.length(); j++) {
                    JSONObject envObject = envVariables.optJSONObject(i);
                    String envName = envObject.getString("name");
                    String envValue = envObject.getString("value");
                    if ("get_apm_container_id_using_whatap".equalsIgnoreCase(envName) && "true".equalsIgnoreCase(envValue)) {
                        String containerName = container.getString("name");
                        String containerID = containerLookup.get(containerName);
                        String[] tokens = containerID.split("://");
                        if (tokens.length > 1) {
                            String id = tokens[1];
                            return id;
                        } else {
                            System.out.println("Container parsing failed:" + containerID);
                        }
                    }
                }
            }
        }
        return null;
    }
    public void process() throws IOException{
        String localPodName = this.localPodName;
        // 1. URL 설정
        // API-SERVER 에 readNamespacedPod 호출
        JSONObject podObject = this.readNamespacedPod(localNamespace, localPodName);
        if (podObject == null){
            return;
        }
        // podObject 로부터 local-containerID 찾기
        String containerID = this.lookupLocalContainerID(podObject);
        System.out.println("containerID="+containerID);
        if (containerID !=null) {
            this.setContainerID(containerID);
        }
    }
}