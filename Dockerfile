FROM openjdk:20
COPY build/libs/waifu-all.jar /waifu.jar
VOLUME ["/home/waifubot"]
WORKDIR /home/waifubot
ENTRYPOINT ["java", "--add-modules", "ALL-MODULE-PATH", "--add-opens", "java.base/java.util.jar=ALL-UNNAMED", "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED", "--add-exports", "java.base/sun.security.util=ALL-UNNAMED", "--enable-preview", "-Dwaifu.rootdir=/home/waifubot", "-Dwaifu.propsFile=/run/secrets/properties", "-jar", "/waifu.jar"]
