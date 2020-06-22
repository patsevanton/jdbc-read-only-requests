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
   - {name: "pgbenchwrite", password: "password"}
   - {name: "pgbenchread", password: "password"}
```

Добавляем создание бд test с owner test

```
postgresql_databases:
   - {db: "test", encoding: "UTF8", lc_collate: "ru_RU.UTF-8", lc_ctype: "ru_RU.UTF-8", owner: "test"}
   - {db: "pgbenchread", encoding: "UTF8", lc_collate: "ru_RU.UTF-8", lc_ctype: "ru_RU.UTF-8", owner: "pgbenchread"}
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
  - {type: "host", database: "pgbenchwrite", user: "pgbenchwrite", address: "0.0.0.0/0", method: "md5"}
  - {type: "host", database: "pgbenchread", user: "pgbenchread", address: "0.0.0.0/0", method: "md5"}
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
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U pgbenchwrite -i -s 150 pgbenchwrite
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U pgbenchread -i -s 150 pgbenchread
```

Запускаем 2 консолях одновременно pgbench write-only и select-only, где все коннекты идут к master

```
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U pgbenchwrite -c 50 -j 2 -P 60 -T 600 -N pgbenchwrite
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U pgbenchread -c 50 -j 2 -P 60 -T 600 -S pgbenchread
```

Вывод pgbench write-only:

```
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U pgbenchwrite -c 50 -j 2 -P 60 -T 600 -N pgbenchwrite
Password: 
starting vacuum...end.
progress: 60.0 s, 113.8 tps, lat 436.492 ms stddev 228.613
progress: 120.0 s, 112.1 tps, lat 445.698 ms stddev 181.140
progress: 180.0 s, 119.9 tps, lat 412.778 ms stddev 400.669
progress: 240.0 s, 110.7 tps, lat 452.843 ms stddev 364.284
progress: 300.0 s, 38.2 tps, lat 1284.131 ms stddev 868.801
progress: 360.0 s, 52.2 tps, lat 983.476 ms stddev 859.265
progress: 420.0 s, 62.9 tps, lat 791.075 ms stddev 704.830
progress: 480.0 s, 70.6 tps, lat 698.554 ms stddev 725.389
progress: 540.0 s, 68.9 tps, lat 739.978 ms stddev 787.998
progress: 600.0 s, 75.3 tps, lat 662.032 ms stddev 721.487
transaction type: <builtin: simple update>
scaling factor: 150
query mode: simple
number of clients: 50
number of threads: 2
duration: 600 s
number of transactions actually processed: 49527
latency average = 606.825 ms
latency stddev = 608.772 ms
tps = 82.005351 (including connections establishing)
tps = 82.006115 (excluding connections establishing)
```

Вывод pgbench select-only:

```
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U pgbenchread -c 50 -j 2 -P 60 -T 600 -S pgbenchread
Password: 
starting vacuum...end.
progress: 60.0 s, 88.6 tps, lat 559.665 ms stddev 169.444
progress: 120.0 s, 99.5 tps, lat 503.239 ms stddev 191.487
progress: 180.0 s, 111.4 tps, lat 448.638 ms stddev 823.392
progress: 240.0 s, 115.4 tps, lat 433.728 ms stddev 232.107
progress: 300.0 s, 75.2 tps, lat 664.727 ms stddev 442.582
progress: 360.0 s, 115.1 tps, lat 433.675 ms stddev 392.391
progress: 420.0 s, 123.1 tps, lat 407.399 ms stddev 461.501
progress: 480.0 s, 135.7 tps, lat 366.747 ms stddev 514.208
progress: 540.0 s, 119.9 tps, lat 416.024 ms stddev 529.415
progress: 600.0 s, 112.5 tps, lat 446.807 ms stddev 607.408
transaction type: <builtin: select only>
scaling factor: 150
query mode: simple
number of clients: 50
number of threads: 2
duration: 600 s
number of transactions actually processed: 65823
latency average = 455.912 ms
latency stddev = 490.338 ms
tps = 109.546152 (including connections establishing)
tps = 109.547312 (excluding connections establishing)
```

Запускаем 2 консолях одновременно pgbench write-only и select-only, где коннект write-only идет к master, а коннект select-only идет на реплику.

```
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U pgbenchwrite -c 50 -j 2 -P 60 -T 600 -N pgbenchwrite
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5002 -U pgbenchread -c 50 -j 2 -P 60 -T 600 -S pgbenchread
```

Вывод pgbench write-only:

```
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5000 -U pgbenchwrite -c 50 -j 2 -P 60 -T 600 -N pgbenchwrite
Password: 
starting vacuum...end.
progress: 60.0 s, 171.5 tps, lat 290.534 ms stddev 198.945
progress: 120.0 s, 95.2 tps, lat 524.225 ms stddev 836.995
progress: 180.0 s, 41.6 tps, lat 1172.546 ms stddev 1184.899
progress: 240.0 s, 106.4 tps, lat 479.830 ms stddev 613.741
progress: 300.0 s, 107.4 tps, lat 456.684 ms stddev 554.722
progress: 360.0 s, 126.7 tps, lat 403.261 ms stddev 425.490
progress: 420.0 s, 171.8 tps, lat 290.589 ms stddev 306.722
progress: 480.0 s, 119.9 tps, lat 413.012 ms stddev 433.962
progress: 540.0 s, 165.5 tps, lat 305.434 ms stddev 309.429
progress: 600.0 s, 134.4 tps, lat 363.495 ms stddev 312.672
transaction type: <builtin: simple update>
scaling factor: 150
query mode: simple
number of clients: 50
number of threads: 2
duration: 600 s
number of transactions actually processed: 74483
latency average = 402.763 ms
latency stddev = 515.695 ms
tps = 124.006808 (including connections establishing)
tps = 124.008050 (excluding connections establishing)
```

Вывод pgbench select-only:

```
/usr/pgsql-12/bin/pgbench -h 172.26.10.73 -p 5002 -U pgbenchread -c 50 -j 2 -P 60 -T 600 -S pgbenchread
Password: 
starting vacuum...ERROR:  cannot execute VACUUM during recovery
(ignoring this error and continuing anyway)
ERROR:  cannot execute VACUUM during recovery
(ignoring this error and continuing anyway)
ERROR:  cannot execute TRUNCATE TABLE in a read-only transaction
(ignoring this error and continuing anyway)
end.
progress: 60.0 s, 170.8 tps, lat 291.310 ms stddev 115.079
progress: 120.0 s, 155.4 tps, lat 320.284 ms stddev 232.217
progress: 180.0 s, 153.7 tps, lat 326.370 ms stddev 284.169
progress: 240.0 s, 211.0 tps, lat 237.428 ms stddev 210.316
progress: 300.0 s, 269.6 tps, lat 185.419 ms stddev 169.064
progress: 360.0 s, 273.1 tps, lat 183.099 ms stddev 144.569
progress: 420.0 s, 294.4 tps, lat 169.912 ms stddev 128.209
progress: 480.0 s, 311.2 tps, lat 160.646 ms stddev 115.194
progress: 540.0 s, 317.8 tps, lat 157.084 ms stddev 113.825
progress: 600.0 s, 319.5 tps, lat 156.751 ms stddev 112.012
transaction type: <builtin: select only>
scaling factor: 150
query mode: simple
number of clients: 50
number of threads: 2
duration: 600 s
number of transactions actually processed: 148638
latency average = 201.815 ms
latency stddev = 169.752 ms
tps = 247.553623 (including connections establishing)
tps = 247.556199 (excluding connections establishing)
```

**Улучшение для write запросов в тесте pgbench при переводе SELECT запросов на реплику:**

**(124-82)/82=0.51 или 51%**

**Улучшение для select запросов в тесте pgbench при переводе SELECT запросов на реплику:**

**(247-109)/109=1.26 или 126%**

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

Запускаем 49 раз Java приложение в фоне в бесконечном цикле.

```
java -classpath .:./postgresql-42.2.14.jar JavaPostgreSqlRepl > /dev/null 2>&1 &
```

Проверяем что у нас запущено 49 приложений java

```
ps aux | grep java | grep -v grep | wc -l
```

Смотрим какие процессы postgres запущены на реплике

![](https://habrastorage.org/webt/an/aq/dg/anaqdg4k0zeq5rintiwixch03lm.png)

Запускаем Java приложение чтобы увидеть среднее время SQL запросов.

Время выполнения транзакций и select, если идет обращение только на Leader

| Время SQL update для Java приложения на Leader | Время SQL update на самом PostgreSQL сервере Leader |
| ---------------------------------------------- | --------------------------------------------------- |
| 17,56                                          | 0,13                                                |
| Время SQL select для Java приложения на Leader | Время SQL select на самом PostgreSQL сервере Leader |
| 8,51                                           | 0,12                                                |

Время выполнения транзакций, которые идут на Leader и select, которые идут на Replica

| Время SQL update для Java приложения на Leader  | Время SQL update на самом PostgreSQL сервере Leader  |
| ----------------------------------------------- | ---------------------------------------------------- |
| 9,07                                            | 0,12                                                 |
| Время SQL select для Java приложения на Replica | Время SQL select на самом PostgreSQL сервере Replica |
| 3,49                                            | 0,10                                                 |

Исходные данные можно посмотреть по ссылке

https://docs.google.com/spreadsheets/d/1jw5DAsHFNsO4wmYUxR2TmbGc1CS9J0w2beNhfI0NLhQ/edit?usp=sharing

Чтобы подтвердить тесты pgbench из java приложений и потдвердить улучшение времени SQL запросов  (TPS) на самом сервере приложений, нужно тестировать многопоточное Java приложение с Connection Pool (Например, HikariCP, C3PO) , которое будет одновременно отправлять несколько десятков SQL запросов так как это делает pgbench.
