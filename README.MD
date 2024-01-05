## find container id of WHATAP-APM


### 1. 쿠버네티스 에이전트 설치
1.1 와탭 쿠버네티스 에이전트 설치

1.2 쿠버네티스 에이전트(whatap-node-agent) 이미지 변경 
```bash
kubectl set image -n whatap-monitoring daemonset whatap-node-agent whatap-node-agent=whatap/kube-apm-helper:0.1.1
```

1.3 데몬셋으로 배포된 whatap-node-agent 파드 볼륨에 대한 권한 변경
```bash
## /rootfs 디렉토리 하위 경로에 write 권한을 부여
kubectl patch daemonset -n whatap-monitoring whatap-node-agent --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/1/volumeMounts/0/readOnly", "value":false}]'
```

### 2. 테스트 파드(container-id 출력) 배포 
```yaml
apiVersion: v1
kind: Pod
metadata:
  creationTimestamp: null
  labels:
    run: apm-helper
  name: apm-helper
spec:
  containers:
  - image: whatap/apm-find-containerid:latest
    name: apm-helper-container
    resources: {}
    env:
    - name: get_apm_container_id_using_whatap
      value: "true"
    - name: NODE_IP
      valueFrom: { fieldRef: { fieldPath: status.hostIP } }
    - name: NODE_NAME
      valueFrom: { fieldRef: { fieldPath: spec.nodeName } }
    - name: POD_NAME
      valueFrom: { fieldRef: { fieldPath: metadata.name } }
    volumeMounts:
    - name: whatap
      mountPath: /whatap
  dnsPolicy: ClusterFirst
  restartPolicy: Never
  volumes:
  - name: whatap
    hostPath:
      path: /whatap
status: {}
```
