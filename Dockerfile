FROM openjdk:17

#WORKDIR /app

#RUN gradlew bootJar

#COPY /var/www/demo-0.0.1-SNAPSHOT.jar var/www/app/demo.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "demo.jar"]