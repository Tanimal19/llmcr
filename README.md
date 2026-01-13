
## main application (spring)

## services
- `mariadb`: database to store etl data
- `faiss`: vector search service
  
```sh
docker-compose down -v {service}
docker-compose up -d {service}

docker-compose stop {service}
docker-compose restart {service}
```

## faiss service

#### `POST /index/add`
```json
{
    "ids": [1, 2, 3],
    "vectors": [
        [0.1, 0.2, 0.3],
        [0.4, 0.5, 0.6],
        [0.7, 0.8, 0.9]
    ]
}
```
#### `POST /index/search`
request:
```json
{
    "qvector": [0.1, 0.2, 0.3],
    "top_k": 2
}
```
response:
```json
{
    "ids": [1, 2],
    "vectors": [
        [0.1, 0.2, 0.3],
        [0.4, 0.5, 0.6]
    ],
    "scores": [0.9, 0.5]
}
```