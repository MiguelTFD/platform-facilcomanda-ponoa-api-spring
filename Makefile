include .env
export

.PHONY: up down run clean

up:
	docker compose up -d

down:
	docker compose down

run:
	mvn spring-boot:run

clean: down
	mvn clean
