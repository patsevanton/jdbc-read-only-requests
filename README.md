# jdbc-read-only-requests
Sample console Java app for jdbc read only requests

## PostgreSQL кластер

## Характеристики PostgreSQL кластера

Возьмем виртуальные машины для PostgreSQL по 2 ГБ ОЗУ, чтобы бд не ввлезала в память и 3 ноды Etcd по 1ГБ.

## Установка

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

Нужно поправить конфигурацию Postgresql.conf в vars/main.yml, например по http://pgconfigurator.cybertec.at/

## Проверка кластера

После установки у вас должно быть примерно такая картина
```
patronictl -c /etc/patroni/patroni.yml list
```
![](https://habrastorage.org/webt/14/g9/kl/14g9kluwlob-bcp3nkci4huoyoe.png)

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
/usr/pgsql-12/bin/psql --host=172.26.10.74 -U test test
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
       GENERATE_SERIES(1, 300000) gen
 WHERE gen <= sections * 3000;
```

Проверяем размер БД после генерации данных:

![](https://habrastorage.org/webt/ce/2a/st/ce2astdeuhsaq_7hk6oul4km4is.png)

### Клонируем репо jdbc-read-only-requests
```
git clone https://github.com/patsevanton/jdbc-read-only-requests.git
cd jdbc-read-only-requests
wget https://jdbc.postgresql.org/download/postgresql-42.2.14.jar
```

## Тестирование. Все запросы идут на Leader

### Правим jdbc строку подключения в файле JavaPostgreSqlRepl.java
```
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
![](https://habrastorage.org/webt/7y/e5/5b/7ye55bh_pyoktphlqnecd4qcux8.png)


### Rомпилируем код и запускаем его
```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSqlRepl.java
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl
```
