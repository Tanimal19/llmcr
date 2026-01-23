
## Container Services
This application depends on two services: FAISS for vector search, and mariadb for data storage. You can see the configuration in `docker-compose.yml`.

```sh
docker-compose up -d    # start the service in background
docker-compose stop     # pause the service
docker-compose restart  # resume the service
docker-compose down -v  # delete the service and volume
```

The index file of FAISS is stored in `./faiss_service/data`.
The database data is stored in docker volume, you can backup it via:
```sh
docker exec mariadb \ 
  mariadb-dump -u root -proot123 ragdb > ragdb_backup.sql
```

## ETL Process
The ETL (Extract, Transform, Load) process is implemented in the `ETLRunner.java` file.
To run the ETL process, execute `spring-app/run.sh`.
