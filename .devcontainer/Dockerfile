FROM mcr.microsoft.com/devcontainers/base:trixie

ARG JVM=temurin:25

# Archives (JDKs) live outside ~/.cache/coursier so the JDK survives if that cache is ever mounted over
ENV COURSIER_ARCHIVE_CACHE=/opt/coursier/arc
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# psql / pg_restore 18 (matches the postgres:18 db service) from the PGDG repo
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gzip postgresql-common \
    && /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y \
    && apt-get install -y --no-install-recommends postgresql-client-18 \
    && rm -rf /var/lib/apt/lists/*

# dbmate, to create and run migrations by hand
RUN arch="$(dpkg --print-architecture)" \
    && curl -fsSL -o /usr/local/bin/dbmate "https://github.com/amacneil/dbmate/releases/latest/download/dbmate-linux-${arch}" \
    && chmod +x /usr/local/bin/dbmate

# Coursier: JDK, sbt, scalafmt and the Metals language server
RUN arch="$(uname -m)" \
    && curl -fsSL "https://github.com/coursier/launchers/raw/master/cs-${arch}-pc-linux.gz" | gzip -d > /usr/local/bin/cs \
    && chmod +x /usr/local/bin/cs \
    && ln -s "$(cs java-home --jvm "${JVM}")" "${JAVA_HOME}" \
    && cs install --install-dir /usr/local/bin sbt scalafmt metals \
    && java -version && metals --version
