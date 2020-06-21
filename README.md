# jdbc-read-only-requests
Sample console Java app for jdbc read only requests

## PostgreSQL одиночный сервер

### Устанавливаем зависимости
```
yum install -y java-1.8.0-openjdk-devel git
```

### Install PostgreSQL одиночный сервер
Устанавливаем PostgreSQL 12 по инструции с сайта https://www.postgresql.org/download/linux/redhat/

```
create user test with password 'password';
create database test with owner test;
```

### Отключаем ident в pg_hba.conf
```
#host    all             all             127.0.0.1/32            ident
#host    all             all             ::1/128                 ident
```

### Добавляем юзера test в pg_hba.conf
```
host    test             test             127.0.0.1/32            md5
host    test             test             ::1/128                 md5
```

### Перезагружаем PostgreSQL
```
systemctl restart postgresql-12
```

### Создаем таблицу scale_data
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

### Генерируем данные в таблице scale_data
```
INSERT INTO scale_data
SELECT sections.*, gen.*
     , CEIL(RANDOM()*100) 
  FROM GENERATE_SERIES(1, 300)     sections,
       GENERATE_SERIES(1, 9000) gen
 WHERE gen <= sections * 300;
```


### Клонируем репо jdbc-read-only-requests
```
git clone https://github.com/patsevanton/jdbc-read-only-requests.git
cd jdbc-read-only-requests
wget https://jdbc.postgresql.org/download/postgresql-42.2.14.jar
```


### Для одиночного PostgreSQL компилируем код и запускаем его
```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSqlRepl.java
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl
```

## PostgreSQL кластер

Устанавливаем PostgreSQL кластер из репозитория https://github.com/vitabaks/postgresql_cluster
```
git clone https://github.com/vitabaks/postgresql_cluster
```
Изменяем адреса серверов в inventory на свои. 

Выставляем синхронный режим
```
synchronous_mode: true
```
Активируем Haproxy, который может отпределять кто Leader, а кто c помощью health check
```
with_haproxy_load_balancing: true
```
Выключаем pgbouncer, так как оне будет мешать экперименту.
```
install_pgbouncer: false
```
После установки у вас должно быть примерно такая картина
```
patronictl -c /etc/patroni/patroni.yml list
```
![](https://habrastorage.org/webt/j1/4b/xw/j14bxwjwu8jdabj7ygew94jcx8c.png)

### Устанавливаем зависимости на Leader, так как на нем будем запускать Java приложение
```
yum install -y java-1.8.0-openjdk-devel git
```

## Создаем postgresql юзера test и базу данных test
```
su - postgres
psql
create user test with password 'password';
create database test with owner test;
```

### Проверяем Read-Only реплику
```
/usr/pgsql-12/bin/psql --host=172.26.10.66 -U test test
Password for user test: 
psql (12.3)
Type "help" for help.

test=> create  user test1 with password 'password';
ERROR:  cannot execute CREATE ROLE in a read-only transaction
test=> 
```

### Создаем таблицу scale_data в бд test от пользователя test
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

### Генерируем данные в таблице scale_data
```
INSERT INTO scale_data
SELECT sections.*, gen.*
     , CEIL(RANDOM()*100) 
  FROM GENERATE_SERIES(1, 300)     sections,
       GENERATE_SERIES(1, 9000) gen
 WHERE gen <= sections * 300;
```

### Клонируем репо jdbc-read-only-requests
```
git clone https://github.com/patsevanton/jdbc-read-only-requests.git
cd jdbc-read-only-requests
wget https://jdbc.postgresql.org/download/postgresql-42.2.14.jar
```

### Правим jdbc строку подключения в файле JavaPostgreSqlRepl.java

String url = "jdbc:postgresql://localhost:5432/test?targetServerType=primary";
```
Поменять на 
```
String url = "jdbc:postgresql://ip-адрес-Leader:5000/test?targetServerType=primary";
```

А строку содержащую preferSecondary
```
String url2 = "jdbc:postgresql://localhost:5432/test?targetServerType=preferSecondary&loadBalanceHosts=true";
```
меняем на 
```
String url2 = "jdbc:postgresql://ip-адрес-Leader:5002/test?targetServerType=preferSecondary&loadBalanceHosts=true";
```

Должно получиться примерно так:
![](https://habrastorage.org/webt/wu/q0/1m/wuq01mkxbm0jeqxsh88vjbcc4v8.png)


### Rомпилируем код и запускаем его
```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSqlRepl.java
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl
```
