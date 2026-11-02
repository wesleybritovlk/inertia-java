# The Boot 2.7 demo (javax / Java 8) does not compile under the GraalVM 17 native
# toolchain — Boot 2 has no AOT engine — so this one runs the FAT JAR (Tomcat 9 embedded)
# on the plain Java 8 mode. The Svelte front-end is rebuilt EVERY time like the other
# three demos do. The java distribution is Liberica (BellSoft): the JDK and the JRE both
# on Alpine — the musl constraint applies to native-image binaries, not to JVM images.
#
# build: docker build -f inertia-demo/inertia-demo-spring-boot2/Dockerfile -t inertia-demo-spring-boot2:jvm .
# run:   docker run --rm -p 8082:8080 inertia-demo-spring-boot2:jvm
# → http://localhost:8082
FROM node:20-alpine AS frontend
WORKDIR /workspace
COPY inertia-demo/frontend-demo-svelte inertia-demo/frontend-demo-svelte
RUN cd inertia-demo/frontend-demo-svelte && npm ci && npm run build

FROM bellsoft/liberica-openjdk-alpine:17 AS build
WORKDIR /workspace
# maven's own scripts are glibc-free (sh + jars) — the maven:3.9 image is only the source
# of the /usr/share/maven tree; the JDK running it is Liberica above
COPY --from=maven:3.9 /usr/share/maven /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/local/bin/mvn
COPY pom.xml .
COPY inertia-core inertia-core
COPY inertia-web inertia-web
COPY inertia-servlet inertia-servlet
COPY inertia-servlet-javax inertia-servlet-javax
COPY inertia-spring-boot inertia-spring-boot
COPY inertia-spring-boot2 inertia-spring-boot2
COPY inertia-quarkus inertia-quarkus
COPY inertia-demo inertia-demo
COPY --from=frontend /workspace/inertia-demo/frontend-assets inertia-demo/frontend-assets
RUN mvn -B -pl :inertia-demo-spring-boot2 -am package -DskipTests

FROM bellsoft/liberica-openjre-alpine:8
WORKDIR /application
COPY --from=build /workspace/inertia-demo/inertia-demo-spring-boot2/target/inertia-demo-spring-boot2.jar /application/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/application/app.jar"]
