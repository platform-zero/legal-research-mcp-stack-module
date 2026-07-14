FROM docker.io/library/eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
RUN groupadd -r legalresearch && useradd -r -g legalresearch legalresearch
WORKDIR /app
COPY stack.kotlin/legal-research-mcp/build/libs/legal-research-mcp-1.0-SNAPSHOT-all.jar /app/app.jar
USER legalresearch
EXPOSE 8130
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
