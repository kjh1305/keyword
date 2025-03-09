FROM openjdk:17

WORKDIR /app

#RUN gradlew bootJar

COPY build/libs/demo-0.0.1-SNAPSHOT.jar /app/demo.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "demo.jar"]