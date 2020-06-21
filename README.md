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
       GENERATE_SERIES(1, 90000) gen
 WHERE gen <= sections * 3000;
```

### Создаем индекс и кластеризуем таблицу scale_data
Без индекса update и select будут упираться в диск, если бд не влазит в ОЗУ. А это мешает эксперименту.
```
CREATE INDEX scale_slow ON scale_data (section, id1, id2);

ALTER TABLE scale_data CLUSTER ON scale_slow;
CLUSTER scale_data;
```

Проверяем размер БД после генерации данных:

![](https://habrastorage.org/webt/3q/u4/rj/3qu4rjixnvl8yx1n0i2htsgbia8.png)

### Клонируем репо jdbc-read-only-requests
```
git clone https://github.com/patsevanton/jdbc-read-only-requests.git
cd jdbc-read-only-requests
wget https://jdbc.postgresql.org/download/postgresql-42.2.14.jar
```

## Тестирование. Все запросы идут на Leader

### Правим строку String nodes в файле JavaPostgreSqlRepl.java
```
String nodes = "172.26.10.73:5000";
```
Поменять на 
```
String nodes = "ip-адрес-Leader:5000";
```

А строку содержащую несколько нод закоментировать
```
String nodes = "ip-адрес-Leader:5000,ip-адрес-Leader:5002";
```
меняем на 
```
//String nodes = "ip-адрес-Leader:5000,ip-адрес-Leader:5002";
```

Должно получиться примерно так:
![](https://habrastorage.org/webt/_b/g2/jb/_bg2jbhbz697cry2ctrmwgv9k_m.png)


### Компилируем код и запускаем его
```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSqlRepl.java
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl
```

Время выполнения транзакций и select, если идет обращение только на Leader
```
Master: PostgreSQL 12.3 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 4.8.5 20150623 (Red Hat 4.8.5-39), 64-bit
Slave: PostgreSQL 12.3 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 4.8.5 20150623 (Red Hat 4.8.5-39), 64-bit
transact: 6.00 (2.60) ms   select: 31.07 (10.99) ms
transact: 0.80 (0.11) ms   select: 0.72 (0.13) ms
transact: 0.75 (0.10) ms   select: 0.56 (0.10) ms
transact: 0.75 (0.12) ms   select: 0.60 (0.09) ms
transact: 0.82 (0.13) ms   select: 0.59 (0.09) ms
transact: 1.30 (0.10) ms   select: 1.04 (0.09) ms
transact: 1.74 (0.10) ms   select: 2.90 (0.10) ms
transact: 2.25 (0.11) ms   select: 1.48 (0.10) ms
transact: 1.55 (0.12) ms   select: 1.14 (0.11) ms
transact: 1.11 (0.11) ms   select: 1.31 (0.12) ms
```

## Тестирование. Транзакции идут на Leader. Select идут на Sync Standby

### Правим строку String nodes в файле JavaPostgreSqlRepl.java
```
String nodes = "ip-адрес-Leader:5000";
```
Поменять на 
```
//String nodes = "ip-адрес-Leader:5000";
```

А строку содержащую несколько нод раскоментировать
```
//String nodes = "ip-адрес-Leader:5000,ip-адрес-Leader:5002";
```
меняем на 
```
String nodes = "ip-адрес-Leader:5000,ip-адрес-Leader:5002";
```

Должно получиться примерно так:
![](https://habrastorage.org/webt/0i/hk/ft/0ihkft7bcclqzt8cwexsler0zf4.png)

### Компилируем код и запускаем его
```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSqlRepl.java
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl
```

Время выполнения транзакций и select, если идет обращение только на Leader
```
PostgreSQL 12.3 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 4.8.5 20150623 (Red Hat 4.8.5-39), 64-bit
transact: 34454.46 (0.00) ms   select: 33060.40 (0.00) ms
transact: 33405.12 (0.00) ms   select: 33026.28 (0.00) ms
transact: 33400.46 (0.00) ms   select: 32965.08 (0.00) ms
transact: 33394.38 (0.00) ms   select: 32948.28 (0.00) ms
transact: 33332.10 (0.00) ms   select: 32897.06 (0.00) ms
```

## Уменьшаем размер бд
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
       GENERATE_SERIES(1, 90000) gen
 WHERE gen <= sections * 3000;
```

Проверяем размер БД после генерации данных:

![](https://habrastorage.org/webt/vc/4x/vo/vc4xvomyljgyb3paoo4lk-o_ifc.png)
