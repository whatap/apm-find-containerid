FROM ubuntu:20.04
RUN sed -i '/security.ubuntu.com/d' /etc/apt/sources.list
RUN sed -i 's/deb.ubuntu.com/ftp.daumkakao.com/g' /etc/apt/sources.list
RUN apt update
RUN apt install -y curl procps net-tools tcptraceroute openjdk-17-jdk-headless

# 애플리케이션을 실행할 디렉토리를 생성합니다.
WORKDIR /usr/src/app

# JAR 파일을 컨테이너로 복사합니다.
COPY target/apm-find-containerid-3.0.0.jar /usr/src/app

# 컨테이너가 시작될 때 실행할 명령어를 설정합니다.
ENTRYPOINT ["java", "-jar", "/usr/src/app/apm-find-containerid-3.0.0.jar"]