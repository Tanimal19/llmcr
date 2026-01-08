#!/bin/bash
set -e

COMPOSE_FILE="./database/docker-compose.yml"

build() {
    echo "cleaning up containers and volumes..."
    docker-compose -f $COMPOSE_FILE down -v
    echo "building container..."
    docker-compose -f $COMPOSE_FILE up -d
}

clean() {
    echo "cleaning up containers and volumes..."
    docker-compose -f $COMPOSE_FILE down -v
}

stop() {
    docker-compose -f $COMPOSE_FILE stop
}

restart() {
    docker-compose -f $COMPOSE_FILE restart
}

status() {
    docker-compose -f $COMPOSE_FILE ps
}


case "$1" in
    build) build ;;
    clean) clean ;;
    stop) stop ;;
    restart) restart ;;
    status) status ;;
    *) echo "usage: $0 {build|clean|stop|restart|status}"; exit 1 ;;
esac