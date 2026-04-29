source .env

SPRING_ARGUMENTS="--app.mode=test"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

cd "spring-app/"

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$SPRING_ARGUMENTS" \
    2>&1 | tee ../logs/spring-app-$TIMESTAMP.log
