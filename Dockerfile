# Separate stage so we don't pack in findutils or intermediaries, also allows cacheing
FROM openjdk:20 as builder
RUN microdnf install findutils
COPY / /
RUN ./gradlew buildBot

# Final image
FROM openjdk:20 as final
COPY --from=builder /build/libs/waifu-all.jar /waifu.jar
WORKDIR /home/waifubot
ENTRYPOINT ["java", "--add-modules", "ALL-MODULE-PATH", "--add-opens", "java.base/java.util.jar=ALL-UNNAMED", "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED", "--add-exports", "java.base/sun.security.util=ALL-UNNAMED", "--enable-preview", "-Dwaifu.rootdir=/home/waifubot", "-Dwaifu.propsFile=/run/secrets/properties", "-jar", "/waifu.jar"]
