# jdbc-read-only-requests
Sample console Java app for jdbc read only requests

## Prepare
```
yum install -y java-1.8.0-openjdk-devel git
```
## PostgreSQL

### Install PostgreSQL
Устанавливаем PostgreSQL 12 по инструции с сайта https://www.postgresql.org/download/linux/redhat/

```
create user test with password 'password';
create database test with owner test;
```

### Disable ident in PostgreSQL
```
#host    all             all             127.0.0.1/32            ident
#host    all             all             ::1/128                 ident
```

### Add custom user to pg-hba
```
host    test             test             127.0.0.1/32            md5
host    test             test             ::1/128                 md5
```

### Restart PostgreSQL
```
systemctl restart postgresql-12
```

### Create Table
```
/usr/pgsql-12/bin/psql --host=localhost -U test test
```

```
CREATE TABLE scale_data (
   section NUMERIC NOT NULL,
   id1     NUMERIC NOT NULL,
   id2     NUMERIC NOT NULL
);
```

### Generate data
```
INSERT INTO scale_data
SELECT sections.*, gen.*
     , CEIL(RANDOM()*100) 
  FROM GENERATE_SERIES(1, 300)     sections,
       GENERATE_SERIES(1, 9000) gen
 WHERE gen <= sections * 300;
```


## Clone repo
```
git clone https://github.com/patsevanton/jdbc-read-only-requests.git
cd jdbc-read-only-requests
wget https://jdbc.postgresql.org/download/postgresql-42.2.14.jar
```


## If PostgreSQL Single, then compile and run
```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSqlRepl.java
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl
```

## If PostgreSQL Cluster, then change jdbc

Находим IP Leader с помощью команды
```
patronictl -c /etc/patroni/patroni.yml list
```
В файле JavaPostgreSqlRepl.java
```
String url = "jdbc:postgresql://localhost:5432/test?targetServerType=primary";
```
Поменять на 
```
String url = "jdbc:postgresql://ip-адрес-Leader:5000/test?targetServerType=primary";
```
