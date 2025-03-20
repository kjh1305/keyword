FROM openjdk:17

ENV SPRING_PROFILES_ACTIVE=prod

WORKDIR /app

COPY build/libs/demo.jar demo.jar

EXPOSE 8888

ENTRYPOINT ["java", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "-jar", "demo.jar"]