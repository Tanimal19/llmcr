run the application
```sh
cd spring-app

# reset and build the database
./database/run.sh build

# make sure ollama is running
ollama serve &

# run the spring boot application
./mvnw spring-boot:run
```

to clean up ollama
```sh
ps aux | grep ollama
kill {pid}
```


## TODO
- [] Implement datasoure extractor logic for pdf, markdown, asciidoc
- [] Handle if the file already exists in the datastore
- [] Implement load steps and vector database
- [] Add logging
- [] Implement RAG backbone
- [] Verify the RAG functionality
- [] Evaluate current performance and optimize as needed
