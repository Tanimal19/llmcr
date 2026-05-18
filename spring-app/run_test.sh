TIMESTAMP=$(date +%Y%m%d-%H%M%S)
./mvnw verify | tee ../logs/test/test-$TIMESTAMP.log