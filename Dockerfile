FROM openjdk:17

WORKDIR /app

COPY demo.jar /app/demo.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "demo.jar"]