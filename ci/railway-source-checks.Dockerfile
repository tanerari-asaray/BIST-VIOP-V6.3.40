FROM node:24-bookworm-slim AS node_tools
FROM oven/bun:1 AS bun_tools
FROM ghcr.io/cirruslabs/android-sdk:35 AS checks
USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 openjdk-17-jdk-headless ca-certificates \
 && ln -s "/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)" /opt/validation-jdk \
 && rm -rf /var/lib/apt/lists/*
COPY --from=node_tools /usr/local/bin/node /usr/local/bin/node
COPY --from=bun_tools /usr/local/bin/bun /usr/local/bin/bun
ENV JAVA_HOME=/opt/validation-jdk
ENV PATH="/opt/validation-jdk/bin:$PATH"
ARG RAILWAY_GIT_COMMIT_SHA
ENV GITHUB_SHA="$RAILWAY_GIT_COMMIT_SHA"
WORKDIR /workspace
COPY android-v6323/ /workspace/android-v6323/
COPY server/ /workspace/server/
COPY ci-source-only.init.gradle ci-verify-source-only.py /workspace/
COPY ci/railway-source-checks.sh /workspace/ci/railway-source-checks.sh
COPY ci/deep-audit-jvm-report.py /workspace/ci/deep-audit-jvm-report.py
RUN bash /workspace/ci/railway-source-checks.sh

FROM node:24-bookworm-slim
WORKDIR /validation
COPY --from=checks /workspace/source-checks/ /validation/source-checks/
COPY --from=checks /workspace/android-v6323/app/build/test-results/ /validation/jvm-test-results/
COPY ci/railway-verification-report.mjs /validation/report.mjs
CMD ["node", "/validation/report.mjs"]
