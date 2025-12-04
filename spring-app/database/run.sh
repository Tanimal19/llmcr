#!/bin/bash
set -e

COMPOSE_FILE="./database/docker-compose.yml"

build() {
    echo "building container..."
    docker-compose -f $COMPOSE_FILE up -d
}

stop() {
    docker-compose -f $COMPOSE_FILE stop
}

restart() {
    docker-compose -f $COMPOSE_FILE restart
}

clean() {
    echo "cleaning up containers and volumes..."
    docker-compose -f $COMPOSE_FILE down -v
}

status() {
    docker-compose -f $COMPOSE_FILE ps
}


case "$1" in
    build) build ;;
    stop) stop ;;
    restart) restart ;;
    clean) clean ;;
    status) status ;;
    *) echo "usage: $0 {build|stop|restart|clean|status}"; exit 1 ;;
esac