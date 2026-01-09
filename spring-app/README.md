run the application
```sh
cd spring-app

# reset and build the database
./database/run.sh build

# run the spring boot application
./mvnw spring-boot:run
```


## TODO
- [x] Implement datasoure extractor logic for pdf, markdown, asciidoc
- [x] Handle if the file already exists in the datastore
- [x] Implement load steps and vector database
- [x] Add logging
- [] Implement RAG backbone
- [] Verify the RAG functionality
- [] Evaluate current performance and optimize as needed
