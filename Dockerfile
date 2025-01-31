FROM openjdk:21
COPY build/libs/waifu-all.jar /waifu.jar
VOLUME ["/home/waifu"]
WORKDIR /home/waifu
RUN cd /home/waifu
ENTRYPOINT ["java", "-jar", "/waifu.jar"]
