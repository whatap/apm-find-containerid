package org.example;

import whatap.util.ThreadUtil;

public class App {
    public static void main(String[] args) {
        System.out.println("start main");
        String useWhatapTokenEnv = System.getenv("get_apm_container_id_using_whatap");
        if (useWhatapTokenEnv == null){
            useWhatapTokenEnv = "false";
        }
        String useWhatapTokenProperty = System.getProperty("get_apm_container_id_using_whatap", "false");
        System.out.println("useWhatapEnv="+useWhatapTokenEnv);
        System.out.println("useWhatapProperty="+useWhatapTokenProperty);
        try{
            if (useWhatapTokenEnv.equalsIgnoreCase("true") || useWhatapTokenProperty.equalsIgnoreCase("true")){
                //start find containerID
                System.out.println("call:KubeClientThread");
                KubeApiClient client = KubeApiClient.getInstance();
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
        }catch (Throwable e){
            System.out.println("error="+e.getMessage());
        }
    }
}