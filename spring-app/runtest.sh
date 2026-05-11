source ../.env

TIMESTAMP=$(date +%Y%m%d-%H%M%S)

./mvnw test \
    2>&1 | tee ../logs/spring-test-$TIMESTAMP.log
