# jdbc-read-only-requests
Sample console Java app for jdbc read only requests

## Prepare
```
yum install -y java-1.8.0-openjdk-devel git
```
## PostgreSQL

### Install PostgreSQL
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
       GENERATE_SERIES(1, 900000) gen
 WHERE gen <= sections * 3000;
```


## Clone repo
```
git clone https://github.com/patsevanton/jdbc-read-only-requests.git
cd jdbc-read-only-requests
wget https://jdbc.postgresql.org/download/postgresql-42.2.14.jar
```

## Compile and run if Single Server PostgreSQL
```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSql.java
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSql
```

## Compile and run if Cluster PostgreSQL
```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSqlRepl.java
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl
```
