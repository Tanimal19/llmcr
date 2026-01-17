source ../.env

SPRING_ARGUMENTS="--faiss.index.name=plain"

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$SPRING_ARGUMENTS" \
    > spring-app.log 2>&1
