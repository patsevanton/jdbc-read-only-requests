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
## Disable ident in PostgreSQL
```
#host    all             all             127.0.0.1/32            ident
#host    all             all             ::1/128                 ident
systemctl restart postgresql-9.6
```

## Clone repo
```
git clone https://github.com/patsevanton/jdbc-read-only-requests.git
cd jdbc-read-only-requests
wget https://jdbc.postgresql.org/download/postgresql-42.2.14.jar
```

## Compile and run
```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSql.java
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSql
```
