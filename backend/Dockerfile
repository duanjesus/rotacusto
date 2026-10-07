FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn .mvn
# Checkout feito no Windows pode trazer o mvnw com CRLF e sem bit de execução.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home rotacusto && chown rotacusto /app
USER rotacusto

COPY --from=build /build/target/rotacusto-backend-*.jar app.jar

# Tudo daqui pra baixo existe pra encurtar o boot: a instância gratuita dorme sem
# tráfego e acorda com uma fração de CPU, então cada segundo de CPU na subida vira
# ~10 s de espera pro usuário.
#
# 1) Jar extraído (classes fora do jar aninhado) + arquivo CDS: a JVM mapeia as
#    classes já carregadas/verificadas num "treino" feito aqui no build, em vez de
#    refazer isso a cada boot. O treino sobe o contexto do Spring e sai
#    (spring.context.exit=onRefresh) sem banco nenhum — por isso as propriedades que
#    impedem o Hibernate de conectar durante a subida.
# 2) Os flags de JVM do treino e do ENTRYPOINT precisam ser os mesmos, senão a JVM
#    recusa o arquivo CDS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

RUN java -Djarmode=tools -jar app.jar extract --destination extracted && rm app.jar
RUN SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:1/treino-cds \
    java $JAVA_OPTS -XX:ArchiveClassesAtExit=app.jsa -Xlog:cds=error \
      -Dspring.context.exit=onRefresh \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -jar extracted/app.jar

EXPOSE 8080
# MaxRAMPercentage: heap limitado a uma parte da RAM do container (512 MB).
# UseSerialGC: não disputa CPU com a aplicação. TieredStopAtLevel=1: só o JIT
# rápido — menos pico de desempenho, bem menos CPU gasta compilando na subida.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:SharedArchiveFile=app.jsa -jar extracted/app.jar"]
