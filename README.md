# Тестирование горизонтального масштабирования SELECT запросов на реплику

Цель данного поста протестировать горизонтальное масштабирование SELECT запросов на реплику.

Схема горизонтального масштабирования примерно такая.

![](https://habrastorage.org/webt/op/zi/t0/opzit0s2opxwhigvzdmpubiypry.png)

## PostgreSQL кластер

## Характеристики PostgreSQL кластера

Возьмем виртуальные машины для PostgreSQL по 2 ГБ ОЗУ, чтобы бд не ввлезала в память и 3 ноды Etcd по 1ГБ.

## Установка

Устанавливаем PostgreSQL кластер из репозитория https://github.com/vitabaks/postgresql_cluster
```
git clone https://github.com/vitabaks/postgresql_cluster
```
Изменяем адреса серверов в inventory на свои. 

Правим параметры в var/main.yaml

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

Добавляем создание пользователя test с паролем password

```
postgresql_users:
   - {name: "test", password: "password"}
   - {name: "benchmark", password: "password"}
```

Добавляем создание бд test с owner test

```
postgresql_databases:
   - {db: "test", encoding: "UTF8", lc_collate: "ru_RU.UTF-8", lc_ctype: "ru_RU.UTF-8", owner: "test"}
   - {db: "benchmark", encoding: "UTF8", lc_collate: "ru_RU.UTF-8", lc_ctype: "ru_RU.UTF-8", owner: "benchmark"}
```

Увеличиваем max_connections

```
 postgresql_parameters:
  - {option: "max_connections", value: "150"}
```

Добавляем бд и юзера test в pg_hba

```
postgresql_pg_hba:
...
  - {type: "host", database: "test", user: "test", address: "0.0.0.0/0", method: "md5"}
  - {type: "host", database: "benchmark", user: "benchmark", address: "0.0.0.0/0", method: "md5"}
```

Тюнинг параметров можно выполнить здесь: http://pgconfigurator.cybertec.at/

## Проверка кластера

После установки у вас должно быть примерно такая картина
```
patronictl -c /etc/patroni/patroni.yml list
```
![](https://habrastorage.org/webt/14/g9/kl/14g9kluwlob-bcp3nkci4huoyoe.png)



## Тестирование с использованием pgbench

Так как pgbench-у нельзя указать ip для реплики, то запустим 2 экземпляра pgbench: первый будет создавать update, второй будетсоздавать только select-only нагрузку.

Заполняем тестовую базу

```
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U benchmark -i -s 150 benchmark
```

Запускаем 2 консолях одновременно pgbench write-only и select-only, где все коннекты идут к master

```
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U benchmark -c 50 -j 2 -P 60 -T 600 -N benchmark
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U benchmark -c 50 -j 2 -P 60 -T 600 -S benchmark
```

Запускаем 2 консолях одновременно pgbench write-only и select-only, где коннект write-only идет к master, а коннект select-only идет на реплику.

```
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U benchmark -c 50 -j 2 -P 60 -T 600 -N benchmark
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5002 -U benchmark -c 50 -j 2 -P 60 -T 600 -S benchmark
```

### Устанавливаем зависимости на Leader, так как на нем будем запускать Java приложение

```
yum install -y java-1.8.0-openjdk-devel git mc
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
/usr/pgsql-12/bin/psql --host=172.26.10.73 -U test test
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
       GENERATE_SERIES(1, 900000) gen
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

## Тестирование. Все запросы идут на Leader. Запуск 1 экземпляра приложения

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

Компилируем код

```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSqlRepl.java
```

Запускаем Java приложение

```
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl
```

Время выполнения транзакций, которые идут на Leader, и select, которые идут на Replica

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

## Тестирование. Транзакции идут на Leader. Select идут на Sync Standby. Запуск 1 экземпляра приложения

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
Компилируем код

```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSqlRepl.java
```

Запускаем Java приложение

```
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl
```

Время выполнения транзакций и select, если идет обращение только на Leader
```
Master: PostgreSQL 12.3 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 4.8.5 20150623 (Red Hat 4.8.5-39), 64-bit
Slave: PostgreSQL 12.3 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 4.8.5 20150623 (Red Hat 4.8.5-39), 64-bit
transact: 4.14 (0.89) ms   select: 63.41 (37.40) ms
transact: 0.80 (0.10) ms   select: 0.96 (0.12) ms
transact: 0.74 (0.10) ms   select: 0.76 (0.10) ms
transact: 0.86 (0.14) ms   select: 0.72 (0.09) ms
transact: 0.82 (0.10) ms   select: 4.94 (0.11) ms
transact: 1.44 (0.12) ms   select: 0.84 (0.10) ms
transact: 0.78 (0.10) ms   select: 1.64 (0.10) ms
transact: 1.56 (0.10) ms   select: 0.79 (0.09) ms
transact: 0.80 (0.10) ms   select: 0.94 (0.09) ms
transact: 0.86 (0.12) ms   select: 0.79 (0.09) ms
```

**Как видим время запросов поменялось не сильно.**

### Запуск нескольких экземпляров Java приложения

Активируем бесконечный цикл SQL запросов в Java приложении. Переходим на 108 строку и расскоментируем `while(true) {`, комментируем `for(int i=0; i < 100; i++ ) {`

```
while(true) {
//for(int i=0; i < 100; i++ ) {
```

Должно получиться примерно так:

![](https://habrastorage.org/webt/bb/wi/jd/bbwijd1huch6osw9matusiqlmna.png)

### Тестирование. Все запросы идут на Leader. Запуск 50 экземпляров приложения

Правим строку String nodes в файле JavaPostgreSqlRepl.java

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

Компилируем код

```
javac -cp "./postgresql-42.2.14.jar" JavaPostgreSqlRepl.java
```

Запускаем Java приложение

```
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl
```

Запустим 50 экземпляров Java приложения в бесконечном цикле.

Проверяем что у нас запущено 50 приложений java

```
ps aux | grep java | wc -l
```

Время выполнения транзакций и select, если идет обращение только на Leader

