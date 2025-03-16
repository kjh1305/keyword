FROM openjdk:17

WORKDIR /app

COPY build/libs/demo.jar demo.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "demo.jar"]