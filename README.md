# jdbc-read-only-requests
Sample console Java app for jdbc read only requests

## Prepare
```
yum install -y java-1.8.0-openjdk-devel git
```
## Install PostgreSQL
```
create user test with password 'password';
create database test with owner test;
```

## Clone repo
```
git clone https://github.com/patsevanton/jdbc-read-only-requests.git
cd jdbc-read-only-requests
wget https://jdbc.postgresql.org/download/postgresql-42.2.14.jar
```

## Compile and run
```
javac -cp "./postgresql.jar" JavaPostgreSql.java
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSql
```
