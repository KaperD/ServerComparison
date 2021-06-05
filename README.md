## Сборка и запуск
```bash
$ ./gradlew fatJar
$ java -jar build/libs/ServerComparison-fat-1.0-SNAPSHOT.jar 
```

## Формат вывода
```bash
NumberOfMeasures 974 # число измерений, которые произошли до того, как какой-то клиент закончил работу
10 1 # значение переменного параметра и среднее время при этом параметре
NumberOfMeasures 1909
20 1
NumberOfMeasures 2858
30 1
NonBlocking # тип архитектуры
NumberOfRequestsPerClient 100 # значение постоянного параметра
ArraySize 100 # значение постоянного параметра
TimeBetweenRequests 10 # значение постоянного параметра
NumberOfClients # переменный параметр
10 1 # значение переменного параметра и среднее время при этом параметре
20 1
30 1
TotalTime 3261 # общее время тестирования
```