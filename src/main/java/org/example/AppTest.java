package org.example;
import whatap.util.ThreadUtil;

public class AppTest {
    public static void main(String[] args) {
        System.out.println("start:main");
        System.out.println("start:KubeClientThread");
        KubeApiClientTest client = KubeApiClientTest.getInstance();
        while (true){
            ThreadUtil.sleep(2000);
            System.out.println("try:getContainerID()");
            String apmContainerID = client.getContainerID();
            System.out.println("apmContainerID="+apmContainerID);
            if (apmContainerID != null){
                System.out.println("success:find//ContainerID="+apmContainerID);
                break;
            }
            else{
                System.out.println("failed:retry-find//ContainerID");
            }
        }
    }
}